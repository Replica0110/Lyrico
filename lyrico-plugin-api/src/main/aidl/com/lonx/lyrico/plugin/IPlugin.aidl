package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.PluginInfo;
import com.lonx.lyrico.plugin.ConfigField;
import android.os.Bundle;


interface IPlugin {
    PluginInfo getPluginInfo();

    List<ConfigField> getConfigSchema();

    Bundle getSettings();

    void updateSettings(in Bundle settings);
}