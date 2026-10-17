package com.personal.mybrowser

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.personal.mybrowser.browser.BrowserViewModel
import com.personal.mybrowser.browser.CompatibleBrowser
import com.personal.mybrowser.browser.CompatibleNavigation
import com.personal.mybrowser.browser.PageContent
import com.personal.mybrowser.browser.DemoPages
import com.personal.mybrowser.browser.PageForm
import com.personal.mybrowser.browser.WebPageState
import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.html.HtmlParser
import com.personal.mybrowser.engine.render.HtmlPageView
import com.personal.mybrowser.network.AddressValidationException
import com.personal.mybrowser.ui.theme.MyBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val browser = ViewModelProvider(this)[BrowserViewModel::class.java]
        setContent {
            MyBrowserTheme {
                var compatibleMode by remember { mutableStateOf(false) }
                var compatibleStart by remember { mutableStateOf("") }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (compatibleMode) {
                        CompatibleBrowser(
                            startAddress = compatibleStart,
                            onOwnEngine = { currentUrl ->
                                compatibleMode = false
                                if (currentUrl != null && CompatibleNavigation.isAllowedNavigation(currentUrl)) {
                                    browser.editAddress(currentUrl)
                                    browser.submitAddress()
                                }
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        BrowserDemo(
                            browser = browser,
                            onCompatible = {
                                compatibleStart = when (val page = browser.webPage) {
                                    is WebPageState.Ready -> page.page.url
                                    is WebPageState.Loading -> page.address.value
                                    is WebPageState.Failed -> page.address.value
                                    WebPageState.Local -> browser.addressInput
                                }
                                browser.stop()
                                compatibleMode = true
                            },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserDemo(browser: BrowserViewModel, modifier: Modifier = Modifier, onCompatible: () -> Unit = {}) {
    val navigator = browser.navigator
    val webPage = browser.webPage
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val submit = { if (browser.submitAddress()) focusManager.clearFocus() }
    BackHandler(enabled = browser.canGoBack) { browser.back() }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = browser.addressInput,
                    onValueChange = browser::editAddress,
                    modifier = Modifier.weight(1f),
                    label = { Text("Search or enter address") },
                    placeholder = { Text("Search DuckDuckGo") },
                    singleLine = true,
                    isError = browser.addressError != null,
                    supportingText = browser.addressError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                Button(onClick = submit) { Text("Go") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = browser::back, enabled = browser.canGoBack) { Text("Back") }
                TextButton(onClick = browser::forward, enabled = browser.canGoForward) { Text("Forward") }
                if (webPage is WebPageState.Loading) {
                    TextButton(onClick = browser::stop) { Text("Stop") }
                } else {
                    TextButton(onClick = browser::reload) { Text("Reload") }
                }
            }
            Text(
                text = when (webPage) {
                    WebPageState.Local -> "Local demo"
                    is WebPageState.Loading -> webPage.address.value
                    is WebPageState.Failed -> webPage.address.value
                    is WebPageState.Ready -> webPage.page.url
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (webPage != WebPageState.Local) {
                Button(onClick = browser::showLocalDemo) { Text("Local demo") }
            }
            TextButton(onClick = onCompatible) { Text("Compatible view (Android WebView)") }
        }
        when (webPage) {
            is WebPageState.Loading -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Loading page…", modifier = Modifier.padding(top = 16.dp))
            }
            is WebPageState.Failed -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(webPage.message)
                Button(onClick = browser::retry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            else -> {
                val html = (webPage as? WebPageState.Ready)?.page?.html ?: navigator.currentHtml
                val content = remember(navigator, webPage) { PageContent.parse(html) }
                when (content) {
                    PageContent.TooComplex -> Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("This page is too complex for this browser yet.")
                        Button(onClick = browser::back, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Go back")
                        }
                    }
                    is PageContent.Ready -> {
                        val form = remember(content.document, webPage) {
                            (webPage as? WebPageState.Ready)?.page?.url?.let { PageForm.find(content.document, it) }
                        }
                        val navigate: (String) -> Unit = { target ->
                            if (!browser.open(target)) {
                                val message = browser.linkError ?: if (webPage == WebPageState.Local) {
                                    "This demo opens bundled pages only."
                                } else {
                                    "Wait for the page to finish loading."
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (form != null) PageFormControls(form, navigate)
                            BrowserPage(
                                document = content.document,
                                pageUrl = (webPage as? WebPageState.Ready)?.page?.url,
                                onLinkClick = navigate,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageFormControls(form: PageForm, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var value by remember(form) { mutableStateOf(form.initialValue) }
    val submit = {
        try {
            navigate(form.submissionUrl(value).value)
        } catch (error: AddressValidationException) {
            Toast.makeText(context, error.message ?: "This form cannot be submitted.", Toast.LENGTH_SHORT).show()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.weight(1f),
            label = { Text(form.fieldLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Button(onClick = submit) { Text(form.submitLabel) }
    }
}

@Composable
private fun BrowserPage(
    document: HtmlNode.Document,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String? = null,
) {
    val foreground = MaterialTheme.colorScheme.onBackground.toArgb()
    val background = MaterialTheme.colorScheme.background.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    HtmlPageView(context),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
                )
            }
        },
        update = { scrollView ->
            val changed = (scrollView.getChildAt(0) as HtmlPageView)
                .show(document, pageUrl, foreground, background, linkColor, onLinkClick)
            if (changed) scrollView.post { scrollView.scrollTo(0, 0) }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun BrowserDemoPreview() {
    MyBrowserTheme {
        val document = remember { HtmlParser().parse(DemoPages.pages.getValue(DemoPages.HOME)) }
        BrowserPage(document, onLinkClick = {})
    }
}
