package com.novelreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.ui.navigation.NovelReaderNavigation
import com.novelreader.ui.theme.AppTheme
import com.novelreader.ui.theme.NovelReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeValue by preferencesManager.themeType.collectAsState(initial = 1) // 1 = Dark
            val appTheme = when (themeValue) {
                0 -> AppTheme.SYSTEM
                1 -> AppTheme.DARK
                2 -> AppTheme.LIGHT
                3 -> AppTheme.AMOLED
                else -> AppTheme.DARK
            }

            NovelReaderTheme(themeType = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovelReaderNavigation()
                }
            }
        }
    }
}
