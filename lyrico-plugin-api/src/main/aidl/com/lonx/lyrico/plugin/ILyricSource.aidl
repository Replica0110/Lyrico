package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.PluginInfo;
import com.lonx.lyrico.plugin.SongSearchResult;

interface ILyricSource {

    PluginInfo getPluginInfo();

    String getLyrics(in SongSearchResult song);
}