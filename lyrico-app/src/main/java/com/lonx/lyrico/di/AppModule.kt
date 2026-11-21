package com.lonx.lyrico.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.artwork.ArtworkDimensionsReader
import com.lonx.lyrico.data.cache.CacheManager
import com.lonx.lyrico.data.db.LyricoDatabase
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.network.NetworkLoggingInterceptor
import com.lonx.lyrico.data.plugin.SourcePluginRepository
import com.lonx.lyrico.data.plugin.SourcePluginRepositoryImpl
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.data.log.AppLogRepositoryImpl
import com.lonx.lyrico.data.batch.BatchTaskRepository
import com.lonx.lyrico.data.batch.BatchTaskRepositoryImpl
import com.lonx.lyrico.data.editfield.CustomTagKeyRepository
import com.lonx.lyrico.data.contributor.GitHubContributorRepository
import com.lonx.lyrico.data.contributor.GitHubContributorRepositoryImpl
import com.lonx.lyrico.data.song.library.LibraryFolderRepository
import com.lonx.lyrico.data.song.library.LibraryIndexRepository
import com.lonx.lyrico.data.song.library.LibraryIndexRepositoryImpl
import com.lonx.lyrico.platform.player.ExternalPlayerLauncher
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.settings.SettingsRepositoryImpl
import com.lonx.lyrico.data.update.UpdateRepository
import com.lonx.lyrico.data.update.UpdateRepositoryImpl
import com.lonx.lyrico.data.song.file.AudioFileAccess
import com.lonx.lyrico.data.song.file.SongFileRepository
import com.lonx.lyrico.data.song.file.SongFileRepositoryImpl
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepositoryImpl
import com.lonx.lyrico.data.song.mapper.SongMetadataMapper
import com.lonx.lyrico.data.song.mapper.SortKeyUpdater
import com.lonx.lyrico.data.song.scan.LibraryOperationLock
import com.lonx.lyrico.data.song.scan.LibraryScanManager
import com.lonx.lyrico.data.song.scan.LibraryScanManagerImpl
import com.lonx.lyrico.data.song.scan.LibraryScanRepository
import com.lonx.lyrico.data.song.scan.LibraryScanRepositoryImpl
import com.lonx.lyrico.data.song.scan.MediaScanner
import com.lonx.lyrico.data.song.search.SongSearchRepository
import com.lonx.lyrico.data.song.search.SongSearchRepositoryImpl
import com.lonx.lyrico.data.song.tag.AudioTagMutationResolver
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepositoryImpl
import com.lonx.lyrico.data.song.tag.DefaultImageBytesFetcher
import com.lonx.lyrico.data.song.tag.ImageBytesFetcher
import com.lonx.lyrico.data.song.tag.ImageMimeTypeDetector
import com.lonx.lyrico.data.song.tag.PictureMutationResolver
import com.lonx.lyrico.data.song.tag.TagMapBuilder
import com.lonx.lyrico.ui.update.UpdatePromptCoordinator
import com.lonx.lyrico.diagnostics.ProblemReproductionRecorder
import com.lonx.lyrico.domain.song.usecase.DeleteSongsUseCase
import com.lonx.lyrico.domain.song.usecase.ReadAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.RenameSongUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.platform.audio.ReplayGainScanner
import com.lonx.lyrico.plugin.distribution.PluginCatalogRepository
import com.lonx.lyrico.plugin.distribution.PluginDistributionManager
import com.lonx.lyrico.plugin.i18n.PluginLocales
import com.lonx.lyrico.plugin.install.SourcePluginInstaller
import com.lonx.lyrico.plugin.runtime.HostAppInfo
import com.lonx.lyrico.plugin.runtime.QuickJsHostApi
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.lonx.lyrico.plugin.source.PluginSearchSourceManager
import com.lonx.lyrico.plugin.source.ScriptSearchSourceFactory
import com.lonx.lyrico.plugin.source.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.ui.SharedSelectionManager
import com.lonx.lyrico.ui.screens.about.AboutViewModel
import com.lonx.lyrico.ui.screens.album.AlbumActionsViewModel
import com.lonx.lyrico.ui.screens.album.AlbumDetailViewModel
import com.lonx.lyrico.ui.screens.artist.ArtistDetailViewModel
import com.lonx.lyrico.ui.screens.artist.ArtistSelectionViewModel
import com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterEditViewModel
import com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterFoldersViewModel
import com.lonx.lyrico.ui.screens.batch.BatchEditViewModel
import com.lonx.lyrico.ui.screens.batch.BatchExportViewModel
import com.lonx.lyrico.ui.screens.batch.BatchLyricsFormatViewModel
import com.lonx.lyrico.ui.screens.batch.BatchMatchViewModel
import com.lonx.lyrico.ui.screens.batch.BatchRenameViewModel
import com.lonx.lyrico.ui.screens.batch.BatchReplayGainViewModel
import com.lonx.lyrico.ui.screens.batch.BatchTaskDetailViewModel
import com.lonx.lyrico.ui.screens.batch.BatchTaskListViewModel
import com.lonx.lyrico.ui.screens.batch.CharacterMappingViewModel
import com.lonx.lyrico.ui.screens.diagnostics.ProblemReproductionViewModel
import com.lonx.lyrico.ui.screens.edit.EditMetadataViewModel
import com.lonx.lyrico.ui.screens.field.FieldSongsViewModel
import com.lonx.lyrico.ui.screens.folder.FolderManagerViewModel
import com.lonx.lyrico.ui.screens.library.AlbumLibraryViewModel
import com.lonx.lyrico.ui.screens.library.ArtistLibraryViewModel
import com.lonx.lyrico.ui.screens.library.SongListViewModel
import com.lonx.lyrico.ui.screens.library.SongSelectionViewModel
import com.lonx.lyrico.ui.screens.log.AppLogViewModel
import com.lonx.lyrico.ui.screens.plugin.PluginViewModel
import com.lonx.lyrico.ui.screens.plugin.SearchSourceConfigViewModel
import com.lonx.lyrico.ui.screens.search.CoverSearchViewModel
import com.lonx.lyrico.ui.screens.search.LocalSearchViewModel
import com.lonx.lyrico.ui.screens.search.LyricsSearchViewModel
import com.lonx.lyrico.ui.screens.search.SearchViewModel
import com.lonx.lyrico.ui.screens.settings.ArtistSplitSettingsViewModel
import com.lonx.lyrico.ui.screens.settings.EditFieldSettingsViewModel
import com.lonx.lyrico.ui.screens.settings.SettingsViewModel
import com.lonx.lyrico.ui.screens.settings.diagnostics.DiagnosticsSettingsViewModel
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.worker.processor.BatchExportProcessor
import com.lonx.lyrico.worker.processor.BatchTaskProcessorFactory
import com.lonx.lyrico.worker.processor.EditTagsProcessor
import com.lonx.lyrico.worker.processor.LyricsFormatProcessor
import com.lonx.lyrico.worker.processor.MatchCoverProcessor
import com.lonx.lyrico.worker.processor.MatchLyricsProcessor
import com.lonx.lyrico.worker.processor.MatchMetadataProcessor
import com.lonx.lyrico.worker.processor.RenameFilesProcessor
import com.lonx.lyrico.worker.processor.ReplayGainProcessor
import java.io.File
import java.util.concurrent.TimeUnit
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
        PluginLocales.initialize(context)
        ScriptSearchSourceFactory(
            json = get(),
            appLogRepository = get(),
            runtimeFactory = { plugin, strings ->
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
                        okHttpClient = okHttpClient,
                        pluginId = plugin.id,
                        pluginStrings = strings,
                        cacheRootDir = File(context.cacheDir, "plugin_cache")
                    )
                )
            }
        )
    }
    single { SourcePluginInstaller(repository = get(), json = get(), appLogRepository = get()) }
    single { PluginCatalogRepository(get<LyricoDatabase>().pluginSubscriptionDao()) }
    single { PluginDistributionManager(androidContext(), get(), get(), get(), get()) }
    single {
        PluginSearchSourceManager(
            repository = get(),
            factory = get(),
            installer = get(),
            appLogRepository = get()
        )
    }
    single { SearchSourceProvider(pluginManager = get()) }

    single { SearchSourceConfigApplier(get()) }

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { NetworkLoggingInterceptor(get(), get()) }
    // 工具类

    single { UpdatePromptCoordinator(get(), get()) }
    single { MediaScanner(androidContext()) }
    single { ReplayGainScanner(androidContext(), get()) }
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
                LyricoDatabase.MIGRATION_12_13,
                LyricoDatabase.MIGRATION_13_14,
                LyricoDatabase.MIGRATION_15_16,
                LyricoDatabase.MIGRATION_16_17,
                LyricoDatabase.MIGRATION_17_18,
                LyricoDatabase.MIGRATION_18_19,
                LyricoDatabase.MIGRATION_19_20,
                LyricoDatabase.MIGRATION_20_21,
                LyricoDatabase.MIGRATION_21_22,
                LyricoDatabase.MIGRATION_22_23
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    LyricoDatabase.configureLibraryIndexes(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    LyricoDatabase.configureLibraryIndexes(db)
                }
            })
            .build()
    }
    single { get<LyricoDatabase>().batchTaskDao() }
    single { get<LyricoDatabase>().appLogDao() }
    single { get<LyricoDatabase>().libraryIndexDao() }
    single { get<LyricoDatabase>().sourcePluginDao() }
    single { get<LyricoDatabase>().songCustomTagKeyDao() }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single { CustomTagKeyRepository(get()) }
    single { EditFieldConfigRepository(androidContext()) }
    single<UpdateRepository> { UpdateRepositoryImpl(get(), get()) }
    single { ExternalPlayerLauncher(get()) }
    single<LibraryIndexRepository> { LibraryIndexRepositoryImpl(get(), get<LyricoDatabase>().songDao(), get(), get()) }
    single { LibraryOperationLock() }
    single { LibraryFolderRepository(get(), get(), androidContext(), get(), get()) }
    single { CacheManager(androidContext(), get()) }
    single { AudioFileAccess(androidContext()) }
    single { ImageMimeTypeDetector() }
    single<ImageBytesFetcher> { DefaultImageBytesFetcher(get(), get()) }
    single { ArtworkDimensionsReader(get<OkHttpClient>()) }
    single { PictureMutationResolver(get(), get()) }
    single { TagMapBuilder() }
    single { AudioTagMutationResolver(get(), get()) }
    single<AudioTagRepository> { AudioTagRepositoryImpl(androidContext(), get(), get(), get()) }
    single { ArtistPosterReader(androidContext(), get(), get(), get()) }
    single<SongFileRepository> { SongFileRepositoryImpl(androidContext(), get(), get()) }
    single { SortKeyUpdater() }
    single { SongMetadataMapper(get()) }
    single<SongLibraryRepository> { SongLibraryRepositoryImpl(get(), get(), get(), get()) }
    single<SongSearchRepository> { SongSearchRepositoryImpl(get()) }
    single<LibraryScanRepository> {
        LibraryScanRepositoryImpl(androidContext(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single { ReadAudioTagsUseCase(get(), get()) }
    single { SaveAudioTagsUseCase(get(), get(), get()) }
    single { DeleteSongsUseCase(get(), get()) }
    single { RenameSongUseCase(get(), get(), get()) }
    single<SourcePluginRepository> { SourcePluginRepositoryImpl(get()) }
    single<LibraryScanManager> { LibraryScanManagerImpl(get(), get(), get(), get()) }
    single<BatchTaskRepository> { BatchTaskRepositoryImpl(get()) }
    single<AppLogRepository> { AppLogRepositoryImpl(get(), get(), get()) }
    single { ProblemReproductionRecorder(get(), get(), get(), get(), get()) }
    single<GitHubContributorRepository> { GitHubContributorRepositoryImpl(get(), get()) }
    single { BatchTaskScheduler(androidContext(), get()) }
    single { LyricsFormatProcessor(get(), get(), get()) }
    single { ReplayGainProcessor(get(), get(), get(), get(), get(), get()) }
    single { MatchMetadataProcessor(get(), get(), get(), get(), get()) }
    single { MatchLyricsProcessor(get(), get(), get(), get()) }
    single { MatchCoverProcessor(get(), get(), get(), get()) }
    single { RenameFilesProcessor(get(), get(), get()) }
    single { EditTagsProcessor(get(), get(), get()) }
    single { BatchExportProcessor(androidContext(), get()) }
    single { BatchTaskProcessorFactory(mapOf(
        BatchTaskType.CONVERT_LYRICS_FORMAT to get<LyricsFormatProcessor>(),
        BatchTaskType.SCAN_REPLAY_GAIN to get<ReplayGainProcessor>(),
        BatchTaskType.MATCH_METADATA to get<MatchMetadataProcessor>(),
        BatchTaskType.MATCH_LYRICS to get<MatchLyricsProcessor>(),
        BatchTaskType.MATCH_COVER to get<MatchCoverProcessor>(),
        BatchTaskType.RENAME_FILES to get<RenameFilesProcessor>(),
        BatchTaskType.EDIT_TAGS to get<EditTagsProcessor>(),
        BatchTaskType.EXPORT_LYRICS to get<BatchExportProcessor>(),
        BatchTaskType.EXPORT_COVER to get<BatchExportProcessor>()
    )) }
    // ViewModels
    viewModel { AboutViewModel(get(),get(), get()) }
    viewModel { AlbumActionsViewModel(get(), get(), get(), get(), get()) }
    viewModel { SongListViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SongSelectionViewModel(get(), get(), get(), get()) }
    viewModel { LocalSearchViewModel(get(), get(), get()) }
    viewModel { (albumId: Long) ->
        AlbumDetailViewModel(
            libraryIndexRepository = get(),
            albumId = albumId
        )
    }
    viewModel { (artistId: Long) ->
        ArtistDetailViewModel(
            libraryIndexRepository = get(),
            artistId = artistId,
            audioTagRepository = get(),
            saveAudioTagsUseCase = get(),
            settingsRepository = get(),
            application = get()
        )
    }
    viewModel { ArtistLibraryViewModel(get(), get(), get()) }
    viewModel { ArtistSelectionViewModel() }
    viewModel { ArtistSplitSettingsViewModel(get(), get()) }
    viewModel { AlbumLibraryViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), androidContext(), get()) }
    viewModel { DiagnosticsSettingsViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get(), get(), get(), get()) }
    viewModel { LyricsSearchViewModel(get(), get(), get()) }
    viewModel { CoverSearchViewModel(get(), get(), get(), get()) }
    viewModel { SearchSourceConfigViewModel(get(), get(), get()) }
    viewModel { EditMetadataViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { EditFieldSettingsViewModel(get(), get()) }
    viewModel { (fieldCode: String, component: Boolean) -> FieldSongsViewModel(fieldCode, component, get(), get(), get(), get()) }
    viewModel { BatchMatchViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AppLogViewModel(get(), get()) }
    viewModel { ProblemReproductionViewModel(get(), get(), get(), get()) }
    viewModel { PluginViewModel(get(), get(), get(), get(), get(), get()) }

    viewModel { FolderManagerViewModel(get(), get(), get(), get()) }
    viewModel { ArtistPosterFoldersViewModel(get(), get(), get()) }
    viewModel { ArtistPosterEditViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { BatchRenameViewModel(get(), get(), get(), get(), get()) }
    viewModel { CharacterMappingViewModel(get()) }
    viewModel { BatchExportViewModel(get(), get(), get()) }
    viewModel { BatchEditViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { BatchReplayGainViewModel(get(), get(), get(), get()) }
    viewModel { BatchLyricsFormatViewModel(get(), get(), get(), get()) }
    viewModel { (taskId: String) -> BatchTaskDetailViewModel(taskId, get(), get()) }
    viewModel { BatchTaskListViewModel(get(), get()) }
}

