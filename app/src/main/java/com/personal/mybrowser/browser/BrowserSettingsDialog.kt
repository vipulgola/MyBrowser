package com.personal.mybrowser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrowserSettingsDialog(
    torEnabled: Boolean,
    onTorEnabledChange: (Boolean) -> Unit,
    biometricLockEnabled: Boolean,
    onBiometricLockEnabledChange: (Boolean) -> Unit,
    popupsAllowed: Boolean,
    onPopupsAllowedChange: (Boolean) -> Unit,
    error: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Browser settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Use Tor", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = torEnabled, onCheckedChange = onTorEnabledChange)
                }
                Text(
                    "When on, MyBrowser requires Orbot and blocks pages if Tor is unavailable. " +
                        "When off, websites can see your normal public IP. Switching closes open tabs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("App lock", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = biometricLockEnabled, onCheckedChange = onBiometricLockEnabledChange)
                }
                Text(
                    "Require biometric verification or your device screen lock when MyBrowser returns to the foreground.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Allow website pop-ups", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = popupsAllowed, onCheckedChange = onPopupsAllowedChange)
                }
                Text(
                    "When on, links you tap that request a new window open in a new tab. " +
                        "Automatic pop-ups stay blocked. Pop-ups never replace the current tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("This choice is saved on this device.", style = MaterialTheme.typography.bodySmall)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
