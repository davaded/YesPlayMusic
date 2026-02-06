package com.yesplaymusic.car.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yesplaymusic.car.R
import com.yesplaymusic.car.data.CookieStore
import com.yesplaymusic.car.data.ProviderRegistry
import com.yesplaymusic.car.data.QrStatus
import com.yesplaymusic.car.databinding.FragmentLoginBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    companion object {
        private const val TAG = "LoginFragment"
    }

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val provider = ProviderRegistry.get()
    private lateinit var cookieStore: CookieStore
    private var currentQrKey: String? = null
    private var pollJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cookieStore = CookieStore.getInstance(requireContext())

        binding.refreshQrButton.setOnClickListener {
            generateQrCode()
        }

        binding.skipLoginButton.setOnClickListener {
            (activity as? LoginCallback)?.onLoginSkipped()
        }

        generateQrCode()
    }

    private fun generateQrCode() {
        pollJob?.cancel()
        binding.qrLoading.visibility = View.VISIBLE
        binding.qrCodeImage.setImageDrawable(null)
        binding.qrExpiredOverlay.visibility = View.GONE
        binding.loginStatus.text = getString(R.string.loading)

        Log.d(TAG, "开始生成二维码...")

        viewLifecycleOwner.lifecycleScope.launch {
            val key = provider.getQrKey()
            if (key == null) {
                Log.e(TAG, "获取二维码 key 失败")
                showError()
                return@launch
            }
            currentQrKey = key
            Log.d(TAG, "获取二维码 key 成功: $key")

            val qrBase64 = provider.createQrCode(key)
            if (qrBase64 == null) {
                Log.e(TAG, "生成二维码图片失败")
                showError()
                return@launch
            }
            Log.d(TAG, "二维码图片生成成功")

            // 解码 base64 图片
            val base64Data = qrBase64.substringAfter("base64,")
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            binding.qrLoading.visibility = View.GONE
            binding.qrCodeImage.setImageBitmap(bitmap)
            binding.loginStatus.text = getString(R.string.login_waiting)

            startPolling(key)
        }
    }

    private fun startPolling(key: String) {
        pollJob?.cancel()
        Log.d(TAG, "开始轮询扫码状态, key: $key")
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(2000) // 每2秒检查一次
                val result = provider.checkQrStatus(key)
                Log.d(TAG, "扫码状态: ${result.status}, message: ${result.message}")

                when (result.status) {
                    QrStatus.EXPIRED -> {
                        Log.w(TAG, "二维码已过期")
                        binding.qrExpiredOverlay.visibility = View.VISIBLE
                        binding.loginStatus.text = getString(R.string.login_qr_expired)
                        break
                    }
                    QrStatus.WAITING -> {
                        binding.loginStatus.text = getString(R.string.login_waiting)
                    }
                    QrStatus.CONFIRMING -> {
                        Log.d(TAG, "等待用户确认登录...")
                        binding.loginStatus.text = getString(R.string.login_confirming)
                    }
                    QrStatus.SUCCESS -> {
                        Log.i(TAG, "扫码登录成功!")
                        binding.loginStatus.text = getString(R.string.login_success)
                        result.cookie?.let { cookie ->
                            onLoginSuccess(cookie)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun onLoginSuccess(cookie: String) {
        Log.d(TAG, "保存 cookie: ${cookie.take(50)}...")
        cookieStore.saveCookie(cookie)
        provider.setCookie(cookie)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 获取用户信息
                Log.d(TAG, "获取用户信息...")
                val status = provider.getLoginStatus()
                Log.d(TAG, "getLoginStatus 返回: isLoggedIn=${status.isLoggedIn}, user=${status.user}")
                status.user?.let { user ->
                    Log.i(TAG, "用户信息获取成功: id=${user.id}, nickname=${user.nickname}, avatarUrl=${user.avatarUrl?.take(50)}")
                    cookieStore.saveUserInfo(user)
                } ?: Log.w(TAG, "未能获取用户信息")

                Log.d(TAG, "触发 onLoginSuccess 回调")
                (activity as? LoginCallback)?.onLoginSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "获取用户信息异常: ${e.message}", e)
                // 即使获取用户信息失败，也触发回调让用户进入主页
                (activity as? LoginCallback)?.onLoginSuccess()
            }
        }
    }

    private fun showError() {
        binding.qrLoading.visibility = View.GONE
        binding.loginStatus.text = getString(R.string.login_failed)
        binding.qrExpiredOverlay.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollJob?.cancel()
        _binding = null
    }

    interface LoginCallback {
        fun onLoginSuccess()
        fun onLoginSkipped()
    }
}
