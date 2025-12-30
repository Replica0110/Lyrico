package com.lonx.lyrico.di

import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.utils.SettingsManager
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrico.viewmodel.LocalSearchViewModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.qm.QmSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // 歌词源
    single { KgSource() }
    single { QmSource() }
    
    // 工具类
    single { SettingsManager(get()) }
    single { MusicScanner(androidContext()) }
    
    // 数据库和存储库
    single { LyricoDatabase.getInstance(androidContext()) }
    single { SongRepository(get(), androidContext(), get(), get()) }
    
    // ViewModels
    viewModel { SongListViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { EditMetadataViewModel(get(), androidContext()) }
    viewModel { LocalSearchViewModel(get()) }
}

