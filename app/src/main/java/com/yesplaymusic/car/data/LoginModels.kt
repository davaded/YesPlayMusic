package com.yesplaymusic.car.data

/**
 * 二维码扫码状态
 */
enum class QrStatus(val code: Int) {
    EXPIRED(800),      // 二维码过期
    WAITING(801),      // 等待扫码
    CONFIRMING(802),   // 待确认
    SUCCESS(803);      // 授权成功

    companion object {
        fun fromCode(code: Int): QrStatus = values().find { it.code == code } ?: EXPIRED
    }
}

/**
 * 二维码扫码结果
 */
data class QrCheckResult(
    val status: QrStatus,
    val cookie: String? = null,
    val message: String? = null
)

/**
 * 用户信息
 */
data class UserInfo(
    val id: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Int = 0
)

/**
 * 登录状态
 */
data class LoginStatus(
    val isLoggedIn: Boolean,
    val user: UserInfo? = null
)
