package com.fakenews.comics.ui.common

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 格式化函数的单测。
 *
 * 锁 Locale：`String.format` 走默认语言环境，在中文 Windows 上是同一个小数点，
 * 但换成某些 locale（如 de_DE）会变成逗号，测试就会变成假的红。
 */
class FormatTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `字节数按量级切换单位`() {
        assertEquals("128 B", formatBytes(128))
        // %.0f 会把 1.0 印成 "1"：与 Web 端 toFixed(0) 同款
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("320.4 MB", formatBytes(335_921_000))
        assertEquals("1.20 GB", formatBytes(1_288_490_188))
    }

    @Test
    fun `零字节与负数是安全的`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun `运行时长取最大两个单位`() {
        assertEquals("42 秒", formatUptime(42))
        assertEquals("3 分", formatUptime(180))
        assertEquals("4 小时 5 分", formatUptime(14_700))
        assertEquals("2 天 3 小时", formatUptime(183_600))
    }
}
