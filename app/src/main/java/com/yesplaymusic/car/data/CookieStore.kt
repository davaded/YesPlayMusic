package com.yesplaymusic.car.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Cookie 持久化存储
 * 用于保存登录后的 cookie，实现持久登录
 */
class CookieStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存 cookie
     */
    fun saveCookie(cookie: String) {
        prefs.edit {
            putString(KEY_COOKIE, cookie)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
        }
    }

    /**
     * 获取 cookie
     */
    fun getCookie(): String? {
        return prefs.getString(KEY_COOKIE, null)
    }

    /**
     * 清除 cookie (退出登录)
     */
    fun clearCookie() {
        prefs.edit {
            remove(KEY_COOKIE)
            remove(KEY_LOGIN_TIME)
            remove(KEY_USER_ID)
            remove(KEY_USER_NICKNAME)
            remove(KEY_USER_AVATAR)
        }
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return !getCookie().isNullOrBlank()
    }

    /**
     * 保存用户信息
     */
    fun saveUserInfo(user: UserInfo) {
        prefs.edit {
            putLong(KEY_USER_ID, user.id)
            putString(KEY_USER_NICKNAME, user.nickname)
            putString(KEY_USER_AVATAR, user.avatarUrl)
            putInt(KEY_USER_VIP, user.vipType)
        }
    }

    /**
     * 获取缓存的用户信息
     */
    fun getCachedUserInfo(): UserInfo? {
        val userId = prefs.getLong(KEY_USER_ID, 0L)
        if (userId == 0L) return null
        return UserInfo(
            id = userId,
            nickname = prefs.getString(KEY_USER_NICKNAME, "") ?: "",
            avatarUrl = prefs.getString(KEY_USER_AVATAR, null),
            vipType = prefs.getInt(KEY_USER_VIP, 0)
        )
    }

    companion object {
        private const val PREFS_NAME = "yesplaymusic_auth"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_USER_VIP = "user_vip"

        @Volatile
        private var instance: CookieStore? = null

        fun getInstance(context: Context): CookieStore {
            return instance ?: synchronized(this) {
                instance ?: CookieStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
