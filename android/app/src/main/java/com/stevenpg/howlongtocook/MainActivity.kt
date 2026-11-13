package com.stevenpg.howlongtocook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.data.model.TemperatureUnit
import com.stevenpg.howlongtocook.data.settings.SettingsRepository
import com.stevenpg.howlongtocook.data.settings.UnitPreference
import com.stevenpg.howlongtocook.ui.components.LocalTemperatureUnit
import com.stevenpg.howlongtocook.ui.navigation.AppNavHost
import com.stevenpg.howlongtocook.ui.theme.HowLongToCookTheme
import com.stevenpg.howlongtocook.util.defaultUnitFor
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private val ready = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hold the splash until settings load so the first frame uses the right theme.
        splash.setKeepOnScreenCondition { !ready.get() }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            ready.set(true)
            val unit = when (s.unit) {
                UnitPreference.FAHRENHEIT -> TemperatureUnit.F
                UnitPreference.CELSIUS -> TemperatureUnit.C
                UnitPreference.AUTO -> defaultUnitFor(LocalConfiguration.current.locales[0])
            }
            HowLongToCookTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(LocalTemperatureUnit provides unit) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AppNavHost()
                    }
                }
            }
        }
    }
}
