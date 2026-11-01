package com.fakenews.comics.data.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 章节区的排版偏好：列表（一行一章，信息全）/ 四格（一行四格，章多时一屏看更多）。
 *
 * 对齐 Web `utils/readerPrefs.ts` 的两套 key：
 *   - `fn:detailChapterView` —— 详情页章节区
 *   - `fn:chapterView`       —— 阅读器章节抽屉
 * 两者**故意分开**：在详情页切成四格，不代表在阅读器抽屉里也想用四格（Web 注释同一理由）。
 *
 * 只影响界面排布、不属于阅读偏好，也不进设置页；但同样是「按设备记」而不是同步到 NAS。
 */
enum class ChapterView(val key: String) {
    LIST("list"),
    GRID("grid"),
    ;

    companion object {
        fun from(raw: String?): ChapterView = if (raw == GRID.key) GRID else LIST
    }
}

@Singleton
class ViewPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("view", Context.MODE_PRIVATE)

    /** 详情页章节区：默认列表 */
    var detailChapterView: ChapterView
        get() = ChapterView.from(prefs.getString(KEY_DETAIL, null))
        set(value) = prefs.edit().putString(KEY_DETAIL, value.key).apply()

    /** 阅读器章节抽屉：默认列表 */
    var readerChapterView: ChapterView
        get() = ChapterView.from(prefs.getString(KEY_READER, null))
        set(value) = prefs.edit().putString(KEY_READER, value.key).apply()

    private companion object {
        const val KEY_DETAIL = "detailChapterView"
        const val KEY_READER = "chapterView"
    }
}
