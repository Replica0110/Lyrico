package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.dto.UpdateDTO
import java.io.IOException

sealed class UpdateCheckResult {
    /** 成功：发现了新版本 */
    data class NewVersion(val info: UpdateDTO) : UpdateCheckResult()

    /** 成功：当前已是最新版本 */
    object NoUpdateAvailable : UpdateCheckResult()

    /** 失败：网络连接问题 */
    data class NetworkError(val exception: IOException) : UpdateCheckResult()

    /** 失败：网络请求超时 */
    object TimeoutError : UpdateCheckResult()

    /** 失败：GitHub API 返回了错误（如 404, 403） */
    data class ApiError(val code: Int, val message: String) : UpdateCheckResult()

    /** 失败：无法解析返回的 JSON 数据 */
    object ParsingError : UpdateCheckResult()
}