package com.kryptsafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.crypto.CredentialKind
import com.kryptsafe.crypto.CredentialRules
import com.kryptsafe.crypto.WrongPinException
import com.kryptsafe.ui.theme.*
import com.kryptsafe.vault.DeviceKeyLostException
import com.kryptsafe.vault.LockedOutException
import com.kryptsafe.vault.VaultStore
import com.kryptsafe.vault.VaultWipedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step { CURRENT, CHOOSE, NEW, CONFIRM }

/**
 * Current -> choose type (6-digit PIN / longer PIN / passphrase) -> new -> confirm. The current credential is checked up
 * front with the same lockout and erase rules as unlocking, so this screen can't be used to guess it. New credentials
 * must pass the "too easy to guess" check. Arrays are wiped as soon as they're no longer needed.
 */
@Composable
fun ChangePinScreen(store: VaultStore, onClose: () -> Unit, onChanged: () -> Unit, onWiped: () -> Unit) {
    BackHandler(onBack = onClose)
    val currentKind = remember { store.credentialKind }
    var step by remember { mutableStateOf(Step.CURRENT) }
    var newKind by remember { mutableStateOf(currentKind) }
    var oldPin by remember { mutableStateOf<CharArray?>(null) }
    var newPin by remember { mutableStateOf<CharArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { oldPin?.fill('0'); newPin?.fill('0') } }

    fun describe(e: Throwable): String? = when (e) {
        is VaultWipedException -> { onWiped(); null }
        is WrongPinException -> "Wrong ${currentKind.noun}" + (store.attemptsLeftBeforeWipe()?.takeIf { it <= 3 }?.let { " — $it attempts left before the vault is erased" }
            ?: store.failedAttempts().let { if (it >= 5) " — wait ${store.lockoutRemainingMs() / 1000}s" else "" })
        is LockedOutException -> "Too many attempts. Wait ${e.remainingMs / 1000}s"
        is DeviceKeyLostException -> "This vault's key is missing on this phone, so it can't be opened."
        else -> "Something went wrong. Nothing was changed."
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        if (step == Step.CHOOSE) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.padding(top = 12.dp, bottom = 12.dp)) { GlassIconButton(onClick = onClose, contentDescription = "Close") { Icon(Icons.Default.Close, null, tint = Color.White) } }
                Text("How do you want to unlock?", fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp)
                Spacer(Modifier.height(6.dp))
                Text("A longer PIN or a passphrase is far harder to guess. You can change this again any time.", fontSize = 13.5.sp, color = TextSecondary, lineHeight = 19.sp)
                Spacer(Modifier.height(22.dp))
                CredentialKindList(current = currentKind) { newKind = it; step = Step.NEW }
                Spacer(Modifier.height(32.dp))
            }
        } else {
            AuthScreen(
                mode = when (step) { Step.CURRENT -> AuthMode.CHANGE_CURRENT; Step.NEW -> AuthMode.CHANGE_NEW; else -> AuthMode.CHANGE_CONFIRM },
                kind = if (step == Step.CURRENT) currentKind else newKind,
                error = error, biometricAvailable = false, busy = busy, showBrand = false, onBack = onClose, onBiometric = {},
                onPin = { pin ->
                    error = null
                    when (step) {
                        Step.CURRENT -> {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.Default) { runCatching { store.verifyPin(pin) } }
                                busy = false
                                r.onSuccess { oldPin = pin.copyOf(); step = Step.CHOOSE }.onFailure { error = describe(it) }
                                pin.fill('0')
                            }
                        }
                        Step.NEW -> {
                            val reason = CredentialRules.validate(newKind, pin)
                            if (reason != null) error = reason
                            else if (oldPin?.contentEquals(pin) == true) error = "Choose one different from your current ${currentKind.noun}."
                            else { newPin = pin.copyOf(); step = Step.CONFIRM }
                            pin.fill('0')
                        }
                        Step.CONFIRM -> {
                            if (!pin.contentEquals(newPin)) {
                                error = "They didn't match. Choose again."; newPin?.fill('0'); newPin = null; step = Step.NEW; pin.fill('0')
                            } else {
                                busy = true
                                scope.launch {
                                    val r = withContext(Dispatchers.Default) { runCatching { store.changeCredential(oldPin!!, pin, newKind) } }
                                    busy = false
                                    pin.fill('0')
                                    r.onSuccess { onChanged() }.onFailure { error = describe(it) }
                                }
                            }
                        }
                        else -> pin.fill('0')
                    }
                },
            )
        }
    }
}
