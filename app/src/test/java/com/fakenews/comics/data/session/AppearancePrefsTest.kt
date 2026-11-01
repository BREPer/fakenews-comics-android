package com.fakenews.comics.data.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 外观偏好的归一化（纯函数部分）。
 *
 * legacy 值映射必须与 Web theme.ts 的 LEGACY_ACCENT 一致：选过「蓝色」「普鲁士蓝」的
 * 用户升级后看到的应该是空色，而不是被打回默认琥珀橙。
 */
class AppearancePrefsTest {
    @Test
    fun `accent 归一化接受三个现值`() {
        assertEquals(Accent.AMBER, Accent.from("amber"))
        assertEquals(Accent.SORA, Accent.from("sora"))
        assertEquals(Accent.BEAN, Accent.from("bean"))
    }

    @Test
    fun `accent 归一化迁移 legacy 值`() {
        assertEquals(Accent.AMBER, Accent.from("orange"))
        assertEquals(Accent.SORA, Accent.from("blue"))
        assertEquals(Accent.SORA, Accent.from("prussian"))
        assertEquals(Accent.BEAN, Accent.from("gray"))
    }

    @Test
    fun `accent 兜底是琥珀橙`() {
        assertEquals(Accent.AMBER, Accent.from(null))
        assertEquals(Accent.AMBER, Accent.from(""))
        assertEquals(Accent.AMBER, Accent.from("不存在的值"))
    }

    @Test
    fun `theme 归一化与兜底`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("DARK"))
        // 兜底浅色：与 Web theme.ts 的 getStoredTheme 一致
        assertEquals(ThemeMode.LIGHT, ThemeMode.from(null))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("dark")) // 大小写敏感：不匹配即兜底
    }

    @Test
    fun `system 模式按系统偏好解析`() {
        val system = Appearance(theme = ThemeMode.SYSTEM)
        assertEquals(false, system.isDark(systemInDark = false))
        assertEquals(true, system.isDark(systemInDark = true))
        assertEquals(false, Appearance(theme = ThemeMode.LIGHT).isDark(systemInDark = true))
        assertEquals(true, Appearance(theme = ThemeMode.DARK).isDark(systemInDark = false))
    }
}
