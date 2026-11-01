package com.fakenews.comics.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 采集启动 + 书架 + 详情 + 阅读器的热路径。
 *
 * 读取的是本机联调实例（模拟器里的 10.0.2.2 指向宿主机 13800）。
 * 采集机上没有配置或数据被清空时，用例会自己在配置页填地址，所以不需要预先登录。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = PACKAGE) {
            pressHome()
            // API 36 模拟器上 startActivityAndWait() 的帧统计等待拿不到数据，
            // 而采集 profile 只关心「进程跑起来并交互过」，所以直接用 shell 启动。
            device.executeShellCommand("am start -W -n $PACKAGE/.MainActivity")

            // 首次安装会落在「连接服务器」页
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 2_000)
            if (field != null) {
                field.click()
                field.text = SERVER
                device.wait(Until.findObject(By.text("连接")), 2_000)?.click()
            }

            // 书架首屏 + 下滚一屏：覆盖列表、封面解码与分页加载
            device.wait(Until.hasObject(By.text("书架")), 10_000)
            device.swipe(540, 1700, 540, 700, 20)
            device.waitForIdle()
            device.swipe(540, 1700, 540, 700, 20)
            device.waitForIdle()

            // 详情页与阅读器：书架在 1080x2400 上是两列网格，点第一张卡片
            device.swipe(540, 700, 540, 1700, 20)
            device.waitForIdle()
            device.click(270, 700)
            device.waitForIdle()
            val start = device.wait(Until.findObject(By.text("开始阅读")), 3_000)
                ?: device.findObject(By.text("继续阅读"))
            if (start != null) {
                start.click()
                device.waitForIdle()
                // 翻两页，覆盖分页、解码与进度上报
                device.swipe(900, 1200, 200, 1200, 12)
                device.waitForIdle()
                device.swipe(900, 1200, 200, 1200, 12)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.fakenews.comics"
        const val SERVER = "http://10.0.2.2:13800"
    }
}
