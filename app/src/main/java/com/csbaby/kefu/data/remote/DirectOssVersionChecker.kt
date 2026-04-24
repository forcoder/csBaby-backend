package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager

/**
 * 直接OSS版本检查器
 */
class DirectOssVersionChecker(
    private val ossManager: AliyunOssManager
) {
    companion object {
        const val VERSION_MANIFEST_FILE = "version.json"
    }

    /**
     * 检查OSS上的更新
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return Result.success(null)
    }
}
