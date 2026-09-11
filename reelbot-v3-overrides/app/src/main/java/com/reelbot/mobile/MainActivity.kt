package com.reelbot.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelbot.mobile.instagram.OAuthCallbackBus
import com.reelbot.mobile.navigation.ReelBotNavHost
import com.reelbot.mobile.ui.ReelBotViewModelFactory
import com.reelbot.mobile.ui.theme.ReelBotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        setContent {
            ReelBotTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SafeStartRoot()
                }
            }
        }

        handleIntentIfOAuthRedirect(intent)
    }

    @Composable
    private fun SafeStartRoot() {
        var factory by remember { mutableStateOf<ReelBotViewModelFactory?>(null) }
        var startupError by remember { mutableStateOf<String?>(null) }
        var previousCrash by remember { mutableStateOf(readLastCrash()) }
        var retryNonce by remember { mutableIntStateOf(0) }

        LaunchedEffect(retryNonce, previousCrash) {
            if (previousCrash != null || factory != null) return@LaunchedEffect

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = application as? ReelBotApp
                        ?: error("ReelBotApp was not created by Android")
                    // Repository/Room initialization is deliberately deferred until after
                    // the first UI frame. A device-specific database failure can no longer
                    // terminate the process before the user sees a screen.
                    ReelBotViewModelFactory(app.repository, applicationContext)
                }
            }

            result.onSuccess {
                startupError = null
                factory = it
            }.onFailure {
                startupError = formatThrowable(it)
            }
        }

        when {
            previousCrash != null -> StartupDiagnosticScreen(
                title = "ReelBot opened in Safe Mode",
                message = previousCrash ?: "Previous startup crash detected.",
                onRetry = {
                    clearLastCrash()
                    previousCrash = null
                    startupError = null
                    factory = null
                    retryNonce++
                }
            )

            startupError != null -> StartupDiagnosticScreen(
                title = "ReelBot could not initialize",
                message = startupError ?: "Unknown startup error",
                onRetry = {
                    startupError = null
                    factory = null
                    retryNonce++
                }
            )

            factory == null -> StartupLoadingScreen()
            else -> ReelBotNavHost(factory!!)
        }
    }

    @Composable
    private fun StartupLoadingScreen() {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ReelBot", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Starting safely…",
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun StartupDiagnosticScreen(
        title: String,
        message: String,
        onRetry: () -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "The app stayed open instead of crashing. Diagnostic details:",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                message.take(5000),
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onRetry) {
                Text("Try normal startup")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentIfOAuthRedirect(intent)
    }

    private fun handleIntentIfOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == BuildConfig.IG_OAUTH_REDIRECT_SCHEME && data.host == "oauth") {
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            if (code != null) {
                OAuthCallbackBus.emit(com.reelbot.mobile.instagram.OAuthCallbackResult.Success(code))
            } else if (error != null) {
                val description = data.getQueryParameter("error_description") ?: error
                OAuthCallbackBus.emit(com.reelbot.mobile.instagram.OAuthCallbackResult.Error(description))
            }
        }
    }

    private fun crashFile(): File = File(filesDir, ReelBotApp.LAST_CRASH_FILE)

    private fun readLastCrash(): String? = runCatching {
        crashFile().takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun clearLastCrash() {
        runCatching { crashFile().delete() }
    }

    private fun formatThrowable(t: Throwable): String = buildString {
        append(t::class.java.name)
        t.message?.let { append(": ").append(it) }
        t.cause?.let { cause ->
            append("\nCaused by: ").append(cause::class.java.name)
            cause.message?.let { append(": ").append(it) }
        }
    }
}
