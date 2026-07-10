package com.novelreader.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.storage.StorageManager
import com.novelreader.data.storage.StorageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private var safUri: Uri? = null

    fun setSafUri(uri: Uri) {
        safUri = uri
    }

    fun finalizeSetup(selectedOption: Int) {
        viewModelScope.launch {
            when (selectedOption) {
                0 -> {
                    storageManager.setStorageType(StorageType.INTERNAL)
                }
                1 -> {
                    safUri?.let { uri ->
                        storageManager.setSafTreeUri(uri.toString())
                        storageManager.setStorageType(StorageType.SAF)
                    } ?: run {
                        storageManager.setStorageType(StorageType.INTERNAL)
                    }
                }
            }
            prefs.setFirstLaunchDone(true)
        }
    }
}
