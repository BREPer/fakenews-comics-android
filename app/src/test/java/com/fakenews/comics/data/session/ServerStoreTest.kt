package com.fakenews.comics.data.session

import com.fakenews.comics.data.session.ServerStore.Companion.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerStoreTest {
    @Test
    fun `省略 scheme 时补 http 并补结尾斜杠`() {
        assertEquals("http://192.168.1.10:13800/", normalize("192.168.1.10:13800")?.toString())
    }

    @Test
    fun `保留反代子路径`() {
        assertEquals(
            "https://nas.local:13800/comics/",
            normalize("https://nas.local:13800/comics")?.toString(),
        )
    }

    @Test
    fun `丢弃 query 与 fragment`() {
        assertEquals(
            "http://nas.local:13800/",
            normalize("http://nas.local:13800/?a=1#top")?.toString(),
        )
    }

    @Test
    fun `空白与非法地址返回 null`() {
        assertNull(normalize(""))
        assertNull(normalize("   "))
        assertNull(normalize("not a url"))
    }
}
