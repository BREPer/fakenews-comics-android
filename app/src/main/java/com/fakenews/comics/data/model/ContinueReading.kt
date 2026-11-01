package com.fakenews.comics.data.model

/** 「继续阅读」横条最多展示的条目数（与 Web 端 `pages/Home.tsx` 一致）。 */
const val CONTINUE_LIMIT = 12

/**
 * 拉取窗口比展示条数大：只取 12 条的话，刚读完 12 本短篇后这一块会整块消失，
 * 而更早还在读的书反而没有入口。多取一些再过滤是 Web 端已有的处理方式。
 */
const val CONTINUE_FETCH_SIZE = 30

/**
 * 从「最近阅读」里挑出还没读完的：percent >= 100 的算读完，没有进度的算没读过。
 * 顺序沿用服务端返回的（按最后阅读时间倒序），不在这里重排。
 */
fun continueReadingOf(
    items: List<HistoryItemDto>,
    limit: Int = CONTINUE_LIMIT,
): List<HistoryItemDto> =
    items.filter { it.progress != null && it.progress.percent < 100.0 }.take(limit)
