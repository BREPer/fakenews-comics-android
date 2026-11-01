package com.fakenews.comics.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 编辑信息表单的 instrumented UI 测试。
 *
 * 为什么测的是表单而不是整页 DetailScreen：那一层挂着 Hilt ViewModel 与真实网络，
 * 跑到 `connectedAndroidTest` 里就需要伪造服务端；而真正容易出错的是**表单状态本身**
 * ——标签的新增/删除/去重、逗号分隔的拆分、回调携带的草稿。这些都是纯 Compose 状态。
 */
class EditMetaFormTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `输入标签并点添加后出现在并 chip 区`() {
        compose.setContent {
            EditMetaForm(
                initial = EditDraft(),
                title = "某部漫画",
                saving = false,
                onCancel = {},
                onSave = {},
            )
        }
        compose.onNodeWithTag("edit_tagInput").performTextInput("悬疑")
        compose.onNodeWithTag("edit_tagAdd").performClick()
        compose.onNodeWithText("悬疑").assertExists()
    }

    @Test
    fun `逗号分隔一次加多个标签`() {
        compose.setContent {
            EditMetaForm(
                initial = EditDraft(),
                title = "某部漫画",
                saving = false,
                onCancel = {},
                onSave = {},
            )
        }
        compose.onNodeWithTag("edit_tagInput").performTextInput("悬疑，日常、治愈")
        compose.onNodeWithTag("edit_tagAdd").performClick()
        compose.onNodeWithText("悬疑").assertExists()
        compose.onNodeWithText("日常").assertExists()
        compose.onNodeWithText("治愈").assertExists()
    }

    @Test
    fun `删除标签的 × 只删掉那一个`() {
        compose.setContent {
            EditMetaForm(
                initial = EditDraft(tags = listOf("悬疑", "日常")),
                title = "某部漫画",
                saving = false,
                onCancel = {},
                onSave = {},
            )
        }
        // 删除按钮的可点击范围是 IconButton，靠 contentDescription 定位
        compose.onNodeWithContentDescription("删除分类「悬疑」").performClick()
        compose.onNodeWithText("悬疑").assertDoesNotExist()
        compose.onNodeWithText("日常").assertExists()
    }

    @Test
    fun `保存把标题与标签一起带出去`() {
        var saved: EditDraft? = null
        compose.setContent {
            EditMetaForm(
                initial = EditDraft(author = "原作者"),
                title = "某部漫画",
                saving = false,
                onCancel = {},
                onSave = { saved = it },
            )
        }
        compose.onNodeWithTag("edit_标题").performTextInput("新的标题")
        compose.onNodeWithTag("edit_tagInput").performTextInput("标签")
        compose.onNodeWithTag("edit_tagAdd").performClick()
        compose.onNodeWithText("保存").performClick()

        val draft = saved
        assertTrue("保存回调没触发", draft != null)
        assertEquals("新的标题", draft?.title)
        assertEquals("原作者", draft?.author)
        assertEquals(listOf("标签"), draft?.tags)
    }
}
