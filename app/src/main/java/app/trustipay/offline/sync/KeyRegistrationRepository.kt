package app.trustipay.offline.sync

import android.content.Context
import android.content.SharedPreferences
import app.trustipay.api.ApiResult
import app.trustipay.api.TrustiPayApiService
import app.trustipay.api.dto.DeviceRegistrationRequest
import app.trustipay.api.safeApiCall
import app.trustipay.offline.security.DeviceKeyManager
import java.util.Base64
import java.util.UUID

class KeyRegistrationRepository(
    context: Context,
    private val apiService: TrustiPayApiService,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager(),
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("trustipay_device_prefs", Context.MODE_PRIVATE)

    suspend fun ensureDeviceRegistered(userId: String): ApiResult<Unit> {
        val prefKey = "device_registered_$userId"
        if (prefs.getBoolean(prefKey, false)) return ApiResult.Success(Unit)

        val publicKeyId = deviceKeyManager.getPublicKeyId()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(deviceKeyManager.getPublicKeyBytes())

        val result = safeApiCall {
            apiService.registerDevice(
                DeviceRegistrationRequest(
                    deviceId = publicKeyId,
                    deviceName = "Android offline wallet",
                    publicSigningKey = publicKeyBase64,
                ),
                idempotencyKey = UUID.nameUUIDFromBytes("register:$userId:$publicKeyId".toByteArray()).toString(),
            )
        }

        if (result is ApiResult.Success) {
            prefs.edit().putBoolean(prefKey, true).apply()
        }

        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            else -> @Suppress("UNCHECKED_CAST") (result as ApiResult<Unit>)
        }
    }
}
