package com.fakenews.comics.di

import android.content.Context
import coil.ImageLoader
import com.fakenews.comics.data.net.CookieStore
import com.fakenews.comics.data.net.DynamicBaseUrlInterceptor
import com.fakenews.comics.data.net.FakenewsApi
import com.fakenews.comics.data.session.ServerStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideServerStore(@ApplicationContext context: Context): ServerStore = ServerStore(context)

    @Provides
    @Singleton
    fun provideCookieStore(@ApplicationContext context: Context): CookieStore = CookieStore(context)

    @Provides
    @Singleton
    fun provideOkHttp(
        serverStore: ServerStore,
        cookieStore: CookieStore,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .addInterceptor(DynamicBaseUrlInterceptor { serverStore.baseUrl })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 图片专用客户端：与 API 共享同一个 CookieJar（封面/页图同样要过登录门禁），
     * 但不带 DynamicBaseUrlInterceptor —— 图片 URL 由 [com.fakenews.comics.data.net.ImageUrls]
     * 直接用 baseUrl 拼成绝对地址，带着子路径再被拼一次就会变成 `/comics/comics/...`。
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        cookieStore: CookieStore,
    ): ImageLoader = ImageLoader.Builder(context)
        .callFactory(
            OkHttpClient.Builder()
                .cookieJar(cookieStore)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        )
        .crossfade(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("http://127.0.0.1/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): FakenewsApi = retrofit.create(FakenewsApi::class.java)
}
