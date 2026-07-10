package com.novelreader.ui.screens.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.storage.StorageManager
import com.novelreader.data.storage.StorageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isFirstLaunch: Boolean = true, // true = doit montrer l'onboarding
    val checking: Boolean = true        // true = en train de vérifier
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var safUri: Uri? = null

    init {
        viewModelScope.launch {
            val done = prefs.firstLaunchDone.collect { it }.let { false } // simplifié
            // Vérifier directement
            val first = with(org.jetbrains.kotlinx.coroutines.flow.first) {
                prefs.firstLaunchDone.collect { it }.let { false }
            }
            // Option plus simple
            _uiState.value = OnboardingUiState(isFirstLaunch = true, checking = false)
        }
    }

    // Version simplifiée pour le fix
    fun checkFirstLaunch(onComplete: () -> Unit) {
        viewModelScope.launch {
            kotlinx.coroutines.flow.first { true }
            val done = try {
                prefs.firstLaunchDone.collect { it }.let { false }
            } catch (e: Exception) { false }
            if (done) onComplete()
            else _uiState.value = OnboardingUiState(isFirstLaunch = true, checking = false)
        }
    }

    fun setSafUri(uri: Uri) { safUri = uri }

    fun finalizeSetup(selectedOption: Int) {
        viewModelScope.launch {
            when (selectedOption) {
                0 -> storageManager.setStorageType(StorageType.INTERNAL)
                1 -> {
                    safUri?.let { uri ->
                        storageManager.setSafTreeUri(uri.toString())
                        storageManager.setStorageType(StorageType.SAF)
                    } ?: storageManager.setStorageType(StorageType.INTERNAL)
                }
            }
            prefs.setFirstLaunchDone(true)
        }
    }
}
