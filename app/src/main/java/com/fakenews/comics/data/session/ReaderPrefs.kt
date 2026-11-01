package com.fakenews.comics.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 四种翻页模式，与 Web 版 `readerMode` 一一对应。 */
enum class ReaderMode {
    /** 卷轴：竖向连续滚动，整幅按屏宽铺开 */
    WEBTOON,

    /** 单页：一屏一图 */
    SINGLE,

    /** 双页：两图并排（页码域 2 图 = 1 页） */
    DOUBLE,

    /** 拆分双页：宽幅跨页图对半看（页码域 1 图 = 2 页） */
    SPLIT,
    ;

    companion object {
        fun from(name: String?): ReaderMode = entries.firstOrNull { it.name == name } ?: SINGLE
    }
}

enum class ReadDir {
    LTR,
    RTL,
    ;

    companion object {
        fun from(name: String?): ReadDir = entries.firstOrNull { it.name == name } ?: LTR
    }
}

/** 服务端只有两档契约：standard（按 w 缩放）/ original（原字节）。「流畅」只是更小的目标宽度。 */
enum class PageQuality {
    /** 目标宽度 720 */
    SMOOTH,
    /** 目标宽度 1080 */
    STANDARD,
    ORIGINAL,
    ;

    companion object {
        fun from(name: String?): PageQuality = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class PageAnimation {
    /** 胶片式平移：拖动 1:1 跟手，松手按距离/速度吸附 */
    PAN,
    SLIDE,
    FADE,
    NONE,
    ;

    /*
     * 这里曾经有过第五种「3D 翻页」（先绕书脊转 `FLIP3D`，后改成折线卷页 `CURL`），
     * 2026-10-29 全部撤掉了 —— 用户判断观感不行，不再保留。
     *
     * 撤掉的遗留值不特殊处理：[from] 落到默认的 `PAN`。翻页动画是可随时改的显示偏好，
     * 升级后从「卷页」静默退回「平移」不会丢任何数据，没必要为它留兼容分支和死代码。
     */
    companion object {
        fun from(name: String?): PageAnimation = entries.firstOrNull { it.name == name } ?: PAN
    }
}

enum class FitMode {
    WIDTH,
    HEIGHT,
    CONTAIN,
    /** 原始尺寸，容器可滚动；只在单页模式生效 */
    ORIGINAL,
    ;

    companion object {
        fun from(name: String?): FitMode = entries.firstOrNull { it.name == name } ?: CONTAIN
    }
}

/** 一份完整的阅读偏好。默认值与 Web 版 `DEVICE_PREFS_DEFAULTS` 对齐。 */
data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.SINGLE,
    val readDir: ReadDir = ReadDir.LTR,
    val quality: PageQuality = PageQuality.STANDARD,
    val animation: PageAnimation = PageAnimation.PAN,
    val fit: FitMode = FitMode.CONTAIN,
    /** 前后各预加载多少页，档位见 [PRELOAD_STEPS] */
    val preload: Int = 3,
    /** 双指缩放：放大后单指平移，单击只显隐工具栏（卷轴模式固定关闭） */
    val pinchZoom: Boolean = false,
    /** 屏幕保持常亮：默认开——多数人希望看书时不灭屏，但睡前在被窝里看的人恰恰要它照常熄 */
    val keepScreenOn: Boolean = true,
) {
    val rtl: Boolean get() = readDir == ReadDir.RTL

    companion object {
        val PRELOAD_STEPS = listOf(0, 1, 3, 5, 8)

        /** 存量中间值吸附到最近档；距离相等取更小档（少预载最多慢一点，多预载会挤内存）。 */
        fun snapPreload(v: Int): Int {
            val clamped = v.coerceIn(0, 8)
            return PRELOAD_STEPS.minBy { abs(it - clamped) }
        }
    }
}

/** 阅读偏好放本地：这是设备手感问题（平板默认双页、手机默认单页），不该同步到 NAS。 */
@Singleton
class ReaderPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

    /**
     * 唯一真源。阅读器与设置页都订阅它 —— 之前 `settings` 只是个普通 var，设置页用
     * `MutableStateFlow(readerPrefs.settings)` 做了一次性快照，于是在阅读器里把翻页模式
     * 改成「双页」、回到设置页仍高亮在「单页」（实测：reader.xml 已经是 DOUBLE）。
     */
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ReaderSettings> = _state.asStateFlow()

    var settings: ReaderSettings
        get() = _state.value
        set(value) {
            save(value)
            _state.value = value
        }

    private fun load(): ReaderSettings = ReaderSettings(
        mode = ReaderMode.from(prefs.getString(KEY_MODE, null)),
        readDir = ReadDir.from(prefs.getString(KEY_READ_DIR, null)),
        quality = PageQuality.from(prefs.getString(KEY_QUALITY, null)),
        animation = PageAnimation.from(prefs.getString(KEY_ANIMATION, null)),
        fit = FitMode.from(prefs.getString(KEY_FIT, null)),
        preload = ReaderSettings.snapPreload(prefs.getInt(KEY_PRELOAD, DEFAULT_PRELOAD)),
        pinchZoom = prefs.getBoolean(KEY_PINCH_ZOOM, false),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
    )

    private fun save(value: ReaderSettings) {
        prefs.edit()
            .putString(KEY_MODE, value.mode.name)
            .putString(KEY_READ_DIR, value.readDir.name)
            .putString(KEY_QUALITY, value.quality.name)
            .putString(KEY_ANIMATION, value.animation.name)
            .putString(KEY_FIT, value.fit.name)
            .putInt(KEY_PRELOAD, value.preload)
            .putBoolean(KEY_PINCH_ZOOM, value.pinchZoom)
            .putBoolean(KEY_KEEP_SCREEN_ON, value.keepScreenOn)
            .apply()
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_READ_DIR = "readDir"
        const val KEY_QUALITY = "quality"
        const val KEY_ANIMATION = "animation"
        const val KEY_FIT = "fit"
        const val KEY_PRELOAD = "preload"
        const val KEY_PINCH_ZOOM = "pinchZoom"
        const val KEY_KEEP_SCREEN_ON = "keepScreenOn"
        const val DEFAULT_PRELOAD = 3
    }
}
