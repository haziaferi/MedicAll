package com.hazia.medicall

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hazia.medicall.core.crypto.MIN_PASSPHRASE
import com.hazia.medicall.core.crypto.Unlock
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.MedicAllDatabase
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.ProfileInfo
import com.hazia.medicall.data.blob.DirectoryBlobStore
import com.hazia.medicall.data.deviceId
import com.hazia.medicall.data.openEncryptedDatabase
import com.hazia.medicall.data.ownLogRead
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

/**
 * The phase 1 debug harness (SPEC:R100, debug builds only): one plain screen whose buttons drive
 * [MedicAllCore] for the two-phone walk. It holds no logic of its own; it is a test tool, not part of
 * any screen design. Each button runs on one background thread, in the order pressed.
 *
 * The folders are picked through the Storage Access Framework with persistable read and write grants,
 * and remembered across starts. The database opens with its own random key ([KeyVault.databaseKey],
 * SPEC:R10); the passphrase unlocks (or makes) the key file in the sync folder and seals the backups, and
 * is kept sealed by [KeyVault] (SPEC:R109), so on the next start the harness opens everything without asking.
 *
 * "Edit name", "Edit kind", "Add test file" and "Remove" act on the first live profile by name;
 * "Restore" on the profile most recently put in the bin. The text field gives "Add test profile" and
 * "Edit name" their name, and "Empty bin" its confirmation.
 */
class HarnessActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("harness", MODE_PRIVATE) }

    private lateinit var passphraseField: EditText
    private lateinit var textField: EditText
    private lateinit var statusView: TextView
    private lateinit var stateView: TextView

    // Touched on the worker thread only.
    private var db: MedicAllDatabase? = null
    private var core: MedicAllCore? = null

    // The status line: the last report of each kind, and the last message. Main thread only.
    private var lastImport = "-"
    private var lastBackup = "-"
    private var lastRestore = "-"
    private var lastIntegrity = "-"
    private var lastMessage = "Pick the sync folder, then type the passphrase and press Set passphrase."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        column.addView(TextView(this).apply { text = "MedicAll phase 1 harness (debug)" })
        statusView = TextView(this).also(column::addView)
        stateView = TextView(this).also(column::addView)
        passphraseField = EditText(this).apply {
            hint = "Passphrase (at least $MIN_PASSPHRASE characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            // The walk's test passphrase is no account: keep the phone's password manager away from it.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }.also(column::addView)
        textField = EditText(this).apply {
            hint = "Name, or DELETE to empty the bin"
            inputType = InputType.TYPE_CLASS_TEXT
        }.also(column::addView)

        button(column, "Set passphrase") { setPassphrase() }
        button(column, "Pick sync folder") { pick(PICK_SYNC) }
        button(column, "Pick backup folder") { pick(PICK_BACKUP) }
        button(column, "Add test profile") { val name = text("Profile A"); run("Add test profile") { it.addProfile(name); "added $name" } }
        button(column, "Edit name") {
            val typed = text("")
            run("Edit name") { c ->
                val p = live(c)
                val name = typed.ifEmpty { "${p.name} renamed" }
                c.rename(p.id, name)
                "renamed to $name"
            }
        }
        button(column, "Edit kind") {
            run("Edit kind") { c ->
                val p = live(c)
                val kind = if (p.kind == MedicAllCore.PET) MedicAllCore.PERSON else MedicAllCore.PET
                c.changeKind(p.id, kind)
                "kind of ${p.name} set to $kind"
            }
        }
        button(column, "Add test file") {
            run("Add test file") { c ->
                val p = live(c)
                val n = c.files().size + 1
                val bytes = "Invented test file $n, made ${Instant.now()}. No real data.".encodeToByteArray()
                c.addFile(p.id, "test-$n.txt", "text/plain", bytes)
                "added test-$n.txt to ${p.name}"
            }
        }
        button(column, "Remove") { run("Remove") { c -> val p = live(c); c.remove(PROFILE, p.id); "${p.name} is in the bin" } }
        button(column, "Restore") {
            run("Restore") { c ->
                val entry = c.binContents().lastOrNull { it.table == PROFILE } ?: error("No profile is in the bin.")
                c.restore(PROFILE, entry.id)
                "restored ${entry.id}"
            }
        }
        button(column, "Empty bin") { val typed = text(""); run("Empty bin") { it.emptyBin(typed); "the bin is empty" } }
        button(column, "Sync now") {
            run("Sync now") { c ->
                val r = c.syncNow()
                ui { lastImport = "$r" }
                // The key folder moved past this cipher (a key-file clash settled, or the passphrase changed on another
                // device): open again with the kept passphrase; if that is refused, the person types the new one.
                if (!r.unlockAgain) "synced" else {
                    val kept = KeyVault(this).loadPassphrase()
                    if (kept == null) "synced; the key folder changed: type the passphrase"
                    else "synced; the key folder changed. " + unlock(kept, typedNow = false)
                }
            }
        }
        button(column, "Back up now") {
            run("Back up now") { c -> c.backUp(backupTarget()).also { r -> ui { lastBackup = "$r" } }; "backup done" }
        }
        button(column, "Restore backup") {
            run("Restore backup") { c ->
                val r = c.restoreBackup(backupTarget())
                ui { lastRestore = r?.toString() ?: "null (wrong passphrase, or not a MedicAll archive)" }
                "restore done"
            }
        }
        button(column, "Check files") {
            run("Check files") { c -> c.checkFiles(backupTarget()).also { r -> ui { lastIntegrity = "$r" } }; "check done" }
        }

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })
        show()
        worker.execute { unlockFromVault(); refresh() }
    }

    override fun onDestroy() {
        worker.execute {
            core?.close()
            db?.close()
        }
        worker.shutdown()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // the platform Activity's own callback; the harness needs nothing newer
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return message("No folder picked.")
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val key = if (requestCode == PICK_SYNC) KEY_SYNC else KEY_BACKUP
        prefs.edit().putString(key, uri.toString()).apply()
        message(if (requestCode == PICK_SYNC) "Sync folder picked." else "Backup folder picked.")
        worker.execute { unlockFromVault(); refresh() }
    }

    private fun pick(code: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        // The walk's folders (Task 16). Opening the picker there leaves only its buttons to press:
        // DocumentsUI ignores adb-injected taps on its folder rows (seen on the 6T, Android 11).
        val folder = if (code == PICK_SYNC) "Documents/MedicAll-test" else "Documents/MedicAll-backup-test"
        intent.putExtra(
            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(folder)),
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, code)
    }

    /** Unlocks with the typed passphrase, which the vault then keeps sealed (SPEC:R109). */
    private fun setPassphrase() {
        val typed = CharArray(passphraseField.length()) { passphraseField.text[it] }
        passphraseField.text.clear()
        if (typed.size < MIN_PASSPHRASE) return message("The passphrase needs at least $MIN_PASSPHRASE characters.")
        worker.execute { val said = unlock(typed, typedNow = true); ui { lastMessage = said }; refresh() }
    }

    /** On start: unlocks with the passphrase the vault keeps, when there is one and the sync folder is picked. Worker thread. */
    private fun unlockFromVault() {
        if (core != null || prefs.getString(KEY_SYNC, null) == null) return
        val kept = runCatching { KeyVault(this).loadPassphrase() }
            .getOrElse { ui { lastMessage = "The key store did not answer: ${it.message ?: it}. Try again." }; return } ?: return
        val said = unlock(kept, typedNow = false)
        ui { lastMessage = said }
    }

    /** Set by the first open of the database: whether it set an old database aside. Worker thread only. */
    private var recovered = false

    /**
     * Opens the database with the vault's key, once (SPEC:R10: it does not depend on the passphrase), then
     * builds the core from [phrase] (SPEC:R109); a [typedNow] passphrase that opened it is sealed in the
     * vault. Clears [phrase]. Returns what to tell the person. Worker thread.
     */
    private fun unlock(phrase: CharArray, typedNow: Boolean): String {
        val outcome = runCatching {
            val device = deviceId(File(filesDir, DEVICE_FILE))
            val folder = syncFolder(device)
            check(folder.reachable()) { "The sync folder cannot be reached." }
            val database = db ?: run {
                val vault = KeyVault(this)
                val key = vault.databaseKey()
                val open = openEncryptedDatabase(this, key.bytes, setAsideExisting = key.isNew)
                // Stored only once the database opens with it: a crash before leaves no key that opens nothing.
                if (key.isNew) vault.store(key.bytes)
                key.bytes.fill(0)
                recovered = open.recovered
                open.database.also { db = it }
            }
            val opened = runBlocking {
                MedicAllCore.open(
                    database, folder, device, phrase, DirectoryBlobStore(File(filesDir, "blobs").toPath()), File(cacheDir, "work"),
                    recovered = recovered,
                    ownLogRead = { ownLogRead(this@HarnessActivity); recovered = false },
                )
            }
            when (opened) {
                is Opened.Ready -> {
                    if (typedNow) KeyVault(this).storePassphrase(phrase)
                    core?.close()
                    core = opened.core
                    (if (typedNow) "Unlocked" else "Unlocked with the kept passphrase") + " as device $device" +
                        if (recovered) " (the database was set aside and is filled again from the sync folder)" else ""
                }
                is Opened.Refused -> when (opened.reason) {
                    Unlock.KeyFileNotArrived -> "Another device's key file has not arrived yet: wait for the sync and try again."
                    Unlock.WrongPassphrase -> "This passphrase does not open the key file (was it changed on another device?): type the current one."
                    else -> "The key file did not unlock: ${opened.reason}"
                }
            }
        }
        phrase.fill(' ')
        return outcome.getOrElse { "Unlock failed: ${it.message ?: it}" }
    }

    /** Runs [action] on the core off the main thread, then shows its message, or why it failed. */
    private fun run(what: String, action: suspend (MedicAllCore) -> String) {
        worker.execute {
            val outcome = runCatching {
                val c = core ?: error("Set the passphrase first.")
                runBlocking { action(c) }
            }
            ui { lastMessage = outcome.getOrElse { "$what failed: ${it.message ?: it}" } }
            refresh()
        }
    }

    /** Redraws what the harness knows: the folders, the profiles, the files and the bin. Worker thread. */
    private fun refresh() {
        val c = core
        val listing = runCatching {
            if (c == null) "Locked." else runBlocking {
                val profiles = c.profiles().joinToString("\n") { p -> "  ${p.name} [${p.kind}]${if (p.removedAt != null) " in bin" else ""} ${p.id.take(8)}" }
                val files = c.files().joinToString("\n") { f -> "  ${f.name} ${f.sha256.take(8)}${if (f.removedAt != null) " in bin" else ""}" }
                val bin = c.binContents().joinToString(", ") { "${if (it.table == BLOB_REF) "file" else "profile"} ${it.id.take(8)}" }
                "Profiles:\n${profiles.ifEmpty { "  none" }}\nFiles:\n${files.ifEmpty { "  none" }}\nBin: ${bin.ifEmpty { "empty" }}"
            }
        }.getOrElse { "Could not list: ${it.message ?: it}" }
        val folders = "Sync folder: ${prefs.getString(KEY_SYNC, null) ?: "not picked"}\nBackup folder: ${prefs.getString(KEY_BACKUP, null) ?: "not picked"}"
        ui { stateView.text = "$folders\n$listing" }
    }

    private fun show() {
        statusView.text = "Last: $lastMessage\nSyncReport: $lastImport\nBackupResult: $lastBackup\nRestoreReport: $lastRestore\nIntegrityReport: $lastIntegrity"
    }

    private fun ui(block: () -> Unit) = runOnUiThread {
        block()
        show()
    }

    private fun message(text: String) {
        lastMessage = text
        show()
    }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(View.OnClickListener { onClick() })
        })
    }

    private fun text(default: String) = textField.text.toString().trim().ifEmpty { default }

    private suspend fun live(c: MedicAllCore): ProfileInfo = c.profiles().firstOrNull { it.removedAt == null } ?: error("No live profile: add one first.")


    private fun syncFolder(device: String) = SafSyncFolder(this, tree(KEY_SYNC) ?: error("Pick the sync folder first."), device)

    private fun backupTarget() = SafBackupTarget(this, tree(KEY_BACKUP) ?: error("Pick the backup folder first."))

    private fun tree(key: String): Uri? = prefs.getString(key, null)?.let(Uri::parse)

    private companion object {
        const val PICK_SYNC = 1
        const val PICK_BACKUP = 2
        const val KEY_SYNC = "sync_tree"
        const val KEY_BACKUP = "backup_tree"
        const val DEVICE_FILE = "device-id"
    }
}
