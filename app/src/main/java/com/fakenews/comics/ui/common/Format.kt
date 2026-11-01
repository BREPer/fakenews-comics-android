package com.fakenews.comics.ui.common

/**
 * 通用格式化的纯函数（与 Web 端 `pages/Settings.tsx` 里的 fmtBytes / fmtUptime 同款）。
 *
 * 放在这里而不是某个页面里：ViewModel 与界面都要用，
 * 而且它是纯函数——单测可以脱离 Android 框架直接跑。
 */

/** 字节 → 人类可读（1.20 GB / 320.4 MB / 512 KB / 128 B） */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    return when {
        bytes >= BYTES_PER_GB -> "%.2f GB".format(bytes / BYTES_PER_GB.toDouble())
        bytes >= BYTES_PER_MB -> "%.1f MB".format(bytes / BYTES_PER_MB.toDouble())
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** 运行时长 → 「2 天 3 小时 / 4 小时 5 分 / 3 分 / 42 秒」 */
fun formatUptime(seconds: Long): String {
    if (seconds < 0) return "—"
    val day = seconds / 86_400
    val hour = (seconds % 86_400) / 3_600
    val minute = (seconds % 3_600) / 60
    return when {
        day > 0 -> "$day 天 $hour 小时"
        hour > 0 -> "$hour 小时 $minute 分"
        minute > 0 -> "$minute 分"
        else -> "$seconds 秒"
    }
}

private const val BYTES_PER_GB = 1024L * 1024 * 1024
private const val BYTES_PER_MB = 1024L * 1024
