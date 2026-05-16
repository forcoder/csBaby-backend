package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String = "",
    @SerializedName("name") val name: String = ""
)

data class RegisterResponse(
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0
)

data class HeartbeatResponse(
    @SerializedName("status") val status: String = ""
)
