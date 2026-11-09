package com.caseymisc.fitdash

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.caseymisc.fitdash.data.garmin.GarminAuth.LoginFlow
import com.caseymisc.fitdash.ui.theme.FitDashTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Signs in to Garmin on Garmin's own SSO page inside a WebView (so MFA and captchas work and
 * the password never touches FitDash), then catches the redirect carrying the one-time
 * service ticket and exchanges it for API tokens.
 */
class GarminLoginActivity : ComponentActivity() {

    private sealed interface UiState {
        data class SigningIn(val flow: LoginFlow) : UiState
        data object Exchanging : UiState
        data class Failed(val message: String) : UiState
    }

    private var state by mutableStateOf<UiState>(UiState.SigningIn(LoginFlow.MOBILE))
    private val mainHandler = Handler(Looper.getMainLooper())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitDashTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Sign in to Garmin") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (val s = state) {
                            is UiState.SigningIn -> SignInWebView(s.flow)
                            UiState.Exchanging -> Column(
                                Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CircularProgressIndicator()
                                Text("Connecting your Garmin account…")
                            }
                            is UiState.Failed -> Column(
                                Modifier.align(Alignment.Center).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("Garmin sign-in failed", style = MaterialTheme.typography.titleLarge)
                                Text(s.message)
                                Button(onClick = { state = UiState.SigningIn(LoginFlow.MOBILE) }) {
                                    Text("Try again")
                                }
                                OutlinedButton(onClick = { state = UiState.SigningIn(LoginFlow.PORTAL) }) {
                                    Text("Try the website sign-in instead")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun SignInWebView(flow: LoginFlow) {
        val handled = remember(flow) { AtomicBoolean(false) }

        /** True if [url] is the redirect carrying the ticket (and it has been handled). */
        fun interceptTicket(url: String): Boolean {
            if (!url.startsWith(flow.serviceUrl)) return false
            val ticket = Uri.parse(url).getQueryParameter("ticket")
            if (ticket.isNullOrBlank() || !ticket.startsWith("ST-")) return false
            if (handled.compareAndSet(false, true)) mainHandler.post { onTicket(ticket, flow) }
            return true
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            interceptTicket(request.url.toString())

                        // Catches the redirect even when it isn't a top-level navigation, and
                        // stops the WebView from spending the single-use ticket itself.
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? =
                            if (interceptTicket(request.url.toString())) {
                                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            } else {
                                null
                            }
                    }
                    loadUrl(flow.signInUrl)
                }
            },
        )
    }

    private fun onTicket(ticket: String, flow: LoginFlow) {
        state = UiState.Exchanging
        val client = (application as FitDashApp).container.garminClient
        lifecycleScope.launch {
            try {
                client.signInWithTicket(ticket, flow.serviceUrl)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                state = UiState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
