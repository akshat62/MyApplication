package com.reelbot.mobile.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelbot.mobile.instagram.MetaOAuthClient
import com.reelbot.mobile.instagram.OAuthCallbackBus
import com.reelbot.mobile.instagram.OAuthCallbackResult
import com.reelbot.mobile.instagram.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstagramConnectionUiState(
    val connected: Boolean = false,
    val username: String? = null,
    val connecting: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val modelManager = (appContext as com.reelbot.mobile.ReelBotApp).modelManager
    val modelState = modelManager.state
    val modelProgress = modelManager.progress
    private val _modelError = MutableStateFlow<String?>(null)
    val modelError = _modelError.asStateFlow()
    private var downloadJob: kotlinx.coroutines.Job? = null
    val diagnostics = MutableStateFlow("")

    fun downloadModel() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _modelError.value = null
            try { modelManager.download(com.reelbot.mobile.data.model.WhisperModelSpec.BASE) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _modelError.value = e.message }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun readDiagnostics() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        diagnostics.value = runCatching { java.io.File(appContext.filesDir, com.reelbot.mobile.ReelBotApp.LAST_CRASH_FILE).readText() }.getOrDefault("No retained crash report.")
    }

    private val tokenVault = TokenVault(context)
    private val oauthClient = MetaOAuthClient(context)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<InstagramConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try { modelManager.refreshState(com.reelbot.mobile.data.model.WhisperModelSpec.BASE) }
            catch (e: Exception) { _modelError.value = e.message }
        }
        viewModelScope.launch {
            OAuthCallbackBus.results.collect { result ->
                when (result) {
                    is OAuthCallbackResult.Success -> handleCode(result.code)
                    is OAuthCallbackResult.Error -> _uiState.value = _uiState.value.copy(
                        connecting = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun startLogin() {
        _uiState.value = _uiState.value.copy(connecting = true, error = null)
        try {
            oauthClient.launchLogin()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(connecting = false, error = e.message ?: "Cannot open sign-in.")
        }
    }

    fun disconnect() {
        tokenVault.clear()
        _uiState.value = InstagramConnectionUiState(connected = false)
    }

    private suspend fun handleCode(code: String) {
        try {
            val session = oauthClient.exchangeCode(code)
            tokenVault.saveSession(session)
            _uiState.value = InstagramConnectionUiState(connected = true, username = session.igUsername)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                connecting = false,
                error = e.message ?: "Instagram sign-in failed."
            )
        }
    }

    private fun loadState(): InstagramConnectionUiState {
        val session = tokenVault.currentSession()
        return if (session != null) {
            InstagramConnectionUiState(connected = true, username = session.igUsername)
        } else {
            InstagramConnectionUiState()
        }
    }
}
