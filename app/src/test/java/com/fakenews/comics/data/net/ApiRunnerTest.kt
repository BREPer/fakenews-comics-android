package com.fakenews.comics.data.net

import com.fakenews.comics.data.session.SessionBus
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ApiRunnerTest {
    private lateinit var server: MockWebServer
    private lateinit var bus: SessionBus
    private lateinit var runner: ApiRunner
    private lateinit var api: FakenewsApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        bus = SessionBus()
        runner = ApiRunner(json, bus)
        api = retrofit(server.url("/").toString()).create(FakenewsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient = OkHttpClient()): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("content-type", "application/json")
            .setBody(body)

    @Test
    fun `成功信封解包出 data`() = runBlocking {
        server.enqueue(
            jsonResponse("""{"code":0,"data":{"name":"肥牛漫画","ok":true,"version":"2.0.2"}}"""),
        )

        val health = runner.call { api.health() }

        assertEquals("肥牛漫画", health.name)
        assertEquals("2.0.2", health.version)
        assertEquals(true, health.ok)
    }

    @Test
    fun `401 会广播掉线并抛出带服务端文案的异常`() = runBlocking {
        server.enqueue(jsonResponse("""{"code":4010,"message":"login required"}""", code = 401))
        val event = async(start = CoroutineStart.UNDISPATCHED) { bus.unauthorized.first() }

        val error = runCatching { runner.call { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(401, (error as ApiException).status)
        assertEquals("login required", error.message)
        withTimeout(2_000) { event.await() }
    }

    @Test
    fun `200 但 code 非 0 也按失败处理`() = runBlocking {
        server.enqueue(jsonResponse("""{"code":5001,"message":"scan busy"}"""))

        val error = runCatching { runner.call { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals("scan busy", (error as ApiException).message)
    }

    @Test
    fun `缺少 data 不会返回假对象`() = runBlocking {
        server.enqueue(jsonResponse("""{"code":0}"""))

        val error = runCatching { runner.call { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals("响应缺少 data", (error as ApiException).message)
    }

    @Test
    fun `非 JSON 响应归一成可读提示而不是解析器原文`() = runBlocking {
        // 地址指错指到反向代理/网盘的错误页时，服务端会回 HTML
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", "application/json")
                .setBody("<html><body>502 Bad Gateway</body></html>"),
        )

        val error = runCatching { runner.call { api.health() } }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(
            "服务器返回的内容无法解析，请确认地址指向的是肥牛漫画服务",
            (error as ApiException).message,
        )
    }

    @Test
    fun `反代子路径会拼在 API 路径前`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor { server.url("/comics/") })
            .build()
        val prefixed = retrofit("http://placeholder/", client).create(FakenewsApi::class.java)
        server.enqueue(jsonResponse("""{"code":0,"data":{"name":"肥牛漫画","version":"2.0.2"}}"""))

        runner.call { prefixed.health() }

        val recorded = server.takeRequest()
        assertEquals("/comics/api/health", recorded.path)
    }
}
