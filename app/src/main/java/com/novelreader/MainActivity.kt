package com.novelreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.ui.navigation.NovelReaderNavigation
import com.novelreader.ui.theme.AppTheme
import com.novelreader.ui.theme.NovelReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    /** Slug reçu via notification (« nouveaux chapitres ») → navigation vers le détail. */
    private val deepLinkSlug = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val themeValue by preferencesManager.themeType.collectAsState(initial = 1) // 1 = Dark
            val appTheme = when (themeValue) {
                0 -> AppTheme.SYSTEM
                1 -> AppTheme.DARK
                2 -> AppTheme.LIGHT
                3 -> AppTheme.AMOLED
                else -> AppTheme.DARK
            }
            val slug by deepLinkSlug.collectAsState()

            // Android 13+ : demande la permission de notification au premier lancement
            // (le système n'affiche la boîte de dialogue qu'une seule fois)
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            NovelReaderTheme(themeType = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovelReaderNavigation(
                        deepLinkSlug = slug,
                        onDeepLinkConsumed = { deepLinkSlug.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_NOVEL_SLUG)
            ?.takeIf { it.isNotBlank() }
            ?.let { deepLinkSlug.value = it }
    }

    companion object {
        const val EXTRA_NOVEL_SLUG = "novel_slug"
    }
}
