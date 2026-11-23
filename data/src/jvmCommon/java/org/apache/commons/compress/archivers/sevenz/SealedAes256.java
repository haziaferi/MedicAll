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

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MedicAll patch, 2026-11-23 (SPEC:R112): the 7zAES options of one stream that {@link SealedSevenZOutputFile}
 * seals, made from a key already derived, so an archive derives its key once and not once per entry.
 *
 * <p>
 * A small copy of Commons Compress 1.28.0's {@code AES256Options}, which is final and package-private and
 * derives the key in its constructor, with its {@code getOptionsAsProperties} and its encoding stream
 * (from {@code AES256SHA256Decoder}). Like upstream's {@code AES256Options(char[])}, the salt is empty and the
 * derivation takes 2^19 SHA-256 rounds; so the header and every entry share one key, and only their IVs
 * differ. See docs/decisions/2026-11-22-7zip-library.md.
 * </p>
 */
final class SealedAes256 {

    /** 2^19 rounds, as upstream and 7-Zip write. */
    static final int NUM_CYCLES_POWER = 19;

    static final int IV_SIZE = 16;

    private static final byte[] NO_SALT = {};

    /**
     * The AES-256 key for [password], as 7-Zip derives it: SHA-256 over 2^19 rounds of the salt (empty), the
     * password in UTF-16LE and a counter. The UTF-16LE copy of the password made here is cleared.
     */
    static byte[] deriveKey(final char[] password) {
        final byte[] utf16 = AES256SHA256Decoder.utf16Decode(password);
        try {
            return AES256SHA256Decoder.sha256Password(utf16, NUM_CYCLES_POWER, NO_SALT);
        } finally {
            Arrays.fill(utf16, (byte) 0);
        }
    }

    /** Shared with the writer, which clears it once the archive is finished. */
    private final byte[] key;
    private final byte[] iv;

    SealedAes256(final byte[] key, final byte[] iv) {
        this.key = key;
        this.iv = iv;
    }

    /** The coder's properties as the header carries them: upstream's {@code getOptionsAsProperties}, with no salt. */
    byte[] properties() {
        final byte[] props = new byte[2 + iv.length];
        props[0] = (byte) (NUM_CYCLES_POWER | 1 << 6);
        props[1] = (byte) (iv.length - 1);
        System.arraycopy(iv, 0, props, 2, iv.length);
        return props;
    }

    /** [out] wrapped in AES-256-CBC under this stream's key and IV, the last block padded with zeros. */
    OutputStream encoder(final OutputStream out) throws IOException {
        final Cipher cipher;
        try {
            cipher = Cipher.getInstance(AES256Options.TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, AES256Options.ALGORITHM), new IvParameterSpec(iv));
        } catch (final GeneralSecurityException e) {
            throw new IOException("AES-256 is not available", e);
        }
        return new EncoderStream(cipher, out);
    }

    /** Upstream's {@code AES256SHA256DecoderOutputStream}, unchanged but for taking its cipher ready. */
    private static final class EncoderStream extends OutputStream {
        private final CipherOutputStream cipherOutputStream;
        private final int cipherBlockSize;
        private final byte[] cipherBlockBuffer;
        private int count;

        private EncoderStream(final Cipher cipher, final OutputStream out) {
            cipherOutputStream = new CipherOutputStream(out, cipher);
            cipherBlockSize = cipher.getBlockSize();
            cipherBlockBuffer = new byte[cipherBlockSize];
        }

        @Override
        public void close() throws IOException {
            if (count > 0) {
                cipherOutputStream.write(cipherBlockBuffer);
            }
            cipherOutputStream.close();
        }

        @Override
        public void flush() throws IOException {
            cipherOutputStream.flush();
        }

        private void flushBuffer() throws IOException {
            cipherOutputStream.write(cipherBlockBuffer);
            count = 0;
            Arrays.fill(cipherBlockBuffer, (byte) 0);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            int gap = len + count > cipherBlockSize ? cipherBlockSize - count : len;
            System.arraycopy(b, off, cipherBlockBuffer, count, gap);
            count += gap;

            if (count == cipherBlockSize) {
                flushBuffer();

                if (len - gap >= cipherBlockSize) {
                    // skip buffer to encrypt data chunks big enough to fit cipher block size
                    final int multipleCipherBlockSizeLen = (len - gap) / cipherBlockSize * cipherBlockSize;
                    cipherOutputStream.write(b, off + gap, multipleCipherBlockSizeLen);
                    gap += multipleCipherBlockSizeLen;
                }
                System.arraycopy(b, off + gap, cipherBlockBuffer, 0, len - gap);
                count = len - gap;
            }
        }

        @Override
        public void write(final int b) throws IOException {
            cipherBlockBuffer[count++] = (byte) b;
            if (count == cipherBlockSize) {
                flushBuffer();
            }
        }
    }
}
