package app.trustipay.offline.sync

class OfflineSyncWorker(
    private val syncRepository: SyncRepository,
) {
    fun runOnce(): SyncRunSummary = syncRepository.processShadowSync()
}
