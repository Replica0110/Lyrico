package com.lonx.lyrico.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PluginManager(private val context: Context) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PERMISSION_SEARCH_SOURCE = "com.lonx.lyrico.permission.SEARCH_SOURCE"
        private const val ACTION_PLUGIN = "com.lonx.lyrico.plugin.PLUGIN"
    }

    private val serviceConnections = mutableMapOf<ComponentName, ServiceConnection>()
    private val componentToPlugin = mutableMapOf<ComponentName, IPlugin>()

    private val _plugins = MutableStateFlow<List<PluginHandle>>(emptyList())
    val plugins: StateFlow<List<PluginHandle>> = _plugins.asStateFlow()

    private val _searchSources = MutableStateFlow<List<ISearchSource>>(emptyList())

    private val _lyricSources = MutableStateFlow<List<ILyricSource>>(emptyList())

    fun startDiscovery() {
        val pm = context.packageManager
        val intent = Intent(ACTION_PLUGIN)
        val services = pm.queryIntentServices(intent, 0)

        for (resolveInfo in services) {
            Log.i(TAG, "Found plugin: $resolveInfo")
            val serviceInfo = resolveInfo.serviceInfo
            val packageName = serviceInfo.packageName
            val className = serviceInfo.name

            // Check signature permission
            if (pm.checkPermission(PERMISSION_SEARCH_SOURCE, packageName) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Skipping plugin $packageName: Missing required permission")
                continue
            }

            val componentName = ComponentName(packageName, className)
            if (serviceConnections.containsKey(componentName)) {
                continue // Already bound
            }

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val plugin = IPlugin.Stub.asInterface(service)
                    componentToPlugin[name] = plugin
                    registerPlugin(name.packageName, plugin)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    val plugin = componentToPlugin.remove(name)
                    plugin?.let { unregisterPlugin(it) }
                    serviceConnections.remove(name)
                }
            }

            val bindIntent = Intent().apply { component = componentName }
            val bound = context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
            if (bound) {
                serviceConnections[componentName] = connection
            } else {
                Log.e(TAG, "Failed to bind to service: $componentName")
            }
        }
    }

    fun stopDiscovery() {
        serviceConnections.values.forEach { context.unbindService(it) }
        serviceConnections.clear()
        componentToPlugin.clear()
        _plugins.value = emptyList()
        _searchSources.value = emptyList()
        _lyricSources.value = emptyList()
    }

    private fun registerPlugin(
        packageName: String,
        plugin: IPlugin
    ) {
        val caps = plugin.capabilities

        var search: ISearchSource? = null
        var lyric: ILyricSource? = null

        if ("search" in caps) {
            search = ISearchSource.Stub.asInterface(plugin.getCapability("search"))
            _searchSources.update { it + search }
        }

        if ("lyric" in caps) {
            lyric = ILyricSource.Stub.asInterface(plugin.getCapability("lyric"))
            _lyricSources.update { it + lyric }
        }

        val handle = PluginHandle(packageName, plugin, search, lyric)
        _plugins.update { it + handle }
    }

    private fun unregisterPlugin(plugin: IPlugin) {
        val handle = _plugins.value.find { it.plugin == plugin } ?: return

        handle.search?.let { s ->
            _searchSources.update { it - s }
        }
        handle.lyric?.let { l ->
            _lyricSources.update { it - l }
        }

        _plugins.update { it - handle }
    }
}
data class PluginHandle(
    val packageName: String,
    val plugin: IPlugin,
    val search: ISearchSource?,
    val lyric: ILyricSource?
) {
    val info: PluginInfo get() = plugin.pluginInfo

}