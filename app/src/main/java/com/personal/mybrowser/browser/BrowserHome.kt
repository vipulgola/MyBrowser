package com.personal.mybrowser.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BrowserHome(
    onExperimentalEngine: () -> Unit,
    engineButtonLabel: String,
    adBlockingEnabled: Boolean,
    popupsAllowed: Boolean,
    torEnabled: Boolean,
    onOpenHistory: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(colors.background, colors.primaryContainer.copy(alpha = 0.55f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.primary,
                shadowElevation = 10.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("M", style = MaterialTheme.typography.displaySmall, color = colors.onPrimary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "A cleaner way to browse.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Search or enter an address in the bar above.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
                shadowElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Your browsing defaults",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ProtectionRow("HTTPS-only pages")
                    ProtectionRow(
                        if (torEnabled) "Pages use Tor or fail to load"
                        else "Tor off · sites see your normal public IP",
                        active = torEnabled,
                    )
                    ProtectionRow("Website location requests denied")
                    ProtectionRow(
                        if (adBlockingEnabled) "Known ad and tracker hosts blocked"
                        else "Ad and tracker blocking paused",
                        active = adBlockingEnabled,
                    )
                    ProtectionRow(
                        if (popupsAllowed) "Tapped pop-ups open in new tabs"
                        else "Website pop-ups blocked",
                        active = !popupsAllowed,
                    )
                    Text(
                        "Sites may still store data on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DefaultBrowserOption()
            if (onOpenHistory != null) TextButton(onClick = onOpenHistory) { Text("History") }
            if (onOpenSettings != null) TextButton(onClick = onOpenSettings) { Text("Settings") }
            TextButton(onClick = onExperimentalEngine) { Text(engineButtonLabel) }
        }
    }
}

@Composable
private fun ProtectionRow(label: String, active: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (active) "✓" else "•",
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(label, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
