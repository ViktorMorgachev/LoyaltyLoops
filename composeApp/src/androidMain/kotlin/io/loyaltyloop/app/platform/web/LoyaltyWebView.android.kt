package io.loyaltyloop.app.platform.web

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun LoyaltyWebView(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    reloadSignal: Int,
    onPageLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    onPageLoading(true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    onPageLoading(false)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    onError(error?.description?.toString() ?: "Unknown error")
                }
            }
            webChromeClient = WebChromeClient()
        }
    }

    LaunchedEffect(url, headers, reloadSignal) {
        onPageLoading(true)
        webView.loadUrl(url, headers)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView }
    )
}


