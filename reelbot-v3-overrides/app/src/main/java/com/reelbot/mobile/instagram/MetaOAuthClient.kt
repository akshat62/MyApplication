package com.reelbot.mobile.instagram

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.reelbot.mobile.BuildConfig
import com.reelbot.mobile.ai.PipelineException
import com.reelbot.mobile.data.model.FailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Instagram publishing goes through Facebook Login for Business (Meta deprecated the old
 * Instagram Basic Display OAuth for this use case) — the person signs in with Facebook,
 * grants access to the Facebook Page connected to their Instagram professional account,
 * and ReelBot resolves the linked Instagram Business/Creator account from there. There is
 * no "Instagram-only" OAuth dialog in the current Graph API; the two "Login with
 * Instagram" / "Continue with Facebook" buttons in Settings both route through this same
 * flow, since that's how Meta's API actually works today — see SETUP.md for exactly what
 * to configure in the Meta Developer dashboard for this to work.
 */
class MetaOAuthClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val redirectUri = "${BuildConfig.IG_OAUTH_REDIRECT_SCHEME}://oauth/callback"

    /** Opens the Meta login dialog in a Custom Tab. The result comes back to
     *  MainActivity's declared intent-filter for [redirectUri]; call [exchangeCode] with
     *  the `code` query param once that fires. */
    fun launchLogin() {
        check(BuildConfig.META_APP_ID.matches(Regex("[0-9]+"))) { "Instagram setup required: configure the Meta Developer App ID and approved redirect URI." }
        error("Instagram publishing setup is incomplete: a supported public-client OAuth configuration is required. App secrets must not be embedded in an APK.")

        val authUrl = Uri.parse("https://www.facebook.com/${GRAPH_VERSION}/dialog/oauth")
            .buildUpon()
            .appendQueryParameter("client_id", BuildConfig.META_APP_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter(
                "scope",
                listOf(
                    "instagram_basic",
                    "instagram_content_publish",
                    "pages_show_list",
                    "pages_read_engagement",
                    "business_management"
                ).joinToString(",")
            )
            .build()

        CustomTabsIntent.Builder().build().launchUrl(context, authUrl)
    }

    /**
     * Exchanges the OAuth `code` for a long-lived token, then resolves the Facebook Page
     * and its connected Instagram professional account. Throws
     * PipelineException(META_OAUTH_FAILED / MISSING_IG_PERMISSIONS) with the real Graph
     * API error message on any failure — never returns a fabricated session.
     */
    suspend fun exchangeCode(code: String): MetaSession = withContext(Dispatchers.IO) {
        val shortLivedToken = exchangeCodeForToken(code)
        val longLivedToken = exchangeForLongLivedToken(shortLivedToken)
        val (pageId, pageToken) = resolveFirstPageWithToken(longLivedToken)
        val (igUserId, igUsername) = resolveInstagramAccount(pageId, pageToken)

        MetaSession(
            accessToken = pageToken,
            igUserId = igUserId,
            igUsername = igUsername,
            facebookPageId = pageId,
            // Long-lived Page tokens don't expire on a fixed schedule while the Page
            // exists and permissions aren't revoked; we still record a conservative
            // 60-day check-in so Settings can prompt a reconnect proactively.
            expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(60)
        )
    }

    private fun exchangeCodeForToken(code: String): String {
        val url = Uri.parse("https://graph.facebook.com/$GRAPH_VERSION/oauth/access_token").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.META_APP_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code", code)
            .appendQueryParameter("client_secret", metaAppSecretOrThrow())
            .build()
        val json = getJson(url.toString())
        return json.optString("access_token").ifBlank {
            throw PipelineException(FailureReason.META_OAUTH_FAILED, graphErrorMessage(json))
        }
    }

    private fun exchangeForLongLivedToken(shortLivedToken: String): String {
        val url = Uri.parse("https://graph.facebook.com/$GRAPH_VERSION/oauth/access_token").buildUpon()
            .appendQueryParameter("grant_type", "fb_exchange_token")
            .appendQueryParameter("client_id", BuildConfig.META_APP_ID)
            .appendQueryParameter("client_secret", metaAppSecretOrThrow())
            .appendQueryParameter("fb_exchange_token", shortLivedToken)
            .build()
        val json = getJson(url.toString())
        return json.optString("access_token").ifBlank {
            throw PipelineException(FailureReason.META_OAUTH_FAILED, graphErrorMessage(json))
        }
    }

    /** Returns (pageId, pageAccessToken) for the first Page the user manages. Real apps
     *  with multiple Pages should let the user choose — a picker is a natural Settings
     *  addition once there's more than one to choose from. */
    private fun resolveFirstPageWithToken(userToken: String): Pair<String, String> {
        val url = "https://graph.facebook.com/$GRAPH_VERSION/me/accounts?access_token=$userToken"
        val json = getJson(url)
        val data = json.optJSONArray("data")
        if (data == null || data.length() == 0) {
            throw PipelineException(
                FailureReason.MISSING_IG_PERMISSIONS,
                "This Facebook account doesn't manage any Pages. Instagram publishing requires a Facebook Page connected to a professional Instagram account."
            )
        }
        val page = data.getJSONObject(0)
        return page.getString("id") to page.getString("access_token")
    }

    private fun resolveInstagramAccount(pageId: String, pageToken: String): Pair<String, String> {
        val url = "https://graph.facebook.com/$GRAPH_VERSION/$pageId?fields=instagram_business_account&access_token=$pageToken"
        val json = getJson(url)
        val igAccount = json.optJSONObject("instagram_business_account")
            ?: throw PipelineException(
                FailureReason.MISSING_IG_PERMISSIONS,
                "This Facebook Page has no connected Instagram professional account. Link one in the Instagram app (Settings > Account > Switch to professional account, then connect it to this Facebook Page) and try again."
            )
        val igUserId = igAccount.getString("id")

        val usernameJson = getJson("https://graph.facebook.com/$GRAPH_VERSION/$igUserId?fields=username&access_token=$pageToken")
        val username = usernameJson.optString("username", "")
        return igUserId to username
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val json = try {
                JSONObject(body)
            } catch (e: Exception) {
                throw PipelineException(FailureReason.META_OAUTH_FAILED, "Non-JSON response from Meta: $body")
            }
            if (!response.isSuccessful) {
                throw PipelineException(FailureReason.META_OAUTH_FAILED, graphErrorMessage(json))
            }
            return json
        }
    }

    private fun graphErrorMessage(json: JSONObject): String {
        val error = json.optJSONObject("error") ?: return json.toString()
        return error.optString("message", json.toString())
    }

    private fun metaAppSecretOrThrow(): String {
        // The App Secret must NEVER ship inside the APK in a real release — it belongs
        // on a server-side token exchange in production. It's read here from a local,
        // non-committed source only to keep this scaffold self-contained; see SETUP.md
        // "Production hardening" for the required server-side exchange before shipping.
        return BuildConfig.META_APP_SECRET.ifBlank {
            throw PipelineException(
                FailureReason.META_OAUTH_FAILED,
                "META_APP_SECRET isn't configured. See SETUP.md."
            )
        }
    }

    companion object {
        private const val GRAPH_VERSION = "v21.0"
    }
}
