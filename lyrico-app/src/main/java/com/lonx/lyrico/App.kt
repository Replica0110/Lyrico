package com.lonx.lyrico

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade
import com.lonx.lyrico.di.appModule
import com.lonx.lyrico.utils.coil.AudioCoverFetcher
import com.lonx.lyrico.utils.coil.AudioCoverKeyer
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.app.SaltApplication
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

@OptIn(UnstableSaltUiApi::class)
class App : SaltApplication(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        Log.d(TAG, "newImageLoader")
        return ImageLoader.Builder(context)
            .components {
                add(AudioCoverKeyer())
                add(AudioCoverFetcher.Factory(context.contentResolver))
            }
            .diskCache {
                // 磁盘缓存：最多 10 MB，目录为应用缓存目录
                DiskCache.Builder()
                    .maxSizeBytes(10 * 1024 * 1024)
                    .directory(context.cacheDir.resolve("image_cache"))
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        private const val TAG = "App"

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var context: Context
    }
}
