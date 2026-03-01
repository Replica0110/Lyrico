package com.lonx.lyrico.plugin

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface ConfigDependency : Parcelable {

    // 基础条件：检查 key 是否等于 value
    @Parcelize
    data class Match(val key: String, val value: String) : ConfigDependency

    // AND 逻辑：必须满足列表里【所有】条件
    @Parcelize
    data class And(val conditions: List<ConfigDependency>) : ConfigDependency

    // OR 逻辑：满足列表里【任意】条件即可
    @Parcelize
    data class Or(val conditions: List<ConfigDependency>) : ConfigDependency
    @Parcelize
    data class Not(val condition: ConfigDependency) : ConfigDependency
}