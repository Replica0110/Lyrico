package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.SongSearchResult;
import com.lonx.lyrico.plugin.PluginInfo;

interface ISearchSource {

    PluginInfo getPluginInfo();

    List<SongSearchResult> search(String keyword,int page,int pageSize);
}
