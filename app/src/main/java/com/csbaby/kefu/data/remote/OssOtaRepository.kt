package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager

/**
 * OSS OTA仓库
 */
class OssOtaRepository(
    private val ossManager: AliyunOssManager,
    private val directChecker: DirectOssVersionChecker
) {
    /**
     * 检查OSS上的更新
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<OtaUpdate?> {
        return directChecker.checkForUpdate(currentVersionCode)
    }
}
