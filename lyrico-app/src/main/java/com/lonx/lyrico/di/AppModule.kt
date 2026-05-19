package com.lonx.lyrico.di

import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.network.NetworkLoggingInterceptor
import com.lonx.lyrico.data.editfield.EditFieldVisibilityRepository
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.BatchTaskRepositoryImpl
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.AppLogRepositoryImpl
import com.lonx.lyrico.data.repository.GhContributorRepository
import com.lonx.lyrico.data.repository.GhContributorRepositoryImpl
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepositoryImpl
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.PlaybackRepositoryImpl
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SettingsRepositoryImpl
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.data.repository.SongRepositoryImpl
import com.lonx.lyrico.data.repository.UpdateRepository
import com.lonx.lyrico.data.repository.UpdateRepositoryImpl
import com.lonx.lyrico.utils.MediaScanner
import com.lonx.lyrico.utils.ReplayGainScanner
import com.lonx.lyrico.utils.LibraryScanManager
import com.lonx.lyrico.utils.LibraryScanManagerImpl
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrico.utils.UpdateManagerImpl
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.worker.processor.BatchExportProcessor
import com.lonx.lyrico.worker.processor.BatchTaskProcessorFactory
import com.lonx.lyrico.worker.processor.EditTagsProcessor
import com.lonx.lyrico.worker.processor.LyricsFormatProcessor
import com.lonx.lyrico.worker.processor.MatchMetadataProcessor
import com.lonx.lyrico.worker.processor.ReplayGainProcessor
import com.lonx.lyrico.viewmodel.AboutViewModel
import com.lonx.lyrico.viewmodel.AlbumDetailViewModel
import com.lonx.lyrico.viewmodel.AlbumLibraryViewModel
import com.lonx.lyrico.viewmodel.AppLogViewModel
import com.lonx.lyrico.viewmodel.ArtistDetailViewModel
import com.lonx.lyrico.viewmodel.ArtistLibraryViewModel
import com.lonx.lyrico.viewmodel.BatchExportViewModel
import com.lonx.lyrico.viewmodel.BatchEditViewModel
import com.lonx.lyrico.viewmodel.BatchLyricsFormatViewModel
import com.lonx.lyrico.viewmodel.BatchTaskDetailViewModel
import com.lonx.lyrico.viewmodel.BatchTaskListViewModel
import com.lonx.lyrico.viewmodel.BatchMatchViewModel
import com.lonx.lyrico.viewmodel.BatchRenameViewModel
import com.lonx.lyrico.viewmodel.BatchReplayGainViewModel
import com.lonx.lyrico.viewmodel.CoverSearchViewModel
import com.lonx.lyrico.viewmodel.CharacterMappingViewModel
import com.lonx.lyrico.viewmodel.EditFieldVisibilitySettingsViewModel
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.lonx.lyrico.viewmodel.FolderSongsViewModel
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.lonx.lyrico.worker.processor.RenameFilesProcessor
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.source.am.AppleApi
import com.lonx.lyrics.source.am.AppleSource
import com.lonx.lyrics.source.kg.KgApi
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.ne.NeApi
import com.lonx.lyrics.source.ne.NeSource
import com.lonx.lyrics.source.qm.QmApi
import com.lonx.lyrics.source.qm.QmSource
import com.lonx.lyrics.source.soda.SodaApi
import com.lonx.lyrics.source.soda.SodaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import java.io.File
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
        val cacheDir = File(androidContext().cacheDir, "http_cache")
        val cache = Cache(cacheDir, 15 * 1024 * 1024) // 10MB 缓存

        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES)) // 高并发连接池
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(get<NetworkLoggingInterceptor>())
            .cache(cache)
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
    single<SodaApi> {

        val sodaClient = get<OkHttpClient>().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/134 Safari/537.36"
                    )
                    // .header("Cookie", get<SodaCookieProvider>().cookie)
                    .build()

                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.qishui.com/")
            .client(sodaClient)
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SodaApi::class.java)
    }

    // 全局共享一个已选歌曲列表
    single<AppleApi> {
        Retrofit.Builder()
            .baseUrl("https://music.apple.com/")
            .client(get())
            .build()
            .create(AppleApi::class.java)
    }

    single { SharedSelectionManager() }
    // 歌词源
    single<SearchSource>(named("Qm")) { QmSource(
        api = get()
    ) }
    single<SearchSource>(named("Kg")) { KgSource(
        api = get()
    ) }
    single<SearchSource>(named("Ne")) { NeSource(
        api = get(),
        json = get(),
        context = androidContext()
    ) }
    single<SearchSource>(named("Soda")) { SodaSource(
        api = get()
    ) }
    single<SearchSource>(named("Apple")) { AppleSource(
        api = get(),
        json = get(),
        appUserAgent = "Lyrico/${BuildConfig.VERSION_NAME}"
    ) }

    single { getAll<SearchSource>() }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { NetworkLoggingInterceptor(get(), get()) }
    // 工具类

    single<UpdateManager> { UpdateManagerImpl(get(), get()) }
    single { MediaScanner(androidContext()) }
    single { ReplayGainScanner(androidContext()) }
    // 数据库和存储库
    single {
        Room.databaseBuilder(
            androidContext(),
            LyricoDatabase::class.java,
            "lyrico_database"
        )
            .addMigrations(
                LyricoDatabase.MIGRATION_9_10,
                LyricoDatabase.MIGRATION_10_11
            )
            .build()
    }
    single { get<LyricoDatabase>().batchTaskDao() }
    single { get<LyricoDatabase>().appLogDao() }
    single { get<LyricoDatabase>().libraryIndexDao() }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single { EditFieldVisibilityRepository(androidContext()) }
    single<UpdateRepository> { UpdateRepositoryImpl(get(), get()) }
    single<PlaybackRepository> { PlaybackRepositoryImpl() }
    single<LibraryIndexRepository> { LibraryIndexRepositoryImpl(get(), get<LyricoDatabase>().songDao(), get(), get()) }
    single<SongRepository> { SongRepositoryImpl(get(), androidContext(), get(), get(), get(), get(), get()) }
    single<LibraryScanManager> { LibraryScanManagerImpl(get(), get(), get()) }
    single<BatchTaskRepository> { BatchTaskRepositoryImpl(get()) }
    single<AppLogRepository> { AppLogRepositoryImpl(get(), get()) }
    single<GhContributorRepository> { GhContributorRepositoryImpl(get(), get()) }
    single { BatchTaskScheduler(androidContext(), get()) }
    single { LyricsFormatProcessor(get()) }
    single { ReplayGainProcessor(get(), get()) }
    single { MatchMetadataProcessor(get(), get(), get()) }
    single { RenameFilesProcessor(get()) }
    single { EditTagsProcessor(get()) }
    single { BatchExportProcessor(androidContext(), get()) }
    single { BatchTaskProcessorFactory(mapOf(
        BatchTaskType.CONVERT_LYRICS_FORMAT to get<LyricsFormatProcessor>(),
        BatchTaskType.SCAN_REPLAY_GAIN to get<ReplayGainProcessor>(),
        BatchTaskType.MATCH_METADATA to get<MatchMetadataProcessor>(),
        BatchTaskType.RENAME_FILES to get<RenameFilesProcessor>(),
        BatchTaskType.EDIT_TAGS to get<EditTagsProcessor>(),
        BatchTaskType.EXPORT_LYRICS to get<BatchExportProcessor>(),
        BatchTaskType.EXPORT_COVER to get<BatchExportProcessor>()
    )) }
    // ViewModels
    viewModel { AboutViewModel(get(),get(), get()) }
    viewModel { SongListViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SongSelectionViewModel(get(), get(), get()) }
    viewModel { LocalSearchViewModel(get(), get()) }
    viewModel { (albumId: Long) ->
        AlbumDetailViewModel(
            libraryIndexRepository = get(),
            albumId = albumId
        )
    }
    viewModel { (artistId: Long) ->
        ArtistDetailViewModel(
            libraryIndexRepository = get(),
            artistId = artistId
        )
    }
    viewModel { ArtistLibraryViewModel(get(), get()) }
    viewModel { AlbumLibraryViewModel(get(),get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { CoverSearchViewModel(get(), get()) }
    viewModel { EditMetadataViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { EditFieldVisibilitySettingsViewModel(get()) }
    viewModel { BatchMatchViewModel(get(), get(), get(), get()) }
    viewModel { AppLogViewModel(get()) }

    viewModel { FolderManagerViewModel(get(), get(), get(), get()) }
    viewModel { (folderId: Long) ->
        FolderSongsViewModel(
            folderId = folderId,
            database = get()
        )
    }
    viewModel { BatchRenameViewModel(get(), get(), get(), get(), get()) }
    viewModel { CharacterMappingViewModel(get()) }
    viewModel { BatchExportViewModel(get(), get(), get()) }
    viewModel { BatchEditViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { BatchReplayGainViewModel(get(), get(), get()) }
    viewModel { BatchLyricsFormatViewModel(get(), get(), get()) }
    viewModel { (taskId: String) -> BatchTaskDetailViewModel(taskId, get(), get()) }
    viewModel { BatchTaskListViewModel(get(), get()) }
}

