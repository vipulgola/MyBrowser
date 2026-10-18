package com.personal.mybrowser.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.mybrowser.network.AddressValidationException

/** Keeps each tab's WebView composed so switching preserves its page and history. */
@Composable
fun NormalBrowserTabs(
    navigationRequest: BrowserNavigation?,
    onCurrentUrlChange: (String?) -> Unit,
    onOwnEngine: (String?) -> Unit,
    popupsAllowed: Boolean,
    torEnabled: Boolean,
    darkPagesEnabled: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabsState = remember { BrowserTabsState() }
    var homeRequestToken by remember { mutableLongStateOf(0L) }
    var overflowMenuRequestToken by remember { mutableLongStateOf(0L) }
    var tabPickerVisible by remember { mutableStateOf(false) }
    var addressEditorVisible by remember { mutableStateOf(true) }
    var addressEditorValue by remember { mutableStateOf("") }
    var addressEditorError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navigationRequest?.id) {
        navigationRequest?.let(tabsState::route)
    }
    LaunchedEffect(tabsState.selectedId) {
        addressEditorValue = tabsState.selectedTab.url.orEmpty()
        addressEditorVisible = tabsState.selectedTab.url == null
        addressEditorError = null
    }

    fun showAddressEditor() {
        addressEditorValue = tabsState.selectedTab.url.orEmpty()
        addressEditorError = null
        addressEditorVisible = true
    }
    fun openNewTab(url: String? = null) {
        tabsState.openTab(url)
        onCurrentUrlChange(url)
        addressEditorValue = url.orEmpty()
        addressEditorVisible = url == null
        addressEditorError = null
    }
    fun submitAddressEditor() {
        try {
            val target = CompatibleNavigation.address(addressEditorValue)
            tabsState.navigateSelected(target)
            onCurrentUrlChange(target)
            addressEditorValue = target
            addressEditorError = null
            addressEditorVisible = false
        } catch (invalid: AddressValidationException) {
            addressEditorError = invalid.message ?: "Enter a valid HTTPS address."
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrivacyBrowserRail(
            tab = tabsState.selectedTab,
            tabCount = tabsState.tabs.size,
            addressEditorVisible = addressEditorVisible,
            addressEditorValue = addressEditorValue,
            addressEditorError = addressEditorError,
            onAddressEditorValueChange = {
                addressEditorValue = it
                addressEditorError = null
            },
            onAddressEditorSubmit = ::submitAddressEditor,
            onAddressEditorDismiss = {
                addressEditorVisible = false
                addressEditorError = null
            },
            onHome = {
                homeRequestToken++
                addressEditorValue = ""
                addressEditorError = null
                addressEditorVisible = true
            },
            onAddress = ::showAddressEditor,
            onNewTab = { openNewTab() },
            onShowTabs = { tabPickerVisible = true },
            onShowMenu = { overflowMenuRequestToken++ },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabsState.tabs.forEach { tab ->
                key(tab.id) {
                    val selected = tab.id == tabsState.selectedId
                    CompatibleBrowser(
                        navigationRequest = tab.navigationRequest,
                        onCurrentUrlChange = { url ->
                            tabsState.updateUrl(tab.id, url)
                            if (tabsState.selectedId == tab.id) {
                                onCurrentUrlChange(url)
                                if (url == null) {
                                    addressEditorValue = ""
                                    addressEditorError = null
                                    addressEditorVisible = true
                                }
                            }
                        },
                        onOwnEngine = { url -> if (tabsState.selectedId == tab.id) onOwnEngine(url) },
                        homeRequestToken = if (selected) homeRequestToken else 0L,
                        overflowMenuRequestToken = if (selected) overflowMenuRequestToken else 0L,
                        externalChrome = true,
                        popupsAllowed = popupsAllowed,
                        torEnabled = torEnabled,
                        darkPagesEnabled = darkPagesEnabled,
                        onOpenNewTab = { url ->
                            if (tabsState.selectedId == tab.id) openNewTab(url)
                        },
                        onOpenSettings = { if (tabsState.selectedId == tab.id) onOpenSettings() },
                        isActive = selected,
                        modifier = if (selected) Modifier.fillMaxSize() else Modifier.size(0.dp),
                    )
                }
            }
        }
    }

    if (tabPickerVisible) {
        BrowserTabPicker(
            tabs = tabsState.tabs,
            selectedId = tabsState.selectedId,
            onSelect = { id ->
                if (tabsState.selectTab(id)) {
                    onCurrentUrlChange(tabsState.selectedTab.url)
                    addressEditorValue = tabsState.selectedTab.url.orEmpty()
                    addressEditorVisible = tabsState.selectedTab.url == null
                    addressEditorError = null
                }
                tabPickerVisible = false
            },
            onClose = { id ->
                val selectedTab = tabsState.closeTab(id)
                onCurrentUrlChange(selectedTab.url)
                addressEditorValue = selectedTab.url.orEmpty()
                addressEditorVisible = selectedTab.url == null
                addressEditorError = null
            },
            onDismiss = { tabPickerVisible = false },
        )
    }
}

@Composable
private fun PrivacyBrowserRail(
    tab: BrowserTab,
    tabCount: Int,
    addressEditorVisible: Boolean,
    addressEditorValue: String,
    addressEditorError: String?,
    onAddressEditorValueChange: (String) -> Unit,
    onAddressEditorSubmit: () -> Unit,
    onAddressEditorDismiss: () -> Unit,
    onHome: () -> Unit,
    onAddress: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onShowMenu: () -> Unit,
) {
    if (addressEditorVisible) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addressEditorValue,
                onValueChange = onAddressEditorValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search or enter address") },
                singleLine = true,
                isError = addressEditorError != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onAddressEditorSubmit() }),
                trailingIcon = {
                    if (tab.url != null) IconButton(
                        onClick = onAddressEditorDismiss,
                        modifier = Modifier.semantics { contentDescription = "Close address editor" },
                    ) { Text("×", style = MaterialTheme.typography.titleLarge) }
                },
                shape = RoundedCornerShape(26.dp),
            )
        }
        return
    }

    val siteLabel = SiteIdentity.hostname(tab.url) ?: "Private search"
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onHome,
            modifier = Modifier.semantics { contentDescription = "Home" },
        ) { Text("⌂", style = MaterialTheme.typography.headlineSmall) }
        FilterChip(
            selected = true,
            onClick = onAddress,
            label = { Text(siteLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.weight(1f).widthIn(max = 360.dp),
            shape = RoundedCornerShape(24.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        IconButton(
            onClick = onNewTab,
            modifier = Modifier.semantics { contentDescription = "New tab" },
        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        TextButton(
            onClick = onShowTabs,
            modifier = Modifier.semantics { contentDescription = "$tabCount open tabs" },
        ) { Text(tabCount.toString(), style = MaterialTheme.typography.titleMedium) }
        IconButton(
            onClick = onShowMenu,
            modifier = Modifier.semantics { contentDescription = "Browser options" },
        ) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun BrowserTabPicker(
    tabs: List<BrowserTab>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { index, tab ->
                    val title = SiteIdentity.hostname(tab.url) ?: "New tab"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onSelect(tab.id) }, modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (tab.id == selectedId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    tab.url ?: "Private search",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onClose(tab.id) },
                            modifier = Modifier.semantics {
                                contentDescription = "Close tab ${index + 1}: $title"
                            },
                        ) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}
