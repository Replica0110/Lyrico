package com.lonx.lyrico.plugin.runtime

interface PluginJsRuntime : AutoCloseable {
    fun eval(script: String, filename: String = "<eval>"): String
    fun call(functionName: String, requestJson: String): String
}
