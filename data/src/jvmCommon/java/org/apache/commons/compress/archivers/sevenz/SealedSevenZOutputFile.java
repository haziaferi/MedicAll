/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.archivers.sevenz;

import static java.nio.charset.StandardCharsets.UTF_16LE;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.CRC32;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.io.file.attribute.FileTimes;
import org.apache.commons.io.output.CountingOutputStream;

/**
 * Writes a 7z file.
 *
 * <p>
 * MedicAll patch, 2026-11-22 and 2026-11-23 (SPEC:R112): a copy of Commons Compress 1.28.0's
 * {@code SevenZOutputFile}, renamed so it does not clash with the library's own class on Android, and kept in
 * this package for the package-private {@code Coders}, {@code NID}, {@code AES256SHA256Decoder} and
 * {@code SevenZFile}. With a password it (1) seals the header, which holds the file names, as 7-Zip's
 * "encrypt file names" ({@code -mhe=on}) does ({@link #writeEncryptedHeader}); (2) gives the header and every
 * entry its own random IV, where upstream uses one {@code AES256Options}, so one IV, for all the entries; and
 * (3) derives the key once per archive ({@link SealedAes256}), where upstream's options derive it once each.
 * There is no salt: as in upstream's {@code AES256Options(char[])} it is empty, so the header and every entry
 * share one key, from 2^19 SHA-256 rounds, and only their IVs differ. The key is cleared when the archive is
 * finished or closed, also when finishing fails. Everything else is upstream's code.
 * See docs/decisions/2026-11-22-7zip-library.md.
 * </p>
 *
 * @since 1.6
 */
public class SealedSevenZOutputFile implements Closeable {

    private final class OutputStreamWrapper extends OutputStream {

        private static final int BUF_SIZE = 8192;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);

        @Override
        public void close() throws IOException {
            // the file will be closed by the containing class's close method
        }

        @Override
        public void flush() throws IOException {
            // no reason to flush the channel
        }

        @Override
        public void write(final byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (len > BUF_SIZE) {
                channel.write(ByteBuffer.wrap(b, off, len));
            } else {
                buffer.clear();
                buffer.put(b, off, len).flip();
                channel.write(buffer);
            }
            compressedCrc32.update(b, off, len);
            fileBytesWritten += len;
        }

        @Override
        public void write(final int b) throws IOException {
            buffer.clear();
            buffer.put((byte) b).flip();
            channel.write(buffer);
            compressedCrc32.update(b);
            fileBytesWritten++;
        }
    }

    private static <T> Iterable<T> reverse(final Iterable<T> i) {
        final LinkedList<T> l = new LinkedList<>();
        for (final T t : i) {
            l.addFirst(t);
        }
        return l;
    }

    private final SeekableByteChannel channel;
    private final List<SevenZArchiveEntry> files = new ArrayList<>();
    private int numNonEmptyStreams;
    private final CRC32 crc32 = new CRC32();
    private final CRC32 compressedCrc32 = new CRC32();
    private long fileBytesWritten;
    private boolean finished;
    private CountingOutputStream currentOutputStream;
    private CountingOutputStream[] additionalCountingStreams;
    private Iterable<? extends SevenZMethodConfiguration> contentMethods = Collections.singletonList(new SevenZMethodConfiguration(SevenZMethod.LZMA2));
    private final Map<SevenZArchiveEntry, long[]> additionalSizes = new HashMap<>();
    /** MedicAll patch: the archive's one AES key, derived from the password once; cleared once the archive is finished. */
    private byte[] key;
    /** MedicAll patch: each entry's own AES options, the archive's key with a fresh IV. */
    private final Map<SevenZArchiveEntry, SealedAes256> entryAes256Options = new IdentityHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Opens file to write a 7z archive to.
     *
     * @param fileName the file to write to
     * @throws IOException if opening the file fails
     */
    public SealedSevenZOutputFile(final File fileName) throws IOException {
        this(fileName, null);
    }

    /**
     * Opens file to write a 7z archive to.
     *
     * @param fileName the file to write to
     * @param password optional password if the archive has to be encrypted
     * @throws IOException if opening the file fails
     * @since 1.23
     */
    public SealedSevenZOutputFile(final File fileName, final char[] password) throws IOException {
        this(Files.newByteChannel(fileName.toPath(), EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)),
                password);
    }

    /**
     * Prepares channel to write a 7z archive to.
     *
     * <p>
     * {@link org.apache.commons.compress.utils.SeekableInMemoryByteChannel} allows you to write to an in-memory archive.
     * </p>
     *
     * @param channel the channel to write to
     * @throws IOException if the channel cannot be positioned properly
     * @since 1.13
     */
    public SealedSevenZOutputFile(final SeekableByteChannel channel) throws IOException {
        this(channel, null);
    }

    /**
     * Prepares channel to write a 7z archive to.
     *
     * <p>
     * {@link org.apache.commons.compress.utils.SeekableInMemoryByteChannel} allows you to write to an in-memory archive.
     * </p>
     *
     * @param channel  the channel to write to
     * @param password optional password if the archive has to be encrypted
     * @throws IOException if the channel cannot be positioned properly
     * @since 1.23
     */
    public SealedSevenZOutputFile(final SeekableByteChannel channel, final char[] password) throws IOException {
        this.channel = channel;
        channel.position(SevenZFile.SIGNATURE_HEADER_SIZE);
        if (password != null) {
            this.key = SealedAes256.deriveKey(password);
        }
    }

    /** MedicAll patch: clears the key, which nothing needs once the header is written. */
    private void clearKey() {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Closes the archive, calling {@link #finish} if necessary.
     *
     * @throws IOException on error
     */
    @Override
    public void close() throws IOException {
        try {
            if (!finished) {
                finish();
            }
        } finally {
            clearKey();
            channel.close();
        }
    }

    /**
     * Closes the archive entry.
     *
     * @throws IOException on error
     */
    public void closeArchiveEntry() throws IOException {
        if (currentOutputStream != null) {
            currentOutputStream.flush();
            currentOutputStream.close();
        }

        final SevenZArchiveEntry entry = files.get(files.size() - 1);
        if (fileBytesWritten > 0) { // this implies currentOutputStream != null
            entry.setHasStream(true);
            ++numNonEmptyStreams;
            entry.setSize(currentOutputStream.getByteCount()); // NOSONAR
            entry.setCompressedSize(fileBytesWritten);
            entry.setCrcValue(crc32.getValue());
            entry.setCompressedCrcValue(compressedCrc32.getValue());
            entry.setHasCrc(true);
            if (additionalCountingStreams != null) {
                final long[] sizes = new long[additionalCountingStreams.length];
                Arrays.setAll(sizes, i -> additionalCountingStreams[i].getByteCount());
                additionalSizes.put(entry, sizes);
            }
        } else {
            entry.setHasStream(false);
            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setHasCrc(false);
        }
        currentOutputStream = null;
        additionalCountingStreams = null;
        crc32.reset();
        compressedCrc32.reset();
        fileBytesWritten = 0;
    }

    /**
     * Creates an archive entry using the inputFile and entryName provided.
     *
     * @param inputFile file to create an entry from
     * @param entryName the name to use
     * @return the ArchiveEntry set up with details from the file
     */
    public SevenZArchiveEntry createArchiveEntry(final File inputFile, final String entryName) {
        final SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setDirectory(inputFile.isDirectory());
        entry.setName(entryName);
        try {
            fillDates(inputFile.toPath(), entry);
        } catch (final IOException e) { // NOSONAR
            entry.setLastModifiedDate(new Date(inputFile.lastModified()));
        }
        return entry;
    }

    /**
     * Creates an archive entry using the inputPath and entryName provided.
     *
     * @param inputPath path to create an entry from
     * @param entryName the name to use
     * @param options   options indicating how symbolic links are handled.
     * @return the ArchiveEntry set up with details from the file
     * @throws IOException on error
     * @since 1.21
     */
    public SevenZArchiveEntry createArchiveEntry(final Path inputPath, final String entryName, final LinkOption... options) throws IOException {
        final SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setDirectory(Files.isDirectory(inputPath, options));
        entry.setName(entryName);
        fillDates(inputPath, entry, options);
        return entry;
    }

    private void fillDates(final Path inputPath, final SevenZArchiveEntry entry, final LinkOption... options) throws IOException {
        final BasicFileAttributes attributes = Files.readAttributes(inputPath, BasicFileAttributes.class, options);
        entry.setLastModifiedTime(attributes.lastModifiedTime());
        entry.setCreationTime(attributes.creationTime());
        entry.setAccessTime(attributes.lastAccessTime());
    }

    /**
     * Finishes the addition of entries to this archive, without closing it.
     *
     * @throws IOException if archive is already closed.
     */
    public void finish() throws IOException {
        if (finished) {
            throw new IOException("This archive has already been finished");
        }
        finished = true;
        try {
            writeHeaders();
        } finally {
            clearKey();
        }
    }

    private void writeHeaders() throws IOException {
        long headerPosition = channel.position();

        final ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
        final DataOutputStream header = new DataOutputStream(headerBaos);

        writeHeader(header);
        header.flush();
        byte[] headerBytes = headerBaos.toByteArray();
        if (key != null) {
            // The header holds the file names: seal it too, as 7-Zip's "encrypt file names" does.
            final long packPosition = headerPosition - SevenZFile.SIGNATURE_HEADER_SIZE;
            headerBytes = writeEncryptedHeader(headerBytes, packPosition);
            headerPosition = channel.position();
        }
        channel.write(ByteBuffer.wrap(headerBytes));

        final CRC32 crc32 = new CRC32();
        crc32.update(headerBytes);

        final ByteBuffer bb = ByteBuffer.allocate(SevenZFile.sevenZSignature.length + 2 /* version */
                + 4 /* start header CRC */
                + 8 /* next header position */
                + 8 /* next header length */
                + 4 /* next header CRC */).order(ByteOrder.LITTLE_ENDIAN);
        // signature header
        channel.position(0);
        bb.put(SevenZFile.sevenZSignature);
        // version
        bb.put((byte) 0).put((byte) 2);

        // placeholder for start header CRC
        bb.putInt(0);

        // start header
        bb.putLong(headerPosition - SevenZFile.SIGNATURE_HEADER_SIZE).putLong(0xffffFFFFL & headerBytes.length).putInt((int) crc32.getValue());
        crc32.reset();
        crc32.update(bb.array(), SevenZFile.sevenZSignature.length + 6, 20);
        bb.putInt(SevenZFile.sevenZSignature.length + 2, (int) crc32.getValue());
        bb.flip();
        channel.write(bb);
    }

    private Iterable<? extends SevenZMethodConfiguration> getContentMethods(final SevenZArchiveEntry entry) {
        final Iterable<? extends SevenZMethodConfiguration> ms = entry.getContentMethods();
        Iterable<? extends SevenZMethodConfiguration> iter = ms == null ? contentMethods : ms;

        if (key != null) {
            // prepend encryption; MedicAll patch: its options are the entry's own SealedAes256, not upstream's
            iter = Stream
                    .concat(Stream.of(new SevenZMethodConfiguration(SevenZMethod.AES256SHA256)), StreamSupport.stream(iter.spliterator(), false))
                    .collect(Collectors.toList());
        }
        return iter;
    }

    /**
     * MedicAll patch: [entry]'s own AES options, made once, when its stream is set up, and found again when
     * the header describes it: the archive's key with a fresh random IV, so no two streams are sealed under
     * the same key and IV.
     */
    private SealedAes256 entryAes256Options(final SevenZArchiveEntry entry) {
        return entryAes256Options.computeIfAbsent(entry, e -> newAes256Options());
    }

    private SealedAes256 newAes256Options() {
        final byte[] iv = new byte[SealedAes256.IV_SIZE];
        random.nextBytes(iv);
        return new SealedAes256(key, iv);
    }

    /*
     * Creation of output stream is deferred until data is actually written as some codecs might write header information even for empty streams and directories
     * otherwise.
     */
    private OutputStream getCurrentOutputStream() throws IOException {
        if (currentOutputStream == null) {
            currentOutputStream = setupFileOutputStream();
        }
        return currentOutputStream;
    }

    /**
     * Records an archive entry to add.
     *
     * The caller must then write the content to the archive and call {@link #closeArchiveEntry()} to complete the process.
     *
     * @param archiveEntry describes the entry
     * @deprecated Use {@link #putArchiveEntry(SevenZArchiveEntry)}.
     */
    @Deprecated
    public void putArchiveEntry(final ArchiveEntry archiveEntry) {
        putArchiveEntry((SevenZArchiveEntry) archiveEntry);
    }

    /**
     * Records an archive entry to add.
     *
     * The caller must then write the content to the archive and call {@link #closeArchiveEntry()} to complete the process.
     *
     * @param archiveEntry describes the entry
     * @since 1.25.0
     */
    public void putArchiveEntry(final SevenZArchiveEntry archiveEntry) {
        files.add(archiveEntry);
    }

    /**
     * Sets the default compression method to use for entry contents - the default is LZMA2.
     *
     * <p>
     * Currently only {@link SevenZMethod#COPY}, {@link SevenZMethod#LZMA2}, {@link SevenZMethod#BZIP2} and {@link SevenZMethod#DEFLATE} are supported.
     * </p>
     *
     * <p>
     * This is a short form for passing a single-element iterable to {@link #setContentMethods}.
     * </p>
     *
     * @param method the default compression method
     */
    public void setContentCompression(final SevenZMethod method) {
        setContentMethods(Collections.singletonList(new SevenZMethodConfiguration(method)));
    }

    /**
     * Sets the default (compression) methods to use for entry contents - the default is LZMA2.
     *
     * <p>
     * Currently only {@link SevenZMethod#COPY}, {@link SevenZMethod#LZMA2}, {@link SevenZMethod#BZIP2} and {@link SevenZMethod#DEFLATE} are supported.
     * </p>
     *
     * <p>
     * The methods will be consulted in iteration order to create the final output.
     * </p>
     *
     * @param methods the default (compression) methods
     * @since 1.8
     */
    public void setContentMethods(final Iterable<? extends SevenZMethodConfiguration> methods) {
        this.contentMethods = reverse(methods);
    }

    private CountingOutputStream setupFileOutputStream() throws IOException {
        if (files.isEmpty()) {
            throw new IllegalStateException("No current 7z entry");
        }

        // doesn't need to be closed, just wraps the instance field channel
        OutputStream out = new OutputStreamWrapper(); // NOSONAR
        final ArrayList<CountingOutputStream> moreStreams = new ArrayList<>();
        boolean first = true;
        final SevenZArchiveEntry entry = files.get(files.size() - 1);
        for (final SevenZMethodConfiguration m : getContentMethods(entry)) {
            if (!first) {
                final CountingOutputStream cos = new CountingOutputStream(out);
                moreStreams.add(cos);
                out = cos;
            }
            out = m.getMethod() == SevenZMethod.AES256SHA256 ? entryAes256Options(entry).encoder(out) : Coders.addEncoder(out, m.getMethod(), m.getOptions());
            first = false;
        }
        if (!moreStreams.isEmpty()) {
            additionalCountingStreams = moreStreams.toArray(new CountingOutputStream[0]);
        }
        return new CountingOutputStream(out) {
            @Override
            public void write(final byte[] b) throws IOException {
                super.write(b);
                crc32.update(b);
            }

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {
                super.write(b, off, len);
                crc32.update(b, off, len);
            }

            @Override
            public void write(final int b) throws IOException {
                super.write(b);
                crc32.update(b);
            }
        };
    }

    /**
     * Writes a byte array to the current archive entry.
     *
     * @param b The byte array to be written.
     * @throws IOException on error
     */
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes part of a byte array to the current archive entry.
     *
     * @param b   The byte array to be written.
     * @param off offset into the array to start writing from
     * @param len number of bytes to write
     * @throws IOException on error
     */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (len > 0) {
            getCurrentOutputStream().write(b, off, len);
        }
    }

    /**
     * Writes all of the given input stream to the current archive entry.
     *
     * @param inputStream the data source.
     * @throws IOException if an I/O error occurs.
     * @since 1.21
     */
    public void write(final InputStream inputStream) throws IOException {
        final byte[] buffer = new byte[8024];
        int n = 0;
        while (-1 != (n = inputStream.read(buffer))) {
            write(buffer, 0, n);
        }
    }

    /**
     * Writes a byte to the current archive entry.
     *
     * @param b The byte to be written.
     * @throws IOException on error
     */
    public void write(final int b) throws IOException {
        getCurrentOutputStream().write(b);
    }

    /**
     * Writes all of the given input stream to the current archive entry.
     *
     * @param path    the data source.
     * @param options options specifying how the file is opened.
     * @throws IOException if an I/O error occurs.
     * @since 1.21
     */
    public void write(final Path path, final OpenOption... options) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path, options))) {
            write(in);
        }
    }

    private void writeBits(final DataOutput header, final BitSet bits, final int length) throws IOException {
        int cache = 0;
        int shift = 7;
        for (int i = 0; i < length; i++) {
            cache |= (bits.get(i) ? 1 : 0) << shift;
            if (--shift < 0) {
                header.write(cache);
                shift = 7;
                cache = 0;
            }
        }
        if (shift != 7) {
            header.write(cache);
        }
    }

    private void writeFileAntiItems(final DataOutput header) throws IOException {
        boolean hasAntiItems = false;
        final BitSet antiItems = new BitSet(0);
        int antiItemCounter = 0;
        for (final SevenZArchiveEntry file1 : files) {
            if (file1.isEmptyStream()) {
                final boolean isAnti = file1.isAntiItem();
                antiItems.set(antiItemCounter++, isAnti);
                hasAntiItems |= isAnti;
            }
        }
        if (hasAntiItems) {
            header.write(NID.kAnti);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            writeBits(out, antiItems, antiItemCounter);
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileATimes(final DataOutput header) throws IOException {
        int numAccessDates = 0;
        for (final SevenZArchiveEntry entry : files) {
            if (entry.getHasAccessDate()) {
                ++numAccessDates;
            }
        }
        if (numAccessDates > 0) {
            header.write(NID.kATime);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            if (numAccessDates != files.size()) {
                out.write(0);
                final BitSet aTimes = new BitSet(files.size());
                for (int i = 0; i < files.size(); i++) {
                    aTimes.set(i, files.get(i).getHasAccessDate());
                }
                writeBits(out, aTimes, files.size());
            } else {
                out.write(1); // "allAreDefined" == true
            }
            out.write(0);
            for (final SevenZArchiveEntry entry : files) {
                if (entry.getHasAccessDate()) {
                    final long ntfsTime = FileTimes.toNtfsTime(entry.getAccessTime());
                    out.writeLong(Long.reverseBytes(ntfsTime));
                }
            }
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileCTimes(final DataOutput header) throws IOException {
        int numCreationDates = 0;
        for (final SevenZArchiveEntry entry : files) {
            if (entry.getHasCreationDate()) {
                ++numCreationDates;
            }
        }
        if (numCreationDates > 0) {
            header.write(NID.kCTime);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            if (numCreationDates != files.size()) {
                out.write(0);
                final BitSet cTimes = new BitSet(files.size());
                for (int i = 0; i < files.size(); i++) {
                    cTimes.set(i, files.get(i).getHasCreationDate());
                }
                writeBits(out, cTimes, files.size());
            } else {
                out.write(1); // "allAreDefined" == true
            }
            out.write(0);
            for (final SevenZArchiveEntry entry : files) {
                if (entry.getHasCreationDate()) {
                    final long ntfsTime = FileTimes.toNtfsTime(entry.getCreationTime());
                    out.writeLong(Long.reverseBytes(ntfsTime));
                }
            }
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileEmptyFiles(final DataOutput header) throws IOException {
        boolean hasEmptyFiles = false;
        int emptyStreamCounter = 0;
        final BitSet emptyFiles = new BitSet(0);
        for (final SevenZArchiveEntry file1 : files) {
            if (file1.isEmptyStream()) {
                final boolean isDir = file1.isDirectory();
                emptyFiles.set(emptyStreamCounter++, !isDir);
                hasEmptyFiles |= !isDir;
            }
        }
        if (hasEmptyFiles) {
            header.write(NID.kEmptyFile);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            writeBits(out, emptyFiles, emptyStreamCounter);
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileEmptyStreams(final DataOutput header) throws IOException {
        final boolean hasEmptyStreams = files.stream().anyMatch(SevenZArchiveEntry::isEmptyStream);
        if (hasEmptyStreams) {
            header.write(NID.kEmptyStream);
            final BitSet emptyStreams = new BitSet(files.size());
            for (int i = 0; i < files.size(); i++) {
                emptyStreams.set(i, files.get(i).isEmptyStream());
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            writeBits(out, emptyStreams, files.size());
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileMTimes(final DataOutput header) throws IOException {
        int numLastModifiedDates = 0;
        for (final SevenZArchiveEntry entry : files) {
            if (entry.getHasLastModifiedDate()) {
                ++numLastModifiedDates;
            }
        }
        if (numLastModifiedDates > 0) {
            header.write(NID.kMTime);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            if (numLastModifiedDates != files.size()) {
                out.write(0);
                final BitSet mTimes = new BitSet(files.size());
                for (int i = 0; i < files.size(); i++) {
                    mTimes.set(i, files.get(i).getHasLastModifiedDate());
                }
                writeBits(out, mTimes, files.size());
            } else {
                out.write(1); // "allAreDefined" == true
            }
            out.write(0);
            for (final SevenZArchiveEntry entry : files) {
                if (entry.getHasLastModifiedDate()) {
                    final long ntfsTime = FileTimes.toNtfsTime(entry.getLastModifiedTime());
                    out.writeLong(Long.reverseBytes(ntfsTime));
                }
            }
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFileNames(final DataOutput header) throws IOException {
        header.write(NID.kName);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(baos);
        out.write(0);
        for (final SevenZArchiveEntry entry : files) {
            out.write(entry.getName().getBytes(UTF_16LE));
            out.writeShort(0);
        }
        out.flush();
        final byte[] contents = baos.toByteArray();
        writeUint64(header, contents.length);
        header.write(contents);
    }

    private void writeFilesInfo(final DataOutput header) throws IOException {
        header.write(NID.kFilesInfo);

        writeUint64(header, files.size());

        writeFileEmptyStreams(header);
        writeFileEmptyFiles(header);
        writeFileAntiItems(header);
        writeFileNames(header);
        writeFileCTimes(header);
        writeFileATimes(header);
        writeFileMTimes(header);
        writeFileWindowsAttributes(header);
        header.write(NID.kEnd);
    }

    private void writeFileWindowsAttributes(final DataOutput header) throws IOException {
        int numWindowsAttributes = 0;
        for (final SevenZArchiveEntry entry : files) {
            if (entry.getHasWindowsAttributes()) {
                ++numWindowsAttributes;
            }
        }
        if (numWindowsAttributes > 0) {
            header.write(NID.kWinAttributes);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(baos);
            if (numWindowsAttributes != files.size()) {
                out.write(0);
                final BitSet attributes = new BitSet(files.size());
                for (int i = 0; i < files.size(); i++) {
                    attributes.set(i, files.get(i).getHasWindowsAttributes());
                }
                writeBits(out, attributes, files.size());
            } else {
                out.write(1); // "allAreDefined" == true
            }
            out.write(0);
            for (final SevenZArchiveEntry entry : files) {
                if (entry.getHasWindowsAttributes()) {
                    out.writeInt(Integer.reverseBytes(entry.getWindowsAttributes()));
                }
            }
            out.flush();
            final byte[] contents = baos.toByteArray();
            writeUint64(header, contents.length);
            header.write(contents);
        }
    }

    private void writeFolder(final DataOutput header, final SevenZArchiveEntry entry) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int numCoders = 0;
        for (final SevenZMethodConfiguration m : getContentMethods(entry)) {
            numCoders++;
            if (m.getMethod() == SevenZMethod.AES256SHA256) {
                writeSingleCodec(m.getMethod(), entryAes256Options(entry).properties(), bos);
            } else {
                writeSingleCodec(m, bos);
            }
        }

        writeUint64(header, numCoders);
        header.write(bos.toByteArray());
        for (long i = 0; i < numCoders - 1; i++) {
            writeUint64(header, i + 1);
            writeUint64(header, i);
        }
    }

    /**
     * Writes [plain] (the header) AES-sealed under the archive's key and its own IV at the channel's
     * position, and returns the small "encoded header" that says where it is and how to open it.
     */
    private byte[] writeEncryptedHeader(final byte[] plain, final long packPosition) throws IOException {
        final SealedAes256 options = newAes256Options();

        final ByteArrayOutputStream sealed = new ByteArrayOutputStream();
        try (OutputStream out = options.encoder(sealed)) {
            out.write(plain);
        }
        final byte[] packed = sealed.toByteArray();
        channel.write(ByteBuffer.wrap(packed));

        final CRC32 crc = new CRC32();
        crc.update(plain);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream h = new DataOutputStream(baos);
        h.write(NID.kEncodedHeader);
        h.write(NID.kPackInfo);
        writeUint64(h, packPosition);
        writeUint64(h, 1);
        h.write(NID.kSize);
        writeUint64(h, packed.length);
        h.write(NID.kEnd);
        h.write(NID.kUnpackInfo);
        h.write(NID.kFolder);
        writeUint64(h, 1);
        h.write(0);
        writeUint64(h, 1);
        writeSingleCodec(SevenZMethod.AES256SHA256, options.properties(), h);
        h.write(NID.kCodersUnpackSize);
        writeUint64(h, plain.length);
        h.write(NID.kCRC);
        h.write(1);
        h.writeInt(Integer.reverseBytes((int) crc.getValue()));
        h.write(NID.kEnd);
        h.write(NID.kEnd);
        h.flush();
        return baos.toByteArray();
    }

    private void writeHeader(final DataOutput header) throws IOException {
        header.write(NID.kHeader);

        header.write(NID.kMainStreamsInfo);
        writeStreamsInfo(header);
        writeFilesInfo(header);
        header.write(NID.kEnd);
    }

    private void writePackInfo(final DataOutput header) throws IOException {
        header.write(NID.kPackInfo);

        writeUint64(header, 0);
        writeUint64(header, 0xffffFFFFL & numNonEmptyStreams);

        header.write(NID.kSize);
        for (final SevenZArchiveEntry entry : files) {
            if (entry.hasStream()) {
                writeUint64(header, entry.getCompressedSize());
            }
        }

        header.write(NID.kCRC);
        header.write(1); // "allAreDefined" == true
        for (final SevenZArchiveEntry entry : files) {
            if (entry.hasStream()) {
                header.writeInt(Integer.reverseBytes((int) entry.getCompressedCrcValue()));
            }
        }

        header.write(NID.kEnd);
    }

    private void writeSingleCodec(final SevenZMethodConfiguration m, final OutputStream bos) throws IOException {
        writeSingleCodec(m.getMethod(), Coders.findByMethod(m.getMethod()).getOptionsAsProperties(m.getOptions()), bos);
    }

    private void writeSingleCodec(final SevenZMethod method, final byte[] properties, final OutputStream bos) throws IOException {
        final byte[] id = method.getId();

        int codecFlags = id.length;
        if (properties.length > 0) {
            codecFlags |= 0x20;
        }
        bos.write(codecFlags);
        bos.write(id);

        if (properties.length > 0) {
            bos.write(properties.length);
            bos.write(properties);
        }
    }

    private void writeStreamsInfo(final DataOutput header) throws IOException {
        if (numNonEmptyStreams > 0) {
            writePackInfo(header);
            writeUnpackInfo(header);
        }

        writeSubStreamsInfo(header);

        header.write(NID.kEnd);
    }

    private void writeSubStreamsInfo(final DataOutput header) throws IOException {
        header.write(NID.kSubStreamsInfo);
        //
        // header.write(NID.kCRC);
        // header.write(1);
        // for (final SevenZArchiveEntry entry : files) {
        // if (entry.getHasCrc()) {
        // header.writeInt(Integer.reverseBytes(entry.getCrc()));
        // }
        // }
        //
        header.write(NID.kEnd);
    }

    private void writeUint64(final DataOutput header, long value) throws IOException {
        int firstByte = 0;
        int mask = 0x80;
        int i;
        for (i = 0; i < 8; i++) {
            if (value < 1L << 7 * (i + 1)) {
                firstByte |= value >>> 8 * i;
                break;
            }
            firstByte |= mask;
            mask >>>= 1;
        }
        header.write(firstByte);
        for (; i > 0; i--) {
            header.write((int) (0xff & value));
            value >>>= 8;
        }
    }

    private void writeUnpackInfo(final DataOutput header) throws IOException {
        header.write(NID.kUnpackInfo);

        header.write(NID.kFolder);
        writeUint64(header, numNonEmptyStreams);
        header.write(0);
        for (final SevenZArchiveEntry entry : files) {
            if (entry.hasStream()) {
                writeFolder(header, entry);
            }
        }

        header.write(NID.kCodersUnpackSize);
        for (final SevenZArchiveEntry entry : files) {
            if (entry.hasStream()) {
                final long[] moreSizes = additionalSizes.get(entry);
                if (moreSizes != null) {
                    for (final long s : moreSizes) {
                        writeUint64(header, s);
                    }
                }
                writeUint64(header, entry.getSize());
            }
        }

        header.write(NID.kCRC);
        header.write(1); // "allAreDefined" == true
        for (final SevenZArchiveEntry entry : files) {
            if (entry.hasStream()) {
                header.writeInt(Integer.reverseBytes((int) entry.getCrcValue()));
            }
        }

        header.write(NID.kEnd);
    }

}
