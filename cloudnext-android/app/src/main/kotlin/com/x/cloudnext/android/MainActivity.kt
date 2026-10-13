package com.x.cloudnext.android

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.x.cloudnext.android.datastore.PreferencesRepository
import com.x.cloudnext.android.datastore.ThemeMode
import com.x.cloudnext.android.designsystem.CnTheme
import com.x.cloudnext.android.navigation.CloudNextNavHost
import com.x.cloudnext.android.navigation.LocalWindowSizeClass
import com.x.cloudnext.android.viewmodel.RootViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: PreferencesRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒了也不影响 ForegroundService 通知，只是后续业务通知会被静默 */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.tag(TAG).d("Activity#onCreate saved=%s taskId=%d", savedInstanceState != null, taskId)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            // 用户主题偏好（M6）：System / Light / Dark，与系统设置组合派生 darkTheme
            val themeMode by preferences.themeModeFlow.collectAsState(initial = ThemeMode.System)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> systemDark
            }

            // WindowSizeClass（M6）：Compact / Medium / Expanded，根 NavHost 通过 LocalWindowSizeClass 消费
            val windowSizeClass = calculateWindowSizeClass(this)

            CnTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val viewModel: RootViewModel = hiltViewModel()
                        CloudNextNavHost(rootViewModel = viewModel)

                        LaunchedEffect(viewModel) {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.sessionExpired.collect { /* token 流自动跳转 */ }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        Timber.tag(TAG).d("Activity#onStart")
        super.onStart()
    }

    override fun onResume() {
        Timber.tag(TAG).d("Activity#onResume")
        super.onResume()
    }

    override fun onPause() {
        Timber.tag(TAG).d("Activity#onPause")
        super.onPause()
    }

    override fun onStop() {
        Timber.tag(TAG).d("Activity#onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("Activity#onDestroy isFinishing=%s isChangingConfigurations=%s", isFinishing, isChangingConfigurations)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // configChanges 拦截后 Compose 自动响应 LocalConfiguration 变化（Activity 不重建）
        val uiNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Timber.tag(TAG).d(
            "Activity#onConfigurationChanged uiNight=%s orientation=%d density=%d fontScale=%.2f",
            uiNight, newConfig.orientation, newConfig.densityDpi, newConfig.fontScale,
        )
        super.onConfigurationChanged(newConfig)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val TAG = "CnLifecycle"
    }
}
