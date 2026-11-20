package com.kryptsafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptsafe.ui.theme.*

val AUTO_LOCK_OPTIONS = listOf(
    0L to "Immediately",
    30_000L to "After 30 seconds",
    60_000L to "After 1 minute",
    300_000L to "After 5 minutes",
)

@Composable
fun SettingsScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    autoLockMs: Long,
    keyProtection: String,
    credentialKind: com.kryptsafe.crypto.CredentialKind,
    wipeAfter: Int,
    onWipeAfterChange: (Int) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onAutoLockChange: (Long) -> Unit,
    onChangePin: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—" }
    var pickLock by remember { mutableStateOf(false) }
    var pickWipe by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onClick = onClose, contentDescription = "Close settings") { Icon(Icons.Default.Close, null, tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp)
            }

            SectionLabel("Security")
            KsCard(Modifier.fillMaxWidth(), corner = 26.dp) {
                Column {
                    SettingRow(
                        icon = { LockGlyph(22.dp, Color.White, hole = ChipBg) },
                        title = "Change PIN or passphrase", subtitle = "Now: ${credentialKind.title}. Your files stay as they are.",
                        onClick = onChangePin,
                    ) { Chevron() }
                    RowDivider()
                    SettingRow(
                        icon = { FingerprintGlyph(24.dp, Color.White) },
                        title = "Biometric unlock",
                        subtitle = when {
                            !biometricAvailable -> "Set up a fingerprint or face unlock in your phone's settings first"
                            biometricEnabled -> "Unlock with your fingerprint or face. Your PIN always works too."
                            else -> "Unlock without typing your PIN"
                        },
                        onClick = if (biometricAvailable) ({ onBiometricChange(!biometricEnabled) }) else null,
                    ) {
                        Switch(
                            checked = biometricEnabled && biometricAvailable, enabled = biometricAvailable, onCheckedChange = onBiometricChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink, checkedTrackColor = Color.White, checkedBorderColor = Color.White,
                                uncheckedThumbColor = TextSecondary, uncheckedTrackColor = ChipBg, uncheckedBorderColor = Hairline,
                                disabledUncheckedThumbColor = TextSecondary.copy(0.5f), disabledUncheckedTrackColor = ChipBg.copy(0.5f), disabledUncheckedBorderColor = Hairline,
                            ),
                        )
                    }
                    RowDivider()
                    SettingRow(
                        icon = { ClockGlyph(22.dp, Color.White) },
                        title = "Auto-lock", subtitle = "When to lock after you leave the app",
                        onClick = { pickLock = true },
                    ) {
                        Text(AUTO_LOCK_OPTIONS.firstOrNull { it.first == autoLockMs }?.second?.removePrefix("After ") ?: "Custom", fontSize = 13.sp, color = TextSecondary)
                        Chevron()
                    }
                    RowDivider()
                    SettingRow(
                        icon = { WarningGlyph(22.dp, if (wipeAfter > 0) Danger else Color.White) },
                        title = "Erase after failed attempts",
                        subtitle = if (wipeAfter > 0) "The vault is destroyed after $wipeAfter wrong attempts in a row." else "Off. Destroys everything after repeated wrong attempts.",
                        onClick = { pickWipe = true },
                    ) {
                        Text(if (wipeAfter > 0) "$wipeAfter tries" else "Off", fontSize = 13.sp, color = if (wipeAfter > 0) Danger else TextSecondary)
                        Chevron()
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("About")
            KsCard(Modifier.fillMaxWidth(), corner = 26.dp) {
                Column {
                    Text(
                        "KryptSafe is a private, encrypted vault for your photos, videos and documents. " +
                            "Everything you add is encrypted on this device and opened only with your PIN, passphrase or fingerprint. " +
                            "It works entirely offline, so your files never leave your phone.",
                        Modifier.padding(horizontal = 20.dp, vertical = 18.dp), fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                    )
                    RowDivider()
                    InfoRow("Encryption", "AES-256-GCM")
                    RowDivider()
                    InfoRow("Key derivation", "Argon2id")
                    RowDivider()
                    InfoRow("Key protection", "Bound to this phone · $keyProtection")
                    RowDivider()
                    InfoRow("Network access", "None - files can't leave this phone")
                    RowDivider()
                    InfoRow("Version", version)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "KryptSafe by Ziriverse\n© 2026 Ziriverse. All rights reserved.\nKeep your PIN or passphrase safe - it cannot be recovered.",
                Modifier.padding(start = 6.dp, top = 6.dp, bottom = 40.dp), fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp,
            )
        }
    }

    if (pickWipe) AlertDialog(
        onDismissRequest = { pickWipe = false },
        shape = RoundedCornerShape(28.dp), containerColor = CardBg,
        title = { Text("Erase after failed attempts", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                listOf(0 to "Off", 5 to "After 5 wrong attempts", 10 to "After 10 wrong attempts", 15 to "After 15 wrong attempts").forEach { (n, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { pickWipe = false; if (n == 0) onWipeAfterChange(0) else confirmWipe = n }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = n == wipeAfter, onClick = { pickWipe = false; if (n == 0) onWipeAfterChange(0) else confirmWipe = n },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = TextSecondary))
                        Text(label, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Wrong attempts are counted in a row and reset when you unlock successfully. Erased vaults cannot be recovered.", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { pickWipe = false }) { Text("Cancel", color = TextSecondary) } },
    )

    confirmWipe?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmWipe = null },
            shape = RoundedCornerShape(28.dp), containerColor = CardBg,
            title = { Text("Permanently erase after $n wrong attempts?", fontWeight = FontWeight.Medium) },
            text = { Text("If someone (or you, by accident, or a child playing with the phone) enters the wrong PIN or passphrase $n times in a row, ALL photos, videos and files in KryptSafe are destroyed. There is no way to get them back — not even for us. Only turn this on if you keep your originals elsewhere.", color = TextSecondary, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { onWipeAfterChange(n); confirmWipe = null }) { Text("Turn on", color = Danger, fontWeight = FontWeight.Medium) } },
            dismissButton = { TextButton(onClick = { confirmWipe = null }) { Text("Cancel", color = TextSecondary) } },
        )
    }

    if (pickLock) AlertDialog(
        onDismissRequest = { pickLock = false },
        shape = RoundedCornerShape(28.dp), containerColor = CardBg,
        title = { Text("Auto-lock", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                AUTO_LOCK_OPTIONS.forEach { (ms, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onAutoLockChange(ms); pickLock = false }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = ms == autoLockMs, onClick = { onAutoLockChange(ms); pickLock = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = TextSecondary))
                        Text(label, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Immediately is the most private. A longer delay is handy if you switch apps a lot.", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { pickLock = false }) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, Modifier.padding(start = 6.dp, bottom = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary, lineHeight = 18.sp)

@Composable
private fun Chevron() = Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(22.dp))

@Composable
private fun RowDivider() = Box(Modifier.fillMaxWidth().padding(start = 76.dp).height(1.dp).background(Hairline))

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(ChipBg), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
            Text(subtitle, fontSize = 12.5.sp, color = TextSecondary, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(0.45f))
        Text(value, fontSize = 13.5.sp, color = TextSecondary, lineHeight = 18.sp, modifier = Modifier.weight(0.55f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
