package com.personal.mybrowser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun BrowserHistoryDialog(
    entries: List<BrowserHistoryEntry>,
    onOpen: (BrowserHistoryEntry) -> Unit,
    onRemove: (BrowserHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("History") },
        text = {
            if (entries.isEmpty()) {
                Text("No saved pages yet.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { onOpen(entry) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        entry.title ?: SiteIdentity.hostname(entry.url) ?: entry.url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        entry.url,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT,
                                        ).format(Date(entry.visitedAtMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            TextButton(onClick = { onRemove(entry) }) { Text("Remove") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.padding(end = 8.dp)) {
                if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear all") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}
