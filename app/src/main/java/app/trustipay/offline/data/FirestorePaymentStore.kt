package app.trustipay.offline.data

import android.util.Log
import app.trustipay.offline.domain.OfflineIOU
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.withTimeoutOrNull
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
            // Use a short timeout for the cloud write. 
            // Firestore with local persistence enabled usually returns quickly,
            // but .await() can still hang if there are issues with the internal queue.
            withTimeoutOrNull(2000) {
                db.collection("offline_transactions")
                    .document(iou.tx_id)
                    .set(data)
                    .await()
            }
            Log.d("FirestorePaymentStore", "IOU save attempt finished (local or cloud)")
        } catch (e: Exception) {
            Log.e("FirestorePaymentStore", "Error saving IOU", e)
            // We don't rethrow here to prevent blocking the UI flow
        }
    }

    suspend fun getLastTransactionHash(deviceId: String): String {
        return try {
            // Simplified query to avoid composite index requirement (sender_id + timestamp)
            // We fetch by sender_id only and sort manually or use a simpler approach
            val result = db.collection("offline_transactions")
                .whereEqualTo("sender_id", deviceId)
                .get()
                .await()
            
            if (result.isEmpty) {
                "GENESIS"
            } else {
                // Find the latest one manually in memory to avoid index error
                val latestDoc = result.documents
                    .filter { it.contains("timestamp") }
                    .maxByOrNull { it.getString("timestamp") ?: "" }
                
                val signature = latestDoc?.getString("signature") ?: ""
                if (signature.isEmpty()) "GENESIS" else app.trustipay.offline.security.IOUCryptography.hash(signature)
            }
        } catch (e: Exception) {
            Log.e("FirestorePaymentStore", "Error getting last hash", e)
            "GENESIS"
        }
    }
}
