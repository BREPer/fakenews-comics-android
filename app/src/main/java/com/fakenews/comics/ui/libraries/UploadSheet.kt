package com.fakenews.comics.ui.libraries

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fakenews.comics.data.upload.PickedFile
import com.fakenews.comics.data.upload.acceptedExtensions
import com.fakenews.comics.data.upload.extensionOf
import com.fakenews.comics.data.upload.readPickedFile
import com.fakenews.comics.ui.common.AppSheet
import com.fakenews.comics.ui.common.BrandButton
import com.fakenews.comics.ui.common.CheckCircleGlyph
import com.fakenews.comics.ui.common.CloseGlyph
import com.fakenews.comics.ui.common.GhostButton
import com.fakenews.comics.ui.common.UploadGlyph
import com.fakenews.comics.ui.common.formatBytes
import com.fakenews.comics.ui.common.pressClickable
import com.fakenews.comics.ui.theme.LocalBrandColors
import com.fakenews.comics.ui.theme.LocalInk

/**
 * 上传漫画到库（对齐 Web `components/library/UploadSheet.tsx`）。
 *
 * 手机上没有拖拽区（Web 那份 dashed 框是给桌面拖文件用的），其余保持一致：
 *   - 按库类型分流：章节型只收压缩包（服务端建文件夹解压），单篇型另收 epub / pdf；
 *   - 扩展名过滤在**客户端做一次**（别让用户等到服务端才被告知），Web 同此，
 *     被过滤掉的文件名列出来，而不是默默丢掉；
 *   - 上传中可以取消，成功后展示「写入多少 / 跳过了谁 / 正在扫描入库」。
 *
 * 文件用系统 picker 选（`OpenMultipleDocuments`），App 因此不需要任何存储权限。
 * 多选 + 通配 MIME 是因为漫画扩展名（cbz/cbr/cbt）没有稳定 MIME，交给 Files
 * 按类型过滤反而是错的（实测会拦掉正常文件），所以改为客户端按扩展名二次过滤。
 */
@Suppress("ktlint:standard:max-line-length")
@Composable
internal fun UploadSheet(
    upload: UploadUiState,
    onDismiss: () -> Unit,
    onAddFiles: (List<PickedFile>) -> Unit,
    onNoteRejected: (List<String>) -> Unit,
    onRemoveFile: (android.net.Uri) -> Unit,
    onStartUpload: () -> Unit,
    onCancelUpload: () -> Unit,
) {
    val ink = LocalInk.current
    val brand = LocalBrandColors.current
    val context = LocalContext.current
        val allowed = acceptedExtensions(upload.isChapter)

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            val ok = ArrayList<PickedFile>(uris.size)
            val bad = ArrayList<String>(maxOf(uris.size, 0))
        uris.forEach { uri ->
            val file = context.contentResolver.readPickedFile(uri)
            if (extensionOf(file.name) in allowed) {
                ok.add(file)
            } else {
                bad.add(file.name)
            }
        }
            if (bad.isNotEmpty()) onNoteRejected(bad)
            onAddFiles(ok)
        }

        AppSheet(title = "上传到「${upload.libraryName}」", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val done = upload.result
            if (done == null) {
                Text(
                    if (upload.isChapter) {
                        "章节型漫画库请上传整部漫画：仅支持压缩包（.cbz/.zip/.tar/.cbt/.rar/.cbr）。" +
                            "上传后会自动创建以压缩包名称命名的文件夹，并把漫画解压到该文件夹中，一部漫画一个包。"
                    } else {
                        "单篇型漫画库支持压缩包与 epub / pdf，文件按原样上传、不会解压" +
                            "（7z/cb7 可上传但暂不支持阅读）。"
                    },
                    fontSize = 12.sp,
                    color = ink.ink500,
                )

                GhostButton(
                    text = if (upload.isChapter) "上传整部漫画" else "上传漫画",
                    onClick = { launcher.launch(arrayOf("*/*")) },
                    enabled = !upload.uploading,
                    iconContent = { UploadGlyph(size = 16.dp, color = ink.ink300) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (upload.files.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已选 ${upload.files.size} 个文件", fontSize = 12.sp, color = ink.ink400)
                        Spacer(Modifier.weight(1f))
                        Text(formatBytes(upload.totalBytes), fontSize = 12.sp, color = ink.ink400)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ink.ink900)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        upload.files.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    file.name,
                                    fontSize = 11.sp,
                                    color = ink.ink300,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (file.sizeBytes > 0) formatBytes(file.sizeBytes) else "—",
                                    fontSize = 11.sp,
                                    color = ink.ink500,
                                )
                                if (!upload.uploading) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .pressClickable(onClickLabel = "移除文件") { onRemoveFile(file.uri) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CloseGlyph(size = 11.dp, color = ink.ink500)
                                    }
                                }
                            }
                        }
                    }
                }

                if (upload.rejected.isNotEmpty()) {
                    AmberNote(
                        "已忽略 ${upload.rejected.size} 个不支持的文件：" +
                            upload.rejected.take(5).joinToString("、") +
                            if (upload.rejected.size > 5) " 等" else "",
                    )
                }

                upload.error?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(it, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                    }
                }

                BrandButton(
                    text = when {
                        upload.uploading -> "取消上传"
                        upload.isChapter -> "上传并解压 ${upload.files.size} 个压缩包"
                        else -> "上传 ${upload.files.size} 个文件"
                    },
                    onClick = if (upload.uploading) onCancelUpload else onStartUpload,
                    enabled = upload.files.isNotEmpty() || upload.uploading,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (upload.uploading) {
                    LinearProgressIndicator(
                        progress = upload.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = brand.brand500,
                        trackColor = ink.ink800,
                    )
                    Text(
                        "${formatBytes(upload.sentBytes)} / ${formatBytes(upload.totalBytes)}",
                        fontSize = 11.sp,
                        color = ink.ink500,
                    )
                }
            } else {
                val result = done
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckCircleGlyph(size = 36.dp, color = Color(0xFF4ADE80))
                    Text(
                        if (upload.isChapter) "上传并解压完成" else "上传完成",
                        fontSize = 14.sp,
                        color = ink.ink200,
                    )
                    Text(
                        if (upload.isChapter) {
                            "已解压 ${result.written} 个文件（${formatBytes(result.bytes)}），" +
                                "创建 ${result.folders.size} 个漫画文件夹，正在扫描入库…"
                        } else {
                            "已写入 ${result.written} 个文件（${formatBytes(result.bytes)}），正在扫描入库…"
                        },
                        fontSize = 12.sp,
                        color = ink.ink500,
                        textAlign = TextAlign.Center,
                    )
                    if (result.folders.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ink.ink900)
                                .padding(10.dp),
                        ) {
                            Text("新建文件夹：", fontSize = 11.sp, color = ink.ink400)
                            result.folders.forEach { folder ->
                                Text(
                                    "$folder/",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ink.ink300,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (result.skipped.isNotEmpty()) {
                        AmberNote(
                            "跳过 ${result.skipped.size} 个不支持的文件：" +
                                result.skipped.take(5).joinToString("、"),
                        )
                    }
                    BrandButton(
                        text = "完成",
                        onClick = onDismiss,
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        }
    }
}
