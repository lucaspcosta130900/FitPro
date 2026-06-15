package com.fitpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitpro.data.preferences.UserPreferencesRepository
import com.fitpro.ui.navigation.AppNavigation
import com.fitpro.ui.theme.FitProTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ─── Theme ViewModel ──────────────────────────────────────────────────────────

@HiltViewModel
class MainViewModel @Inject constructor(
    prefsRepo: UserPreferencesRepository
) : ViewModel() {
    val darkMode: StateFlow<Boolean?> = prefsRepo.preferences
        .map { it.darkMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

// ─── Activity ─────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = hiltViewModel()
            val darkModePreference by vm.darkMode.collectAsStateWithLifecycle()
            // Fall back to system setting while preference loads
            val useDark = darkModePreference ?: isSystemInDarkTheme()

            FitProTheme(darkTheme = useDark) {
                Surface {
                    // NavigationSuiteScaffold (inside AppNavigation) reads the
                    // ambient WindowSizeClass automatically — no manual wiring needed.
                    // ZFold 7 folded  → Compact  → BottomNavigationBar
                    // ZFold 7 unfolded → Expanded → NavigationRail
                    AppNavigation()
                }
            }
        }
    }
}
