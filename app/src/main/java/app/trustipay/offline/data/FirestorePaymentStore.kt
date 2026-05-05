package app.trustipay.offline.data

import android.util.Log
import app.trustipay.offline.domain.OfflineIOU
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.tasks.await

class FirestorePaymentStore {
    private val db = FirebaseFirestore.getInstance()

    init {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    suspend fun saveIOU(iou: OfflineIOU) {
        val data = mapOf(
            "tx_id" to iou.tx_id,
            "sender_id" to iou.sender_id,
            "receiver_id" to iou.receiver_id,
            "timestamp" to iou.timestamp,
            "amount" to iou.amount,
            "category" to iou.category,
            "location" to iou.location,
            "device_id" to iou.device_id,
            "device_type" to iou.device_type,
            "network_type" to iou.network_type,
            "nonce" to iou.nonce,
            "prev_hash" to iou.prev_hash,
            "signature" to iou.signature,
            "status" to "PENDING_SYNC"
        )

        try {
            db.collection("offline_transactions")
                .document(iou.tx_id)
                .set(data)
                .await()
            Log.d("FirestorePaymentStore", "IOU saved to Firestore (local cache)")
        } catch (e: Exception) {
            Log.e("FirestorePaymentStore", "Error saving IOU", e)
            throw e
        }
    }

    suspend fun getLastTransactionHash(deviceId: String): String {
        return try {
            val result = db.collection("offline_transactions")
                .whereEqualTo("sender_id", deviceId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            if (result.isEmpty) {
                "GENESIS"
            } else {
                val doc = result.documents[0]
                val signature = doc.getString("signature") ?: ""
                // Use the signature as the basis for the next hash
                app.trustipay.offline.security.IOUCryptography.hash(signature)
            }
        } catch (e: Exception) {
            Log.e("FirestorePaymentStore", "Error getting last hash", e)
            "GENESIS" // Fallback for prototype
        }
    }
}
