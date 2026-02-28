package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.SongSearchResult;
import com.lonx.lyrico.plugin.ConfigField;
import android.os.Bundle;
import com.lonx.lyrico.plugin.PluginInfo;

interface ISearchSource {
    PluginInfo getPluginInfo();

    List<ConfigField> getConfigSchema();

    Bundle getSettings();

    void updateSettings(in Bundle settings);
    List<SongSearchResult> search(String keyword,int page,int pageSize);
}
