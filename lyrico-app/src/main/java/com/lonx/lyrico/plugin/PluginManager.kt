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

    private val _connectedLyricSources = MutableStateFlow<List<ILyricSource>>(emptyList())
    val connectedLyricSources: StateFlow<List<ILyricSource>> = _connectedLyricSources.asStateFlow()

    private val _connectedSearchSources = MutableStateFlow<List<ISearchSource>>(emptyList())
    val connectedSearchSources: StateFlow<List<ISearchSource>> = _connectedSearchSources.asStateFlow()

    // Map to keep track of connections and their associated interfaces for cleanup
    private val serviceConnections = mutableMapOf<ComponentName, ServiceConnection>()
    private val componentToPluginMap = mutableMapOf<ComponentName, Any>()

    companion object {
        private const val TAG = "PluginManager"
        private const val PERMISSION_SEARCH_SOURCE = "com.lonx.lyrico.permission.SEARCH_SOURCE"
        private const val ACTION_LYRIC_SOURCE = "com.lonx.lyrico.plugin.LyricSource"
        private const val ACTION_SEARCH_SOURCE = "com.lonx.lyrico.plugin.SearchSource"
    }

    fun startDiscovery() {
        Log.i(TAG, "Starting plugin discovery")
        discoverAndBind(ACTION_LYRIC_SOURCE) { binder -> ILyricSource.Stub.asInterface(binder) }
        discoverAndBind(ACTION_SEARCH_SOURCE) { binder -> ISearchSource.Stub.asInterface(binder) }
    }

    fun stopDiscovery() {
        Log.i(TAG, "Stopping plugin discovery and unbinding services")
        serviceConnections.values.forEach { context.unbindService(it) }
        serviceConnections.clear()
        componentToPluginMap.clear()
        _connectedLyricSources.value = emptyList()
        _connectedSearchSources.value = emptyList()
    }

    private fun <T : Any> discoverAndBind(action: String, asInterface: (IBinder) -> T) {
        val pm = context.packageManager
        val intent = Intent(action)
        // QUERY_RESULT_INTENT_DOCUMENT is not needed unless we process specifically? 
        // 0 is usually fine but GET_META_DATA or GET_RESOLVED_FILTER might be safer.
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
                    Log.i(TAG, "Service connected: $name")
                    try {
                        val interfaceObj = asInterface(service)
                        synchronized(this@PluginManager) {
                           componentToPluginMap[name] = interfaceObj
                           addPlugin(interfaceObj)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error casting plugin interface", e)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    Log.i(TAG, "Service disconnected: $name")
                    synchronized(this@PluginManager) {
                        val plugin = componentToPluginMap.remove(name)
                        if (plugin != null) {
                            removePlugin(plugin)
                        }
                        serviceConnections.remove(name)
                    }
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

    private fun addPlugin(plugin: Any) {
        when (plugin) {
            is ILyricSource -> {
                _connectedLyricSources.update { it + plugin }
            }
            is ISearchSource -> {
                _connectedSearchSources.update { it + plugin }
            }
        }
    }

    private fun removePlugin(plugin: Any) {
        when (plugin) {
            is ILyricSource -> {
                _connectedLyricSources.update { it - plugin }
            }
            is ISearchSource -> {
                _connectedSearchSources.update { it - plugin }
            }
        }
    }
}
