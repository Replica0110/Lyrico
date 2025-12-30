package com.lonx.lyrics.source.qm


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

// 统一响应结构
@Serializable
data class QmUnifiedResponse(
    val code: Int = 0,
    val ts: Long = 0,
    val start_ts: Long = 0,
    // key 是模块名 (如 music.search.SearchCgiService)
    // value 是包含 code 和 data 的对象
    val req_0: QmModuleResponse? = null
)

@Serializable
data class QmModuleResponse(
    val code: Int = 0,
    val data: JsonObject // 使用 JsonObject 后续手动解析，因为不同接口结构差异巨大
)

// 请求体结构
@Serializable
data class QmRequestBody(
    val comm: Map<String, String>,
    val req_0: QmRequestModule // 简化为单请求
)

@Serializable
data class QmRequestModule(
    val method: String,
    val module: String,
    val param: Map<String, JsonElement> // 支持数字和字符串值
)

interface QmApi {
    @POST("cgi-bin/musicu.fcg")
    suspend fun request(@Body body: QmRequestBody): JsonObject // 返回完整 JSON 自行解析
}