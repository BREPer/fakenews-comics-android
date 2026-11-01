package com.fakenews.comics

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fakenews.comics.data.session.AppearancePrefs
import com.fakenews.comics.ui.AppNav
import com.fakenews.comics.ui.theme.FakenewsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appearancePrefs: AppearancePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏/导航栏（小白条）都全透明，内容铺到系统栏后面。
        // 显式给 auto(TRANSPARENT, TRANSPARENT)：默认的导航栏 scrim 会在浅色页面上
        // 压出一条半透明白带，和底栏的毛玻璃叠成两层。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // 外观（主题/高亮色）按设备存：这里订阅唯一真源，全 App 跟着变
            val appearance by appearancePrefs.state.collectAsStateWithLifecycle()
            val dark = appearance.isDark(isSystemInDarkTheme())
            FakenewsTheme(darkTheme = dark, accent = appearance.accent) {
                // 系统栏区域也要由 App 主题铺底：windowBackground 跟的是系统深色模式，
                // 而本 App 可以手动切主题 —— 不铺这层，状态栏/小白条会露出另一套底色。
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // 系统栏图标随明暗翻转（Web 同步 meta theme-color 的同款动作）。
                    // 只有 dark 变化才需要重设 —— LaunchedEffect(dark) 而不是 SideEffect，
                    // 后者每次重组都空跑两笔赋值
                    LaunchedEffect(dark) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !dark
                        controller.isAppearanceLightNavigationBars = !dark
                    }
                    AppNav()
                }
            }
        }
    }
}
