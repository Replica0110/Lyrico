package com.lonx.lyrico.plugin;

import com.lonx.lyrico.plugin.PluginInfo;
import com.lonx.lyrico.plugin.ConfigField;
import com.lonx.lyrico.plugin.ConfigDependency;
import android.os.Bundle;
import com.lonx.lyrico.plugin.SongSearchResult;

interface ILyricSource {

    String getLyrics(in SongSearchResult song);
}