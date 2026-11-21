package com.kryptsafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.kryptsafe.crypto.CredentialKind
import com.kryptsafe.ui.theme.*
import kotlin.math.roundToInt

const val PIN_LEN = 6

enum class AuthMode { SETUP_ENTER, SETUP_CONFIRM, UNLOCK, CHANGE_CURRENT, CHANGE_NEW, CHANGE_CONFIRM }

@Composable
fun AuthScreen(
    mode: AuthMode,
    error: String?,
    biometricAvailable: Boolean,
    busy: Boolean = false,
    showBrand: Boolean = true,
    kind: CredentialKind = CredentialKind.PIN6,
    onBack: (() -> Unit)? = null,
    /** When set, shows a link to pick the unlock type: "Use a longer PIN or passphrase" at setup, "Use a different unlock method" when unlocking. */
    onChooseKind: (() -> Unit)? = null,
    onPin: (CharArray) -> Unit,
    onBiometric: () -> Unit,
) {
    var entry by remember(mode, kind) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    // A wrong entry clears the field and shakes so the user knows to retry.
    LaunchedEffect(error) {
        if (error != null) {
            entry = ""
            repeat(3) { shake.animateTo(14f, tween(45)); shake.animateTo(-14f, tween(45)) }
            shake.animateTo(0f, tween(45))
        }
    }
    val noun = kind.noun
    val title = when (mode) {
        AuthMode.SETUP_ENTER -> "Create your $noun"
        AuthMode.SETUP_CONFIRM -> "Confirm your $noun"
        AuthMode.UNLOCK -> "Welcome back"
        AuthMode.CHANGE_CURRENT -> "Enter current $noun"
        AuthMode.CHANGE_NEW -> "Choose a new $noun"
        AuthMode.CHANGE_CONFIRM -> "Confirm new $noun"
    }
    val sizeHint = if (kind.autoSubmit) "6 digits" else if (kind.digitsOnly) "${kind.minLen}–${kind.maxLen} digits" else "at least ${kind.minLen} characters"
    val subtitle = when {
        busy -> "Securing your vault…"
        mode == AuthMode.SETUP_ENTER || mode == AuthMode.CHANGE_NEW -> "Choose $sizeHint. If you forget it, your files can't be recovered."
        mode == AuthMode.SETUP_CONFIRM || mode == AuthMode.CHANGE_CONFIRM -> "Enter the same $noun once more."
        mode == AuthMode.CHANGE_CURRENT -> "Confirm it's you before changing your $noun."
        else -> "Unlock your private vault"
    }
    val submit = { if (!busy && entry.length >= kind.minLen) onPin(entry.toCharArray()) }
    val showBio = biometricAvailable && mode == AuthMode.UNLOCK

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).windowInsetsPadding(WindowInsets.ime).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showBrand) { BrandMark(); Spacer(Modifier.height(24.dp)) }
            KsCard(Modifier.fillMaxWidth(), corner = 28.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 29.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, fontSize = 13.sp, textAlign = TextAlign.Center, color = TextSecondary, lineHeight = 18.sp)
                    Spacer(Modifier.height(24.dp))

                    when {
                        kind.autoSubmit -> {
                            Row(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                repeat(PIN_LEN) { i -> PinDot(filled = i < entry.length) }
                            }
                        }
                        kind == CredentialKind.PIN_LONG -> MaskedReadout(entry.length, "Enter ${kind.minLen}–${kind.maxLen} digits", Modifier.offset { IntOffset(shake.value.roundToInt(), 0) })
                        else -> PassphraseField(entry, { if (it.length <= kind.maxLen) entry = it }, Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, onDone = submit)
                    }

                    Box(Modifier.heightIn(min = 34.dp).padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        if (error != null) Text(error, color = Danger, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 17.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    if (kind != CredentialKind.PASSPHRASE) {
                        PinPad(
                            showBiometric = showBio,
                            onDigit = { d ->
                                if (!busy && entry.length < kind.maxLen) {
                                    entry += d
                                    if (kind.autoSubmit && entry.length == kind.maxLen) onPin(entry.toCharArray())
                                }
                            },
                            onDelete = { entry = entry.dropLast(1) },
                            onBiometric = onBiometric,
                        )
                    }
                    if (!kind.autoSubmit) {
                        Spacer(Modifier.height(if (kind == CredentialKind.PASSPHRASE) 4.dp else 22.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val enabled = !busy && entry.length >= kind.minLen
                            Box(
                                Modifier.weight(1f).height(54.dp).clip(CircleShape)
                                    .background(if (enabled) Color.White else ChipBg).clickable(enabled = enabled, onClick = submit),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "Securing…" else if (mode == AuthMode.UNLOCK) "Unlock" else "Continue", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (enabled) Ink else TextSecondary) }
                            if (kind == CredentialKind.PASSPHRASE && showBio) GlassIconButton(onClick = onBiometric, size = 54.dp, contentDescription = "Unlock with biometrics") { FingerprintGlyph(28.dp, Color.White) }
                        }
                    }
                    if (onChooseKind != null) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (mode == AuthMode.UNLOCK) "Use a different unlock method" else "Use a longer PIN or passphrase", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onChooseKind).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (onBack != null) GlassIconButton(
            onClick = onBack, contentDescription = "Back",
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
        ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, null, tint = Color.White) }
    }
}

/** Shows only how many digits were typed — "•••••••" — never the digits themselves. */
@Composable
private fun MaskedReadout(length: Int, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(ChipBg).border(1.dp, Hairline, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        if (length == 0) Text(hint, fontSize = 15.sp, color = TextSecondary)
        else Text("•".repeat(length), fontSize = 26.sp, letterSpacing = 3.sp, maxLines = 1, color = Color.White)
    }
}

@Composable
private fun PassphraseField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, onDone: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(ChipBg).border(1.dp, Hairline, RoundedCornerShape(18.dp)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Enter passphrase", fontSize = 16.sp, color = TextSecondary)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = Jakarta),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation('•'),
                // Password keyboard: no suggestions, no learning, no autocorrect of what you type.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Text(
            if (show) "Hide" else "Show", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(CircleShape).clickable { show = !show }.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** App mark: the gold K-key tile, same artwork as the launcher icon and splash. */
@Composable
fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.kryptsafe.R.drawable.ic_logo_tile), null, Modifier.size(72.dp))
        Spacer(Modifier.height(14.dp))
        Text("KryptSafe", fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 33.sp)
        Text("by Ziriverse", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val scale by animateFloatAsState(if (filled) 1.15f else 1f, tween(140), label = "dot")
    Box(
        Modifier.size(15.dp).scale(scale).clip(CircleShape)
            .then(
                if (filled) Modifier.background(Color.White)
                else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            )
    )
}

@Composable
private fun PinPad(showBiometric: Boolean, onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { r.forEach { c -> KeyButton(onClick = { onDigit(c) }) { pressed -> Text(c.toString(), fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (pressed) Ink else TextPrimary) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (showBiometric) KeyButton(onClick = onBiometric, plain = true) { FingerprintGlyph(30.dp, Color.White) } else Spacer(Modifier.size(72.dp))
            KeyButton(onClick = { onDigit('0') }) { pressed -> Text("0", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = if (pressed) Ink else TextPrimary) }
            KeyButton(onClick = onDelete, plain = true) { BackspaceGlyph(28.dp, TextSecondary) }
        }
    }
}

/** Key: muted-slate circle that flips to the white "active" style while pressed. */
@Composable
private fun KeyButton(onClick: () -> Unit, plain: Boolean = false, content: @Composable (pressed: Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "key")
    Box(
        Modifier.size(72.dp).scale(scale).clip(CircleShape)
            .then(if (plain) Modifier else Modifier.background(if (pressed) Color.White else ChipBg).border(1.dp, Hairline, CircleShape))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(pressed) }
}

/** Three-option picker used at first setup and when changing credentials. */
@Composable
fun CredentialKindList(current: CredentialKind?, onPick: (CredentialKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CredentialKind.entries.forEach { k ->
            KsCard(Modifier.fillMaxWidth().clickable { onPick(k) }, corner = 22.dp) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(k.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
                        Text(k.blurb, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 17.sp)
                    }
                    if (k == current) GlassBadge { Text("Current", fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 11.sp) }
                }
            }
        }
    }
}
