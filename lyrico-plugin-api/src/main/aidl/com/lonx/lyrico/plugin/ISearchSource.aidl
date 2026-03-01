package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.SongSearchResult;
import com.lonx.lyrico.plugin.ConfigField;
import com.lonx.lyrico.plugin.ConfigDependency;
import android.os.Bundle;
import com.lonx.lyrico.plugin.PluginInfo;

interface ISearchSource {
    List<SongSearchResult> search(String keyword,int page,int pageSize);
}
