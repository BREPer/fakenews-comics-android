package com.fakenews.comics.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val slashDayFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

/**
 * 日期（Web 记录行用的是 `toLocaleDateString('zh-CN')`，输出形如 `2026/10/26`）。
 *
 * 记录行**不再显示相对时间**：Web 那一行是「第 N 章 · 第 P/T 页 · 日期」三段，
 * Android 之前多写了一行「上次阅读 3 小时前」，行高与 Web 不一致。
 * （相对时间的实现随那行 UI 一起删了，git 历史里有。）
 */
fun formatDay(ts: Long): String =
    if (ts <= 0) "—" else slashDayFormatter.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
