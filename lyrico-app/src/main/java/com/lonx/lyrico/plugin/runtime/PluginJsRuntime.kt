package com.lonx.lyrico.plugin.runtime

interface PluginJsRuntime : AutoCloseable {
    fun resetCancellation() {}
    fun cancelInvocation() {}
    fun eval(script: String, filename: String = "<eval>"): String
    fun call(functionName: String, requestJson: String): String
}
