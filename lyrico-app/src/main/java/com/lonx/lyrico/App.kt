package com.lonx.lyrico

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.di.appModule
import com.lonx.lyrico.utils.coil.AudioCoverFetcher
import com.lonx.lyrico.utils.coil.AudioCoverKeyer
import com.moriafly.salt.ui.app.SaltApplication
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.system.exitProcess

class App : SaltApplication(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@App)
            modules(appModule)
        }

        installCrashLogger()

        CoroutineScope(Dispatchers.Main.immediate).launch {
            val settingsRepository = org.koin.core.context.GlobalContext.get().get<SettingsRepository>()
            settingsRepository.themeMode.distinctUntilChanged().collect { mode ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Persist the override so the system can theme the next cold-start splash.
                    // AUTO clears the app override and restores the system configuration.
                    getSystemService(UiModeManager::class.java).setApplicationNightMode(
                        when (mode) {
                            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                            ThemeMode.AUTO -> UiModeManager.MODE_NIGHT_AUTO
                        }
                    )
                } else {
                    AppCompatDelegate.setDefaultNightMode(
                        when (mode) {
                            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                            ThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val logRepository = org.koin.core.context.GlobalContext.get().get<AppLogRepository>()
            logRepository.trim()
//            logRepository.log(
//                level = AppLogLevel.INFO,
//                type = AppLogType.APP,
//                tag = TAG,
//                message = "Application started"
//            )
            val repo = org.koin.core.context.GlobalContext.get().get<BatchTaskRepository>()
            repo.markOrphanedTasksFailed()
        }
    }

    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                runBlocking(Dispatchers.IO) {
                    val logRepository = org.koin.core.context.GlobalContext.get().get<AppLogRepository>()
                    logRepository.logException(
                        type = AppLogType.CRASH,
                        tag = TAG,
                        message = "Uncaught exception on ${thread.name}",
                        throwable = throwable
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist crash log", e)
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        Log.d(TAG, "newImageLoader")
        return ImageLoader.Builder(context)
            .components {
                add(AudioCoverKeyer())
                add(com.lonx.lyrico.utils.coil.PosterFileFetcherFactory())
                add(AudioCoverFetcher.Factory(
                    context.contentResolver,
                    org.koin.core.context.GlobalContext.get().get()
                ))
            }
            .diskCache {
                DiskCache.Builder()
                    .maxSizeBytes(50L * 1024 * 1024)
                    .directory(context.cacheDir.resolve("image_cache"))
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        private const val TAG = "App"
        const val ACTION_EDIT_TAG = "com.lonx.lyrico.action.EDIT_TAG"
        const val TELEGRAM_GROUP_LINK = "https://t.me/lyrico_app"
        const val OWNER_ID = "Replica0110"
        const val REPO_NAME = "Lyrico"

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var context: Context
    }
}
