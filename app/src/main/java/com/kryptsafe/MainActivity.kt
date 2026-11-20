package com.kryptsafe

import android.os.Bundle
import android.view.WindowManager
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.kryptsafe.crypto.MediaKind
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kryptsafe.crypto.CredentialRules
import com.kryptsafe.crypto.WrongPinException
import com.kryptsafe.security.BiometricHelper
import com.kryptsafe.ui.*
import com.kryptsafe.ui.theme.KryptSafeTheme
import com.kryptsafe.vault.LockedOutException
import com.kryptsafe.vault.Session
import com.kryptsafe.vault.VaultStore
import com.kryptsafe.vault.VaultViewModel

private val TEXT_EXT = setOf("txt", "md", "json", "xml", "csv", "log", "yml", "yaml", "ini", "conf", "html", "css", "js", "kt", "java", "py")

class MainActivity : FragmentActivity() {
    private lateinit var store: VaultStore
    private lateinit var bio: BiometricHelper
    private var unlocked by mutableStateOf(false)
    private val vm: VaultViewModel by viewModels()
    // True while a system picker/dialog is on top, so leaving the app for it doesn't auto-lock.
    private var externalUi = false
    private var stoppedAt = 0L
    private var pendingExportIds: Set<String> = emptySet()

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        externalUi = false; vm.import(uris)
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        externalUi = false; vm.import(uris)
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        externalUi = false
        if (tree != null && pendingExportIds.isNotEmpty()) vm.export(pendingExportIds, tree)
        pendingExportIds = emptySet()
    }
    private val deleteRequest = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        externalUi = false
        vm.message = if (r.resultCode == Activity.RESULT_OK) "Originals deleted from gallery" else "Originals kept in gallery"
        vm.deletable = emptyList()
    }

    /**
     * Photo-picker URIs (content://media/picker/...) can't be passed to createDeleteRequest. For local
     * items the trailing id equals the MediaStore _id, so rebuild the real MediaStore URI from it.
     */
    private fun toMediaStoreUri(u: android.net.Uri): android.net.Uri {
        if (!u.toString().contains("/picker/")) return u
        val id = u.lastPathSegment?.toLongOrNull() ?: return u
        val mime = contentResolver.getType(u) ?: return u
        val base = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return u
        }
        return android.content.ContentUris.withAppendedId(base, id)
    }

    private fun deleteOriginals(uris: List<android.net.Uri>) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                externalUi = true
                val mediaUris = uris.map(::toMediaStoreUri)
                val sender = MediaStore.createDeleteRequest(contentResolver, mediaUris).intentSender
                deleteRequest.launch(IntentSenderRequest.Builder(sender).build())
                return
            } catch (e: Exception) { android.util.Log.w("KryptSafe", "createDeleteRequest failed for " + uris.firstOrNull(), e); externalUi = false }
        }
        val n = vm.deleteOriginalsDirectly(uris)
        vm.message = if (n == uris.size) "Originals deleted" else "Could not delete $n of ${uris.size} originals — remove them from your gallery manually"
        vm.deletable = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps the splash theme for the real one once the first frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Blocks screenshots, screen recording and the recent-apps thumbnail.
        // Skipped in debuggable builds only, so UI can be screenshotted during development.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Tapjacking defence: ignore touches while another app draws over us (a fake overlay could capture the PIN).
        if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { window.setHideOverlayWindows(true) }
        store = VaultStore(this)
        bio = BiometricHelper(this, store)
        unlocked = Session.isUnlocked

        setContent {
            KryptSafeTheme {
                // Branded splash only on a true cold start (not on rotation / process-alive returns).
                var showSplash by remember { mutableStateOf(savedInstanceState == null) }
                var mode by remember { mutableStateOf(if (store.isSetUp) AuthMode.UNLOCK else AuthMode.SETUP_ENTER) }
                var firstPin by remember { mutableStateOf<CharArray?>(null) }
                var kind by remember { mutableStateOf(store.credentialKind) }
                var showKindPicker by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                var bioEnabled by remember { mutableStateOf(store.biometricEnabled) }

                if (unlocked) {
                    var viewerId by remember { mutableStateOf<String?>(null) }
                    var videoItem by remember { mutableStateOf<com.kryptsafe.vault.VaultItem?>(null) }
                    var pdfItem by remember { mutableStateOf<com.kryptsafe.vault.VaultItem?>(null) }
                    var textItem by remember { mutableStateOf<com.kryptsafe.vault.VaultItem?>(null) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showChangePin by remember { mutableStateOf(false) }
                    var autoLock by remember { mutableLongStateOf(store.autoLockMs) }
                    var wipeAfter by remember { mutableIntStateOf(store.wipeAfter) }
                    var credKind by remember { mutableStateOf(store.credentialKind) }
                    LaunchedEffect(Unit) { vm.refresh() }
                    LaunchedEffect(vm.message) {
                        vm.message?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show(); vm.message = null }
                    }
                    GalleryScreen(
                        items = vm.items,
                        busyMessage = vm.busyMessage,
                        thumbnail = vm::thumbnail,
                        biometricAvailable = bio.isAvailable(),
                        biometricEnabled = bioEnabled,
                        onOpen = { item ->
                            val ext = item.meta.name.substringAfterLast('.', "").lowercase()
                            when {
                                item.meta.kind == MediaKind.IMAGE -> viewerId = item.id
                                item.meta.kind == MediaKind.VIDEO -> videoItem = item
                                item.meta.mime == "application/pdf" || ext == "pdf" -> pdfItem = item
                                item.meta.mime.startsWith("text/") || ext in TEXT_EXT -> textItem = item
                                else -> Toast.makeText(this@MainActivity, "No preview for this file type. Use Restore to open it elsewhere.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onImportMedia = {
                            externalUi = true
                            mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onImportFiles = { externalUi = true; filePicker.launch(arrayOf("application/pdf", "text/*", "*/*")) },
                        onDelete = { ids -> vm.delete(ids) },
                        onExport = { ids -> pendingExportIds = ids; externalUi = true; folderPicker.launch(null) },
                        onOpenSettings = { showSettings = true },
                        onLock = ::lockVault,
                    )
                    viewerId?.let { id ->
                        val images = vm.items.filter { it.meta.kind == MediaKind.IMAGE }
                        if (images.any { it.id == id })
                            ImageViewerScreen(images, id, vm, onClose = { viewerId = null })
                        else viewerId = null
                    }
                    videoItem?.let { v -> VideoPlayerScreen(vm.items.filter { it.meta.kind == MediaKind.VIDEO }, v.id, vm) { videoItem = null } }
                    pdfItem?.let { PdfReaderScreen(it, vm) { pdfItem = null } }
                    textItem?.let { TextReaderScreen(it, vm) { textItem = null } }
                    if (showSettings) SettingsScreen(
                        biometricAvailable = bio.isAvailable(),
                        biometricEnabled = bioEnabled,
                        autoLockMs = autoLock,
                        keyProtection = remember { com.kryptsafe.vault.DeviceKey.protectionLabel() },
                        credentialKind = credKind,
                        wipeAfter = wipeAfter,
                        onWipeAfterChange = { n -> wipeAfter = n; store.wipeAfter = n },
                        onBiometricChange = { on ->
                            if (on) Session.masterKey?.let { k -> bio.enable(k) { bioEnabled = it } }
                            else { bio.disable(); bioEnabled = false }
                        },
                        onAutoLockChange = { ms -> autoLock = ms; store.autoLockMs = ms },
                        onChangePin = { showChangePin = true },
                        onClose = { showSettings = false },
                    )
                    if (showChangePin) ChangePinScreen(store, onClose = { showChangePin = false }, onChanged = { showChangePin = false; credKind = store.credentialKind; vm.message = "Unlock method updated" }, onWiped = ::onVaultWiped)
                    if (vm.deletable.isNotEmpty() && vm.busyMessage == null) AlertDialog(
                        onDismissRequest = { vm.deletable = emptyList() },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        containerColor = com.kryptsafe.ui.theme.CardBg,
                        title = { Text("Delete originals?", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
                        text = { Text("Your files are now encrypted in KryptSafe. Delete the unprotected originals from your gallery so other apps can no longer see them?", color = com.kryptsafe.ui.theme.TextSecondary) },
                        confirmButton = { TextButton(onClick = { deleteOriginals(vm.deletable) }) { Text("Delete originals", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) } },
                        dismissButton = { TextButton(onClick = { vm.deletable = emptyList() }) { Text("Keep", color = com.kryptsafe.ui.theme.TextSecondary) } },
                    )
                } else {
                    // Auto-prompt biometrics when the lock screen appears.
                    LaunchedEffect(mode, bioEnabled) {
                        if (mode == AuthMode.UNLOCK && bioEnabled && bio.isAvailable()) tryBiometric { error = it }
                    }
                    AuthScreen(
                        mode = mode,
                        kind = kind,
                        error = error,
                        biometricAvailable = bioEnabled && bio.isAvailable(),
                        onBiometric = { tryBiometric { error = it } },
                        busy = busy,
                        onChooseKind = if (mode == AuthMode.SETUP_ENTER) ({ showKindPicker = true }) else null,
                        onPin = { pin ->
                            error = null
                            when (mode) {
                                AuthMode.SETUP_ENTER -> {
                                    val reason = CredentialRules.validate(kind, pin)
                                    if (reason != null) error = reason else { firstPin = pin.copyOf(); mode = AuthMode.SETUP_CONFIRM }
                                    pin.fill('0')
                                }
                                AuthMode.SETUP_CONFIRM ->
                                    if (pin.contentEquals(firstPin)) {
                                        busy = true
                                        scope.launch {
                                            // The key stretching is deliberately slow: never run it on the main thread.
                                            withContext(Dispatchers.Default) { store.setUp(pin, kind) }
                                            pin.fill('0'); firstPin?.fill('0'); firstPin = null
                                            busy = false; unlocked = true
                                        }
                                    } else { error = "They didn't match. Start again."; firstPin?.fill('0'); firstPin = null; mode = AuthMode.SETUP_ENTER; pin.fill('0') }
                                AuthMode.UNLOCK -> {
                                    busy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.Default) { runCatching { store.unlockWithPin(pin) } }
                                        pin.fill('0'); busy = false
                                        result.onSuccess { unlocked = true }.onFailure { e ->
                                            if (e is com.kryptsafe.vault.VaultWipedException) { onVaultWiped(); return@onFailure }
                                            error = when (e) {
                                                is WrongPinException -> "Wrong ${kind.noun}" + (store.attemptsLeftBeforeWipe()?.takeIf { it <= 3 }?.let { " — $it attempts left before the vault is erased" }
                                                    ?: store.failedAttempts().let { if (it >= 5) " — wait ${store.lockoutRemainingMs() / 1000}s" else "" })
                                                is LockedOutException -> "Too many attempts. Wait ${e.remainingMs / 1000}s"
                                                is com.kryptsafe.vault.DeviceKeyLostException -> "This vault's key is missing on this phone, so it can't be opened."
                                                else -> "Something went wrong"
                                            }
                                        }
                                    }
                                }
                                else -> pin.fill('0') // change-credential modes are handled in ChangePinScreen
                            }
                        },
                    )
                    if (showKindPicker) AlertDialog(
                        onDismissRequest = { showKindPicker = false },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), containerColor = com.kryptsafe.ui.theme.CardBg,
                        title = { Text("How do you want to unlock?", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                        text = { CredentialKindList(current = kind) { kind = it; showKindPicker = false; firstPin?.fill('0'); firstPin = null; mode = AuthMode.SETUP_ENTER } },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { showKindPicker = false }) { Text("Cancel", color = com.kryptsafe.ui.theme.TextSecondary) } },
                    )
                }
                if (showSplash) BrandSplash(onDone = { showSplash = false })
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        (findViewById<android.view.ViewGroup>(android.R.id.content))?.let { c ->
            c.filterTouchesWhenObscured = true
            for (i in 0 until c.childCount) c.getChildAt(i).filterTouchesWhenObscured = true
        }
    }

    private fun tryBiometric(onError: (String?) -> Unit) =
        bio.unlock({ key -> Session.open(key); unlocked = true }, { msg -> onError(msg) })

    /** The opt-in erase limit was hit: everything is already destroyed; restart into first-time setup. */
    private fun onVaultWiped() {
        vm.clear()
        android.widget.Toast.makeText(this, "Vault erased after too many wrong attempts", android.widget.Toast.LENGTH_LONG).show()
        recreate()
    }

    private fun lockVault() { Session.lock(); vm.clear(); unlocked = false; recreate() }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockRunnable = Runnable { lockQuietly() }

    private fun lockQuietly() { Session.lock(); vm.clear(); unlocked = false }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        // Leaving the app (except for our own system pickers) locks it — immediately, or after the user's chosen delay.
        if (!isChangingConfigurations && Session.isUnlocked && !externalUi) {
            val delay = store.autoLockMs
            if (delay <= 0) lockQuietly() else handler.postDelayed(lockRunnable, delay)
        }
    }

    override fun onStart() {
        super.onStart()
        val delay = store.autoLockMs
        val away = System.currentTimeMillis() - stoppedAt
        handler.removeCallbacks(lockRunnable)
        // Timer may not have fired (process frozen): enforce it by elapsed time. Also the long-parked-picker safety net.
        if (Session.isUnlocked && ((delay > 0 && stoppedAt > 0 && away > delay) || (externalUi && away > 120_000))) {
            lockQuietly(); externalUi = false
        }
    }
}
