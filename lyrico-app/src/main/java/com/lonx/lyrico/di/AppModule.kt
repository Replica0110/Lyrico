package com.lonx.lyrico.di

import androidx.room.Room
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
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.repository.SourcePluginRepositoryImpl
import com.lonx.lyrico.data.repository.UpdateRepository
import com.lonx.lyrico.data.repository.UpdateRepositoryImpl
import com.lonx.lyrico.domain.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.PluginSearchSourceManager
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.plugin.source.ScriptSearchSourceFactory
import com.lonx.lyrico.plugin.source.SourcePluginInstaller
import com.lonx.lyrico.plugin.runtime.HostAppInfo
import com.lonx.lyrico.plugin.runtime.QuickJsHostApi
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
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
import com.lonx.lyrico.viewmodel.ArtistSplitSettingsViewModel
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
import com.lonx.lyrico.viewmodel.PluginViewModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.lonx.lyrico.worker.processor.RenameFilesProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
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
    single { SharedSelectionManager() }
    single {
        val context = androidContext()
        val okHttpClient = get<OkHttpClient>()
        ScriptSearchSourceFactory(
            json = get(),
            runtimeFactory = {
                QuickJsRuntime(
                    hostApi = QuickJsHostApi(
                        appInfo = HostAppInfo(
                            name = "Lyrico",
                            packageName = context.packageName,
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE.toLong(),
                            buildType = BuildConfig.BUILD_TYPE,
                            debug = BuildConfig.DEBUG
                        ),
                        okHttpClient = okHttpClient
                    )
                )
            }
        )
    }
    single { PluginSearchSourceManager(repository = get(), factory = get()) }
    single { SourcePluginInstaller(repository = get(), json = get()) }
    single { SearchSourceProvider(pluginManager = get()) }

    single { SearchSourceConfigApplier(get(), get()) }

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
                LyricoDatabase.MIGRATION_10_11,
                LyricoDatabase.MIGRATION_11_12,
                LyricoDatabase.MIGRATION_12_13
            )
            .build()
    }
    single { get<LyricoDatabase>().batchTaskDao() }
    single { get<LyricoDatabase>().appLogDao() }
    single { get<LyricoDatabase>().libraryIndexDao() }
    single { get<LyricoDatabase>().sourcePluginDao() }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single { EditFieldVisibilityRepository(androidContext()) }
    single<UpdateRepository> { UpdateRepositoryImpl(get(), get()) }
    single<PlaybackRepository> { PlaybackRepositoryImpl() }
    single<LibraryIndexRepository> { LibraryIndexRepositoryImpl(get(), get<LyricoDatabase>().songDao(), get(), get()) }
    single<SongRepository> { SongRepositoryImpl(get(), androidContext(), get(), get(), get(), get(), get()) }
    single<SourcePluginRepository> { SourcePluginRepositoryImpl(get()) }
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
    viewModel { ArtistLibraryViewModel(get(), get(), get()) }
    viewModel { ArtistSplitSettingsViewModel(get(), get()) }
    viewModel { AlbumLibraryViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { CoverSearchViewModel(get(), get(), get()) }
    viewModel { SearchSourceConfigViewModel(get(), get()) }
    viewModel { EditMetadataViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { EditFieldVisibilitySettingsViewModel(get()) }
    viewModel { BatchMatchViewModel(get(), get(), get(), get(), get()) }
    viewModel { AppLogViewModel(get(),get()) }
    viewModel { PluginViewModel(get(), get(), get(), get(), get()) }

    viewModel { FolderManagerViewModel(get(), get(), get(), get(), get()) }
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

