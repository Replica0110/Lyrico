package com.lonx.lyrico.di

import androidx.room.Room
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.utils.SettingsManager
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.ne.NeSource
import com.lonx.lyrics.source.qm.QmSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    // 歌词源
    single<SearchSource>(named("Kg")) { KgSource() }
    single<SearchSource>(named("Qm")) { QmSource() }
    single<SearchSource>(named("Ne")) { NeSource() }

    // getAll 会自动收集所有声明为 SearchSource 类型的实例（哪怕它们有不同的名称）
    single { getAll<SearchSource>() }
    // 工具类
    single { SettingsManager(get()) }
    single { MusicScanner(androidContext()) }
    
    // 数据库和存储库
    single {
        Room.databaseBuilder(
            androidContext(),
            LyricoDatabase::class.java,
            "lyrico_database"
        ).build()
    }

    single { SongRepository(get(), androidContext(), get(), get()) }
    
    // ViewModels
    viewModel { SongListViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { EditMetadataViewModel(get(), androidContext()) }
    viewModel { LocalSearchViewModel(get()) }
}

