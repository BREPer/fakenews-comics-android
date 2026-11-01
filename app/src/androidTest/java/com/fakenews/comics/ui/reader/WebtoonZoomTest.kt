package com.fakenews.comics.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 卷轴模式的双指缩放。
 *
 * 为什么用 instrumented 测试而不是在模拟器上点：`adb shell input` 只有单指，
 * 而 sendevent 写 /dev/input/eventX 在这台模拟器上被直接丢弃（getevent 一个事件都收不到）。
 * Compose 的 `performTouchInput { pinch(...) }` 直接往组合里灌手势，绕开系统输入栈，
 * 这是唯一能真正跑通「两指」的路径。
 *
 * 断言的是缩放状态本身（[ZoomState] 只有 draw 阶段读它，没有可观察的 UI 副作用），
 * 所以测试要自己持有并传进去。
 */
class WebtoonZoomTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setPager(zoom: ZoomState, pinchZoom: Boolean) {
        compose.setContent {
            WebtoonPager(
                pageIndex = 0,
                pageCount = 4,
                urlFor = { null },
                onSettle = {},
                onTapCenter = {},
                pinchZoom = pinchZoom,
                zoomState = zoom,
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun `开启双指缩放后捏合会放大`() {
        val zoom = ZoomState()
        setPager(zoom, pinchZoom = true)

        compose.onRoot().performTouchInput {
            pinch(
                start0 = Offset(centerX - 60f, centerY),
                end0 = Offset(centerX - 320f, centerY),
                start1 = Offset(centerX + 60f, centerY),
                end1 = Offset(centerX + 320f, centerY),
            )
        }
        compose.waitForIdle()

        // 两指间距 120 → 640，约 5.3 倍，会被 MAX_ZOOM(4) 夹住
        assertTrue("捏合后应放大，实际 ${zoom.scale.floatValue}", zoom.scale.floatValue > 2f)
    }

    @Test
    fun `关闭双指缩放后捏合不改变缩放`() {
        val zoom = ZoomState()
        setPager(zoom, pinchZoom = false)

        compose.onRoot().performTouchInput {
            pinch(
                start0 = Offset(centerX - 60f, centerY),
                end0 = Offset(centerX - 320f, centerY),
                start1 = Offset(centerX + 60f, centerY),
                end1 = Offset(centerX + 320f, centerY),
            )
        }
        compose.waitForIdle()

        assertEquals(1f, zoom.scale.floatValue, 0.001f)
    }

    /**
     * 放大后单指拖动是**平移画面**，不是滚列表。
     *
     * 这一条是这次改动的关键回归点：缩放层挂在 Initial 阶段，LazyColumn 挂在 Main 阶段，
     * 前者先消费，后者收到的 change 已被标记 consumed 就不会滚。只要这条不成立，
     * 用户放大之后会发现「拖不动画面，反而把列表滚走了」。
     */
    @Test
    fun `放大后单指拖动平移画面`() {
        val zoom = ZoomState()
        setPager(zoom, pinchZoom = true)

        compose.onRoot().performTouchInput {
            pinch(
                start0 = Offset(centerX - 60f, centerY),
                end0 = Offset(centerX - 320f, centerY),
                start1 = Offset(centerX + 60f, centerY),
                end1 = Offset(centerX + 320f, centerY),
            )
        }
        compose.waitForIdle()
        val beforeOffset = zoom.offsetY.floatValue

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertTrue(
            "拖动后纵向位移应变化，实际 $beforeOffset → ${zoom.offsetY.floatValue}",
            zoom.offsetY.floatValue != beforeOffset,
        )
        assertTrue("放大状态应保持", zoom.scale.floatValue > 2f)
    }
}
