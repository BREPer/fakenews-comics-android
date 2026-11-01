package com.fakenews.comics.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 高亮色。legacy 值映射与 Web theme.ts 的 LEGACY_ACCENT 一致 ——
 * 选过「蓝色」「普鲁士蓝」的用户升级后看到的是空色，不会被打回默认色。
 * 色板（每套 accent 的 brand 色阶）在 ui/theme/Theme.kt。
 */
enum class Accent(val key: String) {
    AMBER("amber"),
    SORA("sora"),
    BEAN("bean"),
    ;

    companion object {
        fun from(name: String?): Accent = when (name) {
            "sora" -> SORA
            "bean" -> BEAN
            "orange" -> AMBER // legacy：改名
            "blue", "prussian" -> SORA // legacy：第一版的蓝 / 普鲁士蓝
            "gray" -> BEAN // legacy：第一版的灰
            else -> AMBER // 兜底与 Web 一致：琥珀橙
        }
    }
}

/** 明暗模式：与 Web `fakenews-theme` 三项一致。兜底浅色（Web 同款，见 theme.ts 的注释）。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun from(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

/** 外观偏好（两项，与 Web 的 10 项设备偏好中的 theme / accent 同源同默认值）。 */
data class Appearance(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val accent: Accent = Accent.AMBER,
) {
    /** system → 依据系统偏好解析为实际明暗 */
    fun isDark(systemInDark: Boolean): Boolean = when (theme) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * 外观偏好按设备存（SharedPreferences 唯一真源），与 Web 的 localStorage 同思路：
 * 手机/平板可以各用各的，不跟着账号同步到 NAS。
 */
@Singleton
class AppearancePrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Appearance> = _state.asStateFlow()

    fun setTheme(theme: ThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _state.value = _state.value.copy(theme = theme)
    }

    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.key).apply()
        _state.value = _state.value.copy(accent = accent)
    }

    private fun read(): Appearance = Appearance(
        theme = ThemeMode.from(prefs.getString(KEY_THEME, null)),
        accent = Accent.from(prefs.getString(KEY_ACCENT, null)),
    )

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
    }
}
