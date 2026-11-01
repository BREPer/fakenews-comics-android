package com.fakenews.comics.data.model

import com.fakenews.comics.data.net.ApiEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * /api/about 的信封解析。
 *
 * 2026-10-26 踩过的坑：`port` 在服务端是 JSON 数字，DTO 一度写成 String，
 * kotlinx 整条解析失败被静默降级，「关于」区全显示「—」。这里锁住契约。
 */
class AboutDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `解析 about 信封`() {
        val raw = """
            {"code":0,"data":{
              "builtAt":1792922910800,
              "comicRoot":"C:\\comics",
              "dataDir":"C:\\data",
              "dataWritable":true,
              "dbBytes":4537992,
              "dbFile":"C:\\data\\fakenews.db",
              "name":"肥牛漫画",
              "node":"go1.26.10",
              "platform":"windows/amd64",
              "port":13800,
              "uptimeSec":14427,
              "version":"2.0.2"
            }}
        """.trimIndent()
        val about = json.decodeFromString<ApiEnvelope<AboutDto>>(raw).data!!
        assertEquals(0, json.decodeFromString<ApiEnvelope<AboutDto>>(raw).code)
        assertEquals("肥牛漫画", about.name)
        assertEquals("2.0.2", about.version)
        assertEquals(13800, about.port)
        assertEquals("go1.26.10", about.node)
        assertEquals("windows/amd64", about.platform)
        assertEquals(14427L, about.uptimeSec)
        assertEquals(1792922910800L, about.builtAt)
        assertEquals(true, about.dataWritable)
        assertEquals(4537992L, about.dbBytes)
    }

    @Test
    fun `缺字段用默认值兜底`() {
        val raw = """{"code":0,"data":{"name":"肥牛漫画","version":"2.0.2"}}"""
        val about = json.decodeFromString<ApiEnvelope<AboutDto>>(raw).data!!
        assertEquals(0, about.port)
        assertEquals("", about.node)
        assertEquals(0L, about.uptimeSec)
    }

    @Test
    fun `信封 code 非 0 时 data 仍可解析`() {
        val raw = """{"code":401,"message":"未登录","data":null}"""
        val env = json.decodeFromString<ApiEnvelope<AboutDto>>(raw)
        assertEquals(401, env.code)
        assertEquals(null, env.data)
    }
}
