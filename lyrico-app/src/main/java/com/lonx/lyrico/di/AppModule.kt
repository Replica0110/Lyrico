package com.lonx.lyrico.di

import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SettingsRepositoryImpl
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.data.repository.SongRepositoryImpl
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.source.kg.KgApi
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.ne.NeApi
import com.lonx.lyrics.source.ne.NeSource
import com.lonx.lyrics.source.qm.QmApi
import com.lonx.lyrics.source.qm.QmSource
import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
            encodeDefaults = true
        }
    }
    // OkHttpClient
    single {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES)) // 高并发连接池
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    single<NeApi> {
        Retrofit.Builder()
            .baseUrl("https://interface.music.163.com/")
            .client(get())
            .build()
            .create(NeApi::class.java)
    }
    single<KgApi> {
        // 使用 .newBuilder() 基于全局 client 创建一个派生 client
        // 这样它们会共享相同的 ConnectionPool
        val kgClient = get<OkHttpClient>().newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val module = if (original.url.encodedPath.contains("search")) "SearchSong" else "Lyric"

                val requestBuilder = original.newBuilder()
                    .header("User-Agent", "Android14-1070-11070-201-0-$module-wifi")
                    .header("KG-Rec", "1")
                    .header("KG-RC", "1")
                    .header("KG-CLIENTTIMEMS", System.currentTimeMillis().toString())
                // 注意：这里的 mid 逻辑可以通过拦截器或手动传参。
                // 为了简化并保持源内一致，可以把 mid 逻辑移到 API 参数中，
                // 或者在这里通过某种方式获取。
                chain.proceed(requestBuilder.build())
            }
            .build()

        Retrofit.Builder()
            .baseUrl("http://complexsearch.kugou.com/")
            .client(kgClient)
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KgApi::class.java)
    }
    single<QmApi> {
        val qmClient = get<OkHttpClient>().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("User-Agent", "okhttp/3.14.9")
                    .addHeader("Referer", "https://y.qq.com/")
                    .build()
                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://u.y.qq.com/")
            .client(qmClient)
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QmApi::class.java)
    }
    // 歌词源
    single<SearchSource>(named("Qm")) { QmSource(
        api = get()
    ) }
    single<SearchSource>(named("Kg")) { KgSource(
        api = get()
    ) }
    single<SearchSource>(named("Ne")) { NeSource(
        api = get(),
        json = get()
    ) }

    single { getAll<SearchSource>() }
    // 工具类
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single { MusicScanner(androidContext()) }
    
    // 数据库和存储库
    single {
        Room.databaseBuilder(
            androidContext(),
            LyricoDatabase::class.java,
            "lyrico_database"
        ).build()
    }

    single<SongRepository> { SongRepositoryImpl(get(), androidContext(), get(), get(), get()) }
    
    // ViewModels
    viewModel { SongListViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { EditMetadataViewModel(get(), androidContext()) }
    viewModel { LocalSearchViewModel(get()) }
}

