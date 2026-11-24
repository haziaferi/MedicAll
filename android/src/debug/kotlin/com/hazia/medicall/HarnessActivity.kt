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
import com.hazia.medicall.core.lock.AppLock
import com.hazia.medicall.core.lock.Verdict
import com.hazia.medicall.data.BLOB_REF
import com.hazia.medicall.data.MedicAllCore
import com.hazia.medicall.data.Module
import com.hazia.medicall.data.Opened
import com.hazia.medicall.data.PROFILE
import com.hazia.medicall.data.ProfileInfo
import com.hazia.medicall.data.device.JoinedFolder
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.Executors

/**
 * The debug harness (SPEC:R100, debug builds only) for the phase 1 and phase 2a walks: one plain screen whose
 * buttons drive [AndroidDevice]. It holds no logic of its own and is not part of any screen design. Each button
 * runs on one background thread, in the order pressed.
 *
 * Folders: "Join folder" picks a Syncthing folder (opened at `Documents/MedicAll-2a-<text>`, since DocumentsUI
 * ignores adb taps on its rows) and joins it with the typed passphrase, labelled with the text field. "Next folder"
 * moves the selection; profile, file, bin, sync and backup buttons act on the selected folder, and profile buttons
 * on the selected profile ("Next profile").
 *
 * Lock (SPEC:R11): when it is on, leaving the app and coming back past the timeout replaces the whole screen with
 * the lock screen, so no profile shows until the PIN, the fingerprint or a folder's passphrase opens it.
 */
class HarnessActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var passphraseField: EditText
    private lateinit var textField: EditText
    private lateinit var statusView: TextView
    private lateinit var stateView: TextView
    private lateinit var mainView: View
    private lateinit var lockView: View
    private lateinit var pinField: EditText
    private lateinit var lockPassphraseField: EditText
    private lateinit var lockStatus: TextView

    // Set on the worker thread; the lock is read from the main thread too.
    @Volatile private var phone: AndroidDevice? = null

    // Touched on the worker thread only.
    private var folderId: String? = null
    private var profileId: String? = null

    // Main thread only.
    private var lastMessage = "Type a label (A or B) and the passphrase, then press Join folder."
    private var lastReport = "-"
    private var pendingJoinLabel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainView = buildMain()
        lockView = buildLock()
        // Nothing of a folder shows until the first lock check has run (SPEC:R11).
        setContentView(TextView(this).apply { text = "Starting…"; setPadding(32, 32, 32, 32) })
        show()
        work {
            val p = AndroidDevice(this).also { phone = it }
            val opened = runBlocking { p.openAll() }
            folderId = p.device.folders().firstOrNull { p.device.core(it.id) != null }?.id
            val failed = opened.values.mapNotNull { it.exceptionOrNull()?.message }
            "Opened ${opened.count { it.value.getOrNull() is Opened.Ready }} of ${p.device.folders().size} folders as device ${p.deviceId}" +
                failed.joinToString("") { "\n$it" }
        }
        worker.execute {
            val locked = phone?.device?.lock?.isLocked == true
            runOnUiThread { if (locked) showLock() else setContentView(mainView) }
            if (!locked) refresh()
        }
    }

    // The lock's clock calls run here on the main thread, not behind the worker's queue: a long sync must not
    // delay the moment the app went to the background, nor the lock screen on return (SPEC:R11).
    override fun onStart() {
        super.onStart()
        val lock = phone?.device?.lock ?: return
        lock.onForeground()
        if (lock.isLocked) showLock()
    }

    override fun onStop() {
        phone?.device?.lock?.onBackground()
        super.onStop()
    }

    /** Replaces the whole screen with the lock screen and blanks the listing behind it. Main thread. */
    private fun showLock() {
        stateView.text = ""
        lockStatus.text = ""
        setContentView(lockView)
    }

    override fun onDestroy() {
        worker.execute { phone?.close() }
        worker.shutdown()
        super.onDestroy()
    }

    private fun buildMain(): View {
        val column = column()
        column.addView(TextView(this).apply { text = "MedicAll phase 2a harness (debug)" })
        statusView = TextView(this).also(column::addView)
        stateView = TextView(this).also(column::addView)
        passphraseField = secretField("Passphrase (at least $MIN_PASSPHRASE characters)").also(column::addView)
        textField = EditText(this).apply {
            hint = "Label, name, date, PIN (old new), or DELETE"
            inputType = InputType.TYPE_CLASS_TEXT
        }.also(column::addView)

        heading(column, "Folders")
        button(column, "Join folder") { val label = text(""); if (label.isEmpty()) message("Type a label first.") else { pendingJoinLabel = label; pick(PICK_JOIN, "Documents/MedicAll-2a-$label") } }
        button(column, "Next folder") { work { val p = need(); val open = p.device.folders().filter { p.device.core(it.id) != null }; folderId = next(open.map { it.id }, folderId); profileId = null; "selected ${label(p, folderId)}" } }
        button(column, "Leave folder") { work { val p = need(); val id = folderId ?: error("No folder selected."); runBlocking { p.device.leave(id) }; folderId = null; profileId = null; "left the folder; its database is set aside" } }
        button(column, "Pick backup folder") { val label = text("A"); pick(PICK_BACKUP, "Documents/MedicAll-2a-backup-$label") }

        heading(column, "Profiles of the selected folder")
        button(column, "Add test profile") { val name = text("Profile A"); onCore("Add test profile") { c -> c.addProfile(name).also { profileId = it }; "added $name" } }
        button(column, "Next profile") { onCore("Next profile") { c -> profileId = next(c.profiles().filter { it.removedAt == null }.map { it.id }, profileId); "selected ${selected(c).name}" } }
        button(column, "Edit name") { val typed = text(""); onCore("Edit name") { c -> val p = selected(c); val name = typed.ifEmpty { "${p.name} renamed" }; c.rename(p.id, name); "renamed to $name" } }
        button(column, "Birth date") { val typed = text(""); onCore("Birth date") { c -> val p = selected(c); c.setBirthDate(p.id, typed.ifEmpty { null }); "birth date of ${p.name}: ${typed.ifEmpty { "none" }}" } }
        for (m in Module.entries) {
            button(column, "Module ${m.key} on/off") { onCore("Module") { c -> val p = selected(c); val on = m !in p.modules; c.setModule(p.id, m, on); "${m.key} ${if (on) "on" else "off"} for ${p.name}" } }
        }
        button(column, "Reminders here on/off") { onCore("Reminders") { c -> val p = selected(c); val on = !c.remindersHere(p.id); c.setRemindersHere(p.id, on); "reminders here ${if (on) "on" else "off"} for ${p.name}" } }
        button(column, "Shown here on/off") { onCore("Shown") { c -> val p = selected(c); val on = !c.shownHere(p.id); c.setShownHere(p.id, on); "shown here ${if (on) "on" else "off"} for ${p.name}" } }
        button(column, "Add test file") {
            onCore("Add test file") { c ->
                val p = selected(c)
                val n = c.files().size + 1
                c.addFile(p.id, "test-$n.txt", "text/plain", "Invented test file $n, made ${Instant.now()}. No real data.".encodeToByteArray())
                "added test-$n.txt to ${p.name}"
            }
        }
        button(column, "Remove") { onCore("Remove") { c -> val p = selected(c); c.remove(PROFILE, p.id); profileId = null; "${p.name} is in the bin" } }
        button(column, "Restore") { onCore("Restore") { c -> val e = c.binContents().lastOrNull { it.table == PROFILE } ?: error("No profile is in the bin."); c.restore(PROFILE, e.id); "restored ${e.id.take(8)}" } }
        button(column, "Empty bin") { val typed = text(""); onCore("Empty bin") { it.emptyBin(typed); "the bin is empty" } }

        heading(column, "Sync and backups")
        button(column, "Sync now (every folder)") { work { syncAll(need()) } }
        button(column, "Back up now") { onCore("Back up now") { c -> val r = c.backUp(backup()); ui { lastReport = "$r" }; "backup done" } }
        button(column, "Restore backup") { onCore("Restore backup") { c -> val r = c.restoreBackup(backup()); ui { lastReport = r?.toString() ?: "null (wrong passphrase, or not a MedicAll archive)" }; "restore done" } }
        button(column, "Check files") { onCore("Check files") { c -> val r = c.checkFiles(backup()); ui { lastReport = "$r" }; "check done" } }

        heading(column, "App lock")
        button(column, "Set PIN") { val pin = text("").toCharArray(); work { lock().setPin(pin); pin.fill(' '); "the lock is on" } }
        button(column, "Change PIN (old new)") {
            val parts = text("").split(" ").filter { it.isNotEmpty() }
            work { require(parts.size == 2) { "Type the old PIN, a space, the new PIN." }; "change: ${lock().changePin(parts[0].toCharArray(), parts[1].toCharArray())}" }
        }
        button(column, "Turn lock off (PIN)") { val pin = text("").toCharArray(); work { "turn off: ${lock().turnOff(pin)}".also { pin.fill(' ') } } }
        button(column, "Timeout (next)") { work { val l = lock(); val now = phone!!.lockMinutes(); val m = AppLock.TIMEOUTS[(AppLock.TIMEOUTS.indexOf(now) + 1) % AppLock.TIMEOUTS.size]; l.setTimeout(m); "lock after $m min in the background" } }
        button(column, "Fingerprint on/off") {
            if (!BiometricGate.available(this)) message("No strong biometric is enrolled on this phone.")
            else work { val l = lock(); val on = !phone!!.fingerprintOn(); l.setFingerprint(on); "fingerprint ${if (on) "on" else "off"}" }
        }
        return ScrollView(this).apply { fitsSystemWindows = true; addView(column) }
    }

    private fun buildLock(): View {
        val column = column()
        column.addView(TextView(this).apply { text = "MedicAll is locked" })
        lockStatus = TextView(this).also(column::addView)
        pinField = secretField("PIN").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }.also(column::addView)
        button(column, "Unlock") {
            val pin = CharArray(pinField.length()) { pinField.text[it] }
            pinField.text.clear()
            attempt { try { lock().unlock(pin) } finally { pin.fill(' ') } }
        }
        button(column, "Use fingerprint") {
            BiometricGate.ask(this, "Unlock MedicAll", "Use the PIN",
                passed = { attempt { lock().unlockedByFingerprint(); Verdict.Ok } },
                notPassed = { reason -> lockStatus.text = reason })
        }
        lockPassphraseField = secretField("Forgot the PIN? A folder's passphrase").also(column::addView)
        button(column, "Forgot PIN") {
            val typed = CharArray(lockPassphraseField.length()) { lockPassphraseField.text[it] }
            lockPassphraseField.text.clear()
            attempt("The PIN is removed: set a new one.") { try { need().device.removeForgottenPin(typed) } finally { typed.fill(' ') } }
        }
        return ScrollView(this).apply { fitsSystemWindows = true; addView(column) }
    }

    /** Runs a lock-screen try on the worker; a failure (a revoked folder grant, say) is shown, never thrown. */
    private fun attempt(okText: String = "Unlocked.", tryIt: () -> Verdict) = worker.execute {
        runCatching(tryIt).fold(
            onSuccess = { afterAttempt(it, okText) },
            onFailure = { e -> runOnUiThread { lockStatus.text = "Failed: ${e.message ?: e}" } },
        )
    }

    /** After a PIN, fingerprint or passphrase try: back to the harness on [Verdict.Ok], else why not. Worker thread. */
    private fun afterAttempt(v: Verdict, okText: String = "Unlocked.") {
        if (v == Verdict.Ok && !lock().isLocked) {
            runOnUiThread { setContentView(mainView) }
            ui { lastMessage = okText }
            refresh()
        } else runOnUiThread {
            lockStatus.text = when (v) {
                is Verdict.Wrong -> "Wrong. ${v.triesLeft} tries before a wait."
                is Verdict.Wait -> "Too many wrong tries: wait ${(v.millis + 999) / 1000} s."
                Verdict.Ok -> "Not unlocked."
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // the platform Activity's own callback; the harness needs nothing newer
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return message("No folder picked.")
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        when (requestCode) {
            PICK_JOIN -> {
                val label = pendingJoinLabel ?: return
                val typed = CharArray(passphraseField.length()) { passphraseField.text[it] }
                passphraseField.text.clear()
                if (typed.size < MIN_PASSPHRASE) return message("The passphrase needs at least $MIN_PASSPHRASE characters.")
                work {
                    val p = need()
                    val opened = runBlocking { p.device.join("Folder $label", uri.toString(), typed) }
                    typed.fill(' ')
                    when (opened) {
                        is Opened.Ready -> { folderId = p.device.folders().last().id; profileId = null; "joined Folder $label" }
                        is Opened.Refused -> refusal(opened.reason)
                    }
                }
            }
            PICK_BACKUP -> work { val id = folderId ?: error("Select a folder first."); need().setBackup(id, uri); "backup folder set for ${label(need(), id)}" }
        }
    }

    private fun pick(code: Int, initial: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        intent.putExtra(
            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(initial)),
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, code)
    }

    /** Syncs every open folder; a folder whose key folder moved on opens again with its kept passphrase (SPEC:R109). Worker thread. */
    private fun syncAll(p: AndroidDevice): String = p.device.folders().mapNotNull { f ->
        val c = p.device.core(f.id) ?: return@mapNotNull "${f.label}: not open"
        runCatching {
            val r = runBlocking { c.syncNow() }
            if (!r.unlockAgain) "${f.label}: ${r.imported}" else {
                val kept = KeyVault.forFolder(this, f.id).loadPassphrase()
                if (kept == null) "${f.label}: the key folder changed; join again with the passphrase"
                else when (val o = runBlocking { p.device.reopen(f.id, kept) }.also { kept.fill(' ') }) {
                    is Opened.Ready -> "${f.label}: opened again with the kept passphrase"
                    is Opened.Refused -> "${f.label}: ${refusal(o.reason)}"
                }
            }
        }.getOrElse { "${f.label}: sync failed: ${it.message ?: it}" }
    }.joinToString("\n").ifEmpty { "No folder joined." }

    private fun refusal(reason: Unlock) = when (reason) {
        Unlock.KeyFileNotArrived -> "another device's key file has not arrived yet: wait for the sync and try again"
        Unlock.WrongPassphrase -> "this passphrase does not open the folder's key file"
        else -> "the key file did not unlock: $reason"
    }

    /** Runs [action] on the worker thread, then shows its message (null: none), or why it failed. */
    private fun work(action: () -> String?) {
        worker.execute {
            val outcome = runCatching(action)
            outcome.getOrElse { "Failed: ${it.message ?: it}" }?.let { said -> ui { lastMessage = said } }
            refresh()
        }
    }

    /** Runs [action] on the selected folder's core. */
    private fun onCore(what: String, action: suspend (MedicAllCore) -> String) = work {
        val c = need().device.core(folderId ?: error("Select a folder first.")) ?: error("The selected folder is not open.")
        runCatching { runBlocking { action(c) } }.getOrElse { "$what failed: ${it.message ?: it}" }
    }

    private fun need(): AndroidDevice = phone ?: error("Still starting.")
    private fun lock(): AppLock = need().device.lock
    private fun backup() = need().backupTarget(folderId ?: error("Select a folder first.")) ?: error("Pick the backup folder first.")
    private fun label(p: AndroidDevice, id: String?) = p.device.folders().firstOrNull { it.id == id }?.label ?: "none"

    private suspend fun selected(c: MedicAllCore): ProfileInfo {
        val live = c.profiles().filter { it.removedAt == null }
        return live.firstOrNull { it.id == profileId } ?: live.firstOrNull()?.also { profileId = it.id } ?: error("No live profile: add one first.")
    }

    private fun <T> next(items: List<T>, current: T?): T? = if (items.isEmpty()) null else items[(items.indexOf(current) + 1) % items.size]

    /** Redraws the folders, the selected folder's profiles with their switches, its files and bin, and the lock. Worker thread. */
    private fun refresh() {
        val p = phone ?: return
        if (p.device.lock.isLocked) return
        val text = runCatching {
            val folders = p.device.folders().joinToString("\n") { f: JoinedFolder ->
                "${if (f.id == folderId) "> " else "  "}${f.label}${if (p.device.core(f.id) == null) " (not open)" else ""} backup: ${if (f.backupUri == null) "not picked" else "picked"}"
            }
            val c = folderId?.let(p.device::core)
            val listing = if (c == null) "No folder selected." else runBlocking {
                val profiles = c.profiles().map { pr ->
                    "${if (pr.id == profileId) "> " else "  "}${pr.name} [${pr.kind}] born ${pr.birthDate ?: "-"}" +
                        " modules ${pr.modules.joinToString(",") { it.key }} reminders here ${c.remindersHere(pr.id)}" +
                        " shown here ${c.shownHere(pr.id)} from ${pr.createdDevice ?: "?"}${if (pr.removedAt != null) " in bin" else ""}"
                }.joinToString("\n")
                val files = c.files().joinToString("\n") { f -> "  ${f.name} ${f.sha256.take(8)}${if (f.removedAt != null) " in bin" else ""}" }
                val bin = c.binContents().joinToString(", ") { "${if (it.table == BLOB_REF) "file" else "profile"} ${it.id.take(8)}" }
                "Profiles:\n${profiles.ifEmpty { "  none" }}\nFiles:\n${files.ifEmpty { "  none" }}\nBin: ${bin.ifEmpty { "empty" }}"
            }
            val l = p.device.lock
            "Folders:\n${folders.ifEmpty { "  none" }}\n$listing\nLock: ${if (l.isOn) "on, ${p.lockMinutes()} min, fingerprint ${p.fingerprintOn()}" else "off"}"
        }.getOrElse { "Could not list: ${it.message ?: it}" }
        ui { stateView.text = text }
    }

    private fun show() {
        statusView.text = "Last: $lastMessage\nReport: $lastReport"
    }

    private fun ui(block: () -> Unit) = runOnUiThread { block(); show() }

    private fun message(text: String) { lastMessage = text; show() }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

    private fun heading(parent: LinearLayout, text: String) = parent.addView(TextView(this).apply { this.text = "\n$text" })

    private fun secretField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        // Test secrets are no account: keep the phone's password manager away from them.
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(View.OnClickListener { onClick() })
        })
    }

    private fun text(default: String) = textField.text.toString().trim().ifEmpty { default }

    private companion object {
        const val PICK_JOIN = 1
        const val PICK_BACKUP = 2
    }
}
