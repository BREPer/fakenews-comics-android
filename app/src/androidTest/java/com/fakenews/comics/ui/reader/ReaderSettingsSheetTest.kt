package com.fakenews.comics.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fakenews.comics.data.session.PageAnimation
import com.fakenews.comics.data.session.PageQuality
import com.fakenews.comics.data.session.ReadDir
import com.fakenews.comics.data.session.ReaderMode
import com.fakenews.comics.data.session.ReaderSettings
import com.fakenews.comics.ui.theme.FakenewsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 阅读设置面板的 instrumented UI 测试。
 *
 * 重点两件事：
 *   - 常亮开关（包 B 新增）必须真的把偏好回调出去并即时生效；
 *   - 预加载档位只能落在 [ReaderSettings.PRELOAD_STEPS] 上，
 *     中间值是靠 `snapPreload` 吸附的，面板本身不该给出第五种值。
 */
class ReaderSettingsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(initial: ReaderSettings = ReaderSettings()): MutableList<ReaderSettings> {
        val emitted = mutableListOf<ReaderSettings>()
        compose.setContent {
            FakenewsTheme(darkTheme = true) {
                ReaderSettingsSheet(settings = initial) { emitted += it }
            }
        }
        return emitted
    }

    @Test
    fun `常亮默认开且点一下回调关闭`() {
        val emitted = show()
        compose.onNodeWithTag("保持常亮").assertIsDisplayed()
        compose.onNodeWithTag("保持常亮").performClick()
        assertTrue("没有任何回调", emitted.isNotEmpty())
        assertEquals(false, emitted.last().keepScreenOn)
    }

    @Test
    fun `关掉常亮后再点回来是开`() {
        val emitted = show(ReaderSettings(keepScreenOn = false))
        compose.onNodeWithTag("保持常亮").performClick()
        assertEquals(true, emitted.last().keepScreenOn)
    }

    @Test
    fun `预加载只给固定档位`() {
        val emitted = show(ReaderSettings(preload = 3))
        for (label in listOf("关", "1", "3", "5", "8")) {
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("8").performClick()
        assertEquals(8, emitted.last().preload)
        assertTrue(
            "8 不在档位里",
            emitted.last().preload in ReaderSettings.PRELOAD_STEPS,
        )
    }

    @Test
    fun `四种模式与画质都在面板上`() {
        val emitted = show()
        compose.onNodeWithText("双页").performClick()
        assertEquals(ReaderMode.DOUBLE, emitted.last().mode)
        compose.onNodeWithText("原图").performClick()
        assertEquals(PageQuality.ORIGINAL, emitted.last().quality)
        compose.onNodeWithText("淡入").performClick()
        assertEquals(PageAnimation.FADE, emitted.last().animation)
        compose.onNodeWithText("右翻（日漫）").performClick()
        assertEquals(ReadDir.RTL, emitted.last().readDir)
    }
}
