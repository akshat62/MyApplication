package com.reelbot.mobile.instagram

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class OAuthCallbackResult {
    data class Success(val code: String) : OAuthCallbackResult()
    data class Error(val message: String) : OAuthCallbackResult()
}

/**
 * MainActivity.onNewIntent (and onCreate, for a cold start via the redirect) captures the
 * OAuth redirect intent-filter's `code` or `error` query param and emits it here.
 * SettingsViewModel collects it and drives the real token exchange or shows the real
 * error Meta returned — nothing about the account ID or token is ever typed by the user.
 */
object OAuthCallbackBus {
    private val _results = MutableSharedFlow<OAuthCallbackResult>(extraBufferCapacity = 1)
    val results: SharedFlow<OAuthCallbackResult> = _results.asSharedFlow()

    fun emit(result: OAuthCallbackResult) {
        _results.tryEmit(result)
    }
}
