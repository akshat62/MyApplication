package com.reelbot.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.reelbot.mobile.instagram.OAuthCallbackBus
import com.reelbot.mobile.navigation.ReelBotNavHost
import com.reelbot.mobile.ui.ReelBotViewModelFactory
import com.reelbot.mobile.ui.theme.ReelBotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ReelBotApp
        val factory = ReelBotViewModelFactory(app.repository, applicationContext)

        setContent {
            // Keep startup deterministic across OEM Android builds. Dynamic Material
            // colors can be enabled later from Settings after the first stable launch.
            ReelBotTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReelBotNavHost(factory)
                }
            }
        }

        handleIntentIfOAuthRedirect(intent)
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
}
