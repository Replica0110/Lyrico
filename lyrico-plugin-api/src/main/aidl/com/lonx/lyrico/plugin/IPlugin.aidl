package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.PluginInfo;
import com.lonx.lyrico.plugin.ConfigField;
import com.lonx.lyrico.plugin.ConfigDependency;
import android.os.Bundle;


interface IPlugin {
    PluginInfo getPluginInfo();
    List<String> getCapabilities(); // ["search", "lyric"]

    IBinder getCapability(String name);
    List<ConfigField> getConfigSchema();

    Bundle getSettings();

    void updateSettings(in Bundle settings);
}