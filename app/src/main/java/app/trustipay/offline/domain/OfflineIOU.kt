package app.trustipay.offline.domain

import org.json.JSONObject

data class OfflineIOU(
    val tx_id: String,
    val sender_id: String,
    val receiver_id: String,
    val timestamp: String,
    val amount: Double,
    val category: String,
    val location: String,
    val device_id: String,
    val device_type: String = "android",
    val network_type: String = "offline",
    val nonce: Int,
    val prev_hash: String,
    val signature: String = ""
) {
    fun toJSONObject(): JSONObject = JSONObject().apply {
        put("tx_id", tx_id)
        put("sender_id", sender_id)
        put("receiver_id", receiver_id)
        put("timestamp", timestamp)
        put("amount", amount)
        put("category", category)
        put("location", location)
        put("device_id", device_id)
        put("device_type", device_type)
        put("network_type", network_type)
        put("nonce", nonce)
        put("prev_hash", prev_hash)
        if (signature.isNotEmpty()) {
            put("signature", signature)
        }
    }

    fun toJson(): String = toJSONObject().toString()

    /**
     * Minifies the IOU for QR code density optimization.
     * Uses short keys to reduce the payload size by ~40%.
     */
    fun toMinifiedJson(): String = JSONObject().apply {
        put("t", tx_id)
        put("s", sender_id)
        put("r", receiver_id)
        put("ts", timestamp)
        put("a", amount)
        put("c", category)
        put("l", location)
        put("d", device_id)
        put("dt", device_type)
        put("nt", network_type)
        put("n", nonce)
        put("p", prev_hash)
        if (signature.isNotEmpty()) put("sig", signature)
    }.toString()

    companion object {
        fun fromJson(json: String): OfflineIOU {
            val obj = JSONObject(json)
            // Support both full and minified formats
            return if (obj.has("t")) {
                OfflineIOU(
                    tx_id = obj.getString("t"),
                    sender_id = obj.getString("s"),
                    receiver_id = obj.getString("r"),
                    timestamp = obj.getString("ts"),
                    amount = obj.getDouble("a"),
                    category = obj.getString("c"),
                    location = obj.getString("l"),
                    device_id = obj.getString("d"),
                    device_type = obj.optString("dt", "android"),
                    network_type = obj.optString("nt", "offline"),
                    nonce = obj.getInt("n"),
                    prev_hash = obj.getString("p"),
                    signature = obj.optString("sig", "")
                )
            } else {
                OfflineIOU(
                    tx_id = obj.getString("tx_id"),
                    sender_id = obj.getString("sender_id"),
                    receiver_id = obj.getString("receiver_id"),
                    timestamp = obj.getString("timestamp"),
                    amount = obj.getDouble("amount"),
                    category = obj.getString("category"),
                    location = obj.getString("location"),
                    device_id = obj.getString("device_id"),
                    device_type = obj.optString("device_type", "android"),
                    network_type = obj.optString("network_type", "offline"),
                    nonce = obj.getInt("nonce"),
                    prev_hash = obj.getString("prev_hash"),
                    signature = obj.optString("signature", "")
                )
            }
        }
    }
}
