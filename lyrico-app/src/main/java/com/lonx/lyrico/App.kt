package com.lonx.lyrico

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lonx.lyrico.di.appModule
import com.lonx.lyrico.utils.coil.AudioCoverFetcher
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application(), ImageLoaderFactory {
    companion object {
        @JvmStatic
        lateinit var context: App
    }
    override fun onCreate() {
        super.onCreate()
        context = this
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioCoverFetcher.Factory(this@App))
            }
            .respectCacheHeaders(false) // Allow caching of content:// uris
            .build()
    }
}