package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AccountRole
import com.example.model.AdminAuditLog
import com.example.model.BotCommand
import com.example.model.CommandCategory
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.EngagementEvent
import com.example.model.GlobalFleetState
import com.example.model.MediaJob
import com.example.model.OperatingMode
import com.example.model.OwnerPolicy
import com.example.model.PlatformIntegration
import com.example.model.PlatformKey
import com.example.model.PlatformStatus
import com.example.model.TargetType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BotFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Owner must be signed in with Google before accessing Firestore.")

    private fun <T> wrapSnapshotFlow(
        path: String,
        op: OperationType,
        flowFactory: (String) -> Flow<T>
    ): Flow<T> = flow {
        val uid = requireUserId()
        emitAll(
            flowFactory(uid).catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, op, path)
                }
                throw error
            }
        )
    }

    fun observeOwnerPolicy(): Flow<OwnerPolicy> =
        wrapSnapshotFlow("users/{uid}/settings/owner_policy", OperationType.GET) { uid ->
            db.collection("users").document(uid)
                .collection("settings").document("owner_policy")
                .snapshots()
                .map { snap ->
                    snap.toObject(
                        OwnerPolicy::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: OwnerPolicy(id = "owner_policy", ownerUid = uid)
                }
        }

    fun observeAccounts(): Flow<List<ConnectedAccount>> =
        wrapSnapshotFlow("users/{uid}/accounts", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("accounts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ConnectedAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    fun observeCommands(): Flow<List<BotCommand>> =
        wrapSnapshotFlow("users/{uid}/commands", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("commands")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        BotCommand::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    fun observeMediaJobs(): Flow<List<MediaJob>> =
        wrapSnapshotFlow("users/{uid}/mediaJobs", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("mediaJobs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        MediaJob::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    fun observePlatforms(): Flow<List<PlatformIntegration>> =
        wrapSnapshotFlow("users/{uid}/platforms", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("platforms")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlatformIntegration::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    fun observeEvents(): Flow<List<EngagementEvent>> =
        wrapSnapshotFlow("users/{uid}/events", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("events")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        EngagementEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    fun observeAuditLogs(): Flow<List<AdminAuditLog>> =
        wrapSnapshotFlow("users/{uid}/auditLogs", OperationType.LIST) { uid ->
            db.collection("users").document(uid)
                .collection("auditLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AdminAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }

    suspend fun ensureDefaultsSeeded(
        defaultFingerprint: String,
        defaultPairingCode: String,
        waConfigured: Boolean,
        mediaExtractorConfigured: Boolean
    ): Result<Unit> = runSafe(OperationType.WRITE, "users/{uid}/seed") { uid ->
        val settingsRef = db.collection("users").document(uid)
            .collection("settings").document("owner_policy")
        if (!settingsRef.get().await().exists()) {
            settingsRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "globalFleetState" to GlobalFleetState.ONLINE.name,
                    "maintenanceMessage" to "Honest Bot Pro is undergoing scheduled maintenance. Support: +263 780549558",
                    "allowNewLinkRequests" to true,
                    "defaultDailyQuota" to 250L,
                    "maxMediaSizeMb" to 16L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val commandsCol = db.collection("users").document(uid).collection("commands")
        val existingCmdIds = commandsCol.get().await().documents.map { it.id }.toSet()
        defaultCommands().forEach { cmd ->
            if (cmd.id !in existingCmdIds) {
                commandsCol.document(cmd.id).set(
                    mapOf(
                        "ownerUid" to uid,
                        "trigger" to cmd.trigger,
                        "title" to cmd.title,
                        "description" to cmd.description,
                        "category" to cmd.category,
                        "promptTemplate" to cmd.promptTemplate,
                        "usesWebSearch" to cmd.usesWebSearch,
                        "commandEnabled" to cmd.commandEnabled,
                        "executionCount" to 0L,
                        "aliases" to cmd.aliases,
                        "ownerOnly" to cmd.ownerOnly,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val platformsCol = db.collection("users").document(uid).collection("platforms")
        val existingPlatIds = platformsCol.get().await().documents.map { it.id }.toSet()
        defaultPlatforms(waConfigured, mediaExtractorConfigured).forEach { plat ->
            if (plat.id !in existingPlatIds) {
                platformsCol.document(plat.id).set(
                    mapOf(
                        "ownerUid" to uid,
                        "platformKey" to plat.platformKey,
                        "name" to plat.name,
                        "status" to plat.status,
                        "endpointOrHandle" to plat.endpointOrHandle,
                        "autoSyncEnabled" to plat.autoSyncEnabled,
                        "webhookSecretHash" to plat.webhookSecretHash,
                        "lastHeartbeatAtMs" to System.currentTimeMillis(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val accountsCol = db.collection("users").document(uid).collection("accounts")
        if (accountsCol.limit(1).get().await().isEmpty) {
            accountsCol.document("acc_honest_support").set(
                mapOf(
                    "ownerUid" to uid,
                    "targetType" to TargetType.PERSONAL_INBOX.name,
                    "countryCode" to "+263",
                    "phoneNumber" to "780549558",
                    "e164Number" to "+263780549558",
                    "displayName" to "Honest Tech Support (Owner)",
                    "pairingCode" to defaultPairingCode,
                    "sessionFingerprint" to defaultFingerprint,
                    "pairingExpiresAtMs" to (System.currentTimeMillis() + 86_400_000L),
                    "connectionStatus" to ConnectionStatus.LIVE.name,
                    "operatingMode" to OperatingMode.FULL_AI_MEDIA.name,
                    "customPhoneNumberId" to "",
                    "role" to AccountRole.VIP.name,
                    "botEnabled" to true,
                    "autoReplyEnabled" to true,
                    "mediaDownloadEnabled" to true,
                    "webSearchEnabled" to true,
                    "groupDispatchEnabled" to true,
                    "blockedCommands" to emptyList<String>(),
                    "dailyQuota" to 5000L,
                    "messagesUsedToday" to 0L,
                    "lastActiveAtMs" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateGlobalPolicy(
        fleetState: GlobalFleetState,
        maintenanceMessage: String,
        allowNewLinkRequests: Boolean,
        defaultDailyQuota: Long,
        maxMediaSizeMb: Long
    ): Result<Unit> = runSafe(OperationType.UPDATE, "users/{uid}/settings/owner_policy") { uid ->
        val docRef = db.collection("users").document(uid)
            .collection("settings").document("owner_policy")
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "globalFleetState" to fleetState.name,
                    "maintenanceMessage" to maintenanceMessage.take(300),
                    "allowNewLinkRequests" to allowNewLinkRequests,
                    "defaultDailyQuota" to defaultDailyQuota.coerceIn(1L, 100000L),
                    "maxMediaSizeMb" to maxMediaSizeMb.coerceIn(1L, 100L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "globalFleetState" to fleetState.name,
                    "maintenanceMessage" to maintenanceMessage.take(300),
                    "allowNewLinkRequests" to allowNewLinkRequests,
                    "defaultDailyQuota" to defaultDailyQuota.coerceIn(1L, 100000L),
                    "maxMediaSizeMb" to maxMediaSizeMb.coerceIn(1L, 100L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateFullOwnerPolicy(policy: OwnerPolicy): Result<Unit> =
        runSafe(OperationType.UPDATE, "users/{uid}/settings/owner_policy") { uid ->
            val docRef = db.collection("users").document(uid)
                .collection("settings").document("owner_policy")
            val payload = mutableMapOf<String, Any>(
                "globalFleetState" to policy.globalFleetState,
                "maintenanceMessage" to policy.maintenanceMessage.take(300),
                "allowNewLinkRequests" to policy.allowNewLinkRequests,
                "defaultDailyQuota" to policy.defaultDailyQuota.coerceIn(1L, 100000L),
                "maxMediaSizeMb" to policy.maxMediaSizeMb.coerceIn(1L, 100L),
                "botName" to policy.botName.ifBlank { "Honest Bot Pro" }.take(64),
                "commandPrefix" to policy.commandPrefix.ifBlank { "." }.take(4),
                "botMode" to policy.botMode.takeIf { it in listOf("public", "private", "inbox", "groups") }.orEmpty().ifBlank { "public" },
                "webPairBridgeUrl" to policy.webPairBridgeUrl.trim().take(500),
                "sessionId" to policy.sessionId.trim().take(500),
                "readMessages" to policy.readMessages,
                "alwaysOnline" to policy.alwaysOnline,
                "autoTyping" to policy.autoTyping.takeIf { it in listOf("off", "all", "inbox", "group") }.orEmpty().ifBlank { "off" },
                "autoRecording" to policy.autoRecording.takeIf { it in listOf("off", "all", "inbox", "group") }.orEmpty().ifBlank { "off" },
                "autoReact" to policy.autoReact,
                "ownerReact" to policy.ownerReact,
                "autoViewStatus" to policy.autoViewStatus,
                "autoLikeStatus" to policy.autoLikeStatus,
                "autoDownloader" to policy.autoDownloader,
                "antiLinkMode" to policy.antiLinkMode.takeIf { it in listOf("off", "warn", "delete") }.orEmpty().ifBlank { "delete" },
                "antiDeletePath" to policy.antiDeletePath.takeIf { it in listOf("off", "inbox", "group", "all") }.orEmpty().ifBlank { "inbox" },
                "antiEditPath" to policy.antiEditPath.takeIf { it in listOf("off", "inbox", "group", "all") }.orEmpty().ifBlank { "inbox" },
                "antiCallEnabled" to policy.antiCallEnabled,
                "callRejectMessage" to policy.callRejectMessage.take(300),
                "antiStatusEnabled" to policy.antiStatusEnabled,
                "welcomeEnabled" to policy.welcomeEnabled,
                "goodbyeEnabled" to policy.goodbyeEnabled,
                "welcomeMessage" to policy.welcomeMessage.take(400),
                "goodbyeMessage" to policy.goodbyeMessage.take(400),
                "sudoNumbers" to policy.sudoNumbers.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(50),
                "bannedNumbers" to policy.bannedNumbers.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!docRef.get().await().exists()) {
                payload["ownerUid"] = uid
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.update(payload).await()
            }
        }

    suspend fun createLinkedAccount(account: ConnectedAccount): Result<String> =
        runSafe(OperationType.CREATE, "users/{uid}/accounts") { uid ->
            val docId = "acc_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val docRef = db.collection("users").document(uid).collection("accounts").document(docId)
            docRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "targetType" to account.targetType,
                    "countryCode" to account.countryCode.take(8),
                    "phoneNumber" to account.phoneNumber.take(64),
                    "e164Number" to account.e164Number.take(80),
                    "displayName" to account.displayName.take(80),
                    "pairingCode" to account.pairingCode.take(24),
                    "sessionFingerprint" to account.sessionFingerprint.take(128),
                    "pairingExpiresAtMs" to account.pairingExpiresAtMs,
                    "connectionStatus" to account.connectionStatus,
                    "operatingMode" to account.operatingMode,
                    "customPhoneNumberId" to account.customPhoneNumberId.take(64),
                    "role" to account.role,
                    "botEnabled" to account.botEnabled,
                    "autoReplyEnabled" to account.autoReplyEnabled,
                    "mediaDownloadEnabled" to account.mediaDownloadEnabled,
                    "webSearchEnabled" to account.webSearchEnabled,
                    "groupDispatchEnabled" to account.groupDispatchEnabled,
                    "blockedCommands" to account.blockedCommands.take(25),
                    "dailyQuota" to account.dailyQuota.coerceIn(0L, 100000L),
                    "messagesUsedToday" to 0L,
                    "lastActiveAtMs" to System.currentTimeMillis(),
                    "linkingMethod" to account.linkingMethod,
                    "sessionId" to account.sessionId.take(500),
                    "antiLinkEnabled" to account.antiLinkEnabled,
                    "antiDeleteEnabled" to account.antiDeleteEnabled,
                    "welcomeEnabled" to account.welcomeEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        }

    suspend fun updateAccountFields(accountId: String, updates: Map<String, Any>): Result<Unit> =
        runSafe(OperationType.UPDATE, "users/{uid}/accounts/$accountId") { uid ->
            require(accountId.isNotBlank())
            val sanitized = updates.toMutableMap()
            sanitized["updatedAt"] = FieldValue.serverTimestamp()
            db.collection("users").document(uid)
                .collection("accounts").document(accountId)
                .update(sanitized)
                .await()
        }

    suspend fun deleteAccount(accountId: String): Result<Unit> =
        runSafe(OperationType.DELETE, "users/{uid}/accounts/$accountId") { uid ->
            require(accountId.isNotBlank())
            db.collection("users").document(uid)
                .collection("accounts").document(accountId)
                .delete()
                .await()
        }

    suspend fun incrementAccountUsage(accountId: String): Result<Unit> =
        runSafe(OperationType.UPDATE, "users/{uid}/accounts/$accountId") { uid ->
            db.collection("users").document(uid)
                .collection("accounts").document(accountId)
                .update(
                    mapOf(
                        "messagesUsedToday" to FieldValue.increment(1L),
                        "lastActiveAtMs" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }

    suspend fun saveCustomCommand(command: BotCommand): Result<Unit> =
        runSafe(OperationType.CREATE, "users/{uid}/commands") { uid ->
            val docId = if (command.id.isNotBlank()) command.id else "cmd_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            db.collection("users").document(uid).collection("commands").document(docId).set(
                mapOf(
                    "ownerUid" to uid,
                    "trigger" to command.trigger.take(32),
                    "title" to command.title.take(80),
                    "description" to command.description.take(240),
                    "category" to command.category,
                    "promptTemplate" to command.promptTemplate.take(1000),
                    "usesWebSearch" to command.usesWebSearch,
                    "commandEnabled" to command.commandEnabled,
                    "executionCount" to command.executionCount.coerceAtLeast(0L),
                    "aliases" to command.aliases.take(15),
                    "ownerOnly" to command.ownerOnly,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

    suspend fun toggleCommandEnabled(commandId: String, enabled: Boolean): Result<Unit> =
        runSafe(OperationType.UPDATE, "users/{uid}/commands/$commandId") { uid ->
            db.collection("users").document(uid)
                .collection("commands").document(commandId)
                .update(
                    mapOf(
                        "commandEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }

    suspend fun incrementCommandCount(commandId: String): Result<Unit> =
        runSafe(OperationType.UPDATE, "users/{uid}/commands/$commandId") { uid ->
            if (commandId.isBlank()) return@runSafe
            db.collection("users").document(uid)
                .collection("commands").document(commandId)
                .update(
                    mapOf(
                        "executionCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }

    suspend fun createMediaJob(job: MediaJob): Result<String> =
        runSafe(OperationType.CREATE, "users/{uid}/mediaJobs") { uid ->
            val docId = "job_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            db.collection("users").document(uid).collection("mediaJobs").document(docId).set(
                mapOf(
                    "ownerUid" to uid,
                    "accountId" to job.accountId.take(128),
                    "targetLabel" to job.targetLabel.take(120),
                    "targetType" to job.targetType,
                    "commandTrigger" to job.commandTrigger.take(120),
                    "query" to job.query.take(300),
                    "sourcePlatform" to job.sourcePlatform,
                    "mediaType" to job.mediaType,
                    "title" to job.title.take(200),
                    "sourceUrl" to job.sourceUrl.take(1000),
                    "localCachePath" to job.localCachePath.take(500),
                    "fileSizeBytes" to job.fileSizeBytes.coerceAtLeast(0L),
                    "whatsappMediaId" to job.whatsappMediaId.take(200),
                    "status" to job.status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        }

    suspend fun updateMediaJobStatus(
        jobId: String,
        status: String,
        localCachePath: String = "",
        fileSizeBytes: Long = 0L,
        whatsappMediaId: String = ""
    ): Result<Unit> = runSafe(OperationType.UPDATE, "users/{uid}/mediaJobs/$jobId") { uid ->
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (localCachePath.isNotBlank()) updates["localCachePath"] = localCachePath.take(500)
        if (fileSizeBytes > 0L) updates["fileSizeBytes"] = fileSizeBytes
        if (whatsappMediaId.isNotBlank()) updates["whatsappMediaId"] = whatsappMediaId.take(200)
        db.collection("users").document(uid)
            .collection("mediaJobs").document(jobId)
            .update(updates)
            .await()
    }

    suspend fun updatePlatform(
        platformId: String,
        status: String,
        endpointOrHandle: String,
        autoSyncEnabled: Boolean,
        webhookSecretHash: String
    ): Result<Unit> = runSafe(OperationType.UPDATE, "users/{uid}/platforms/$platformId") { uid ->
        db.collection("users").document(uid)
            .collection("platforms").document(platformId)
            .update(
                mapOf(
                    "status" to status,
                    "endpointOrHandle" to endpointOrHandle.take(500),
                    "autoSyncEnabled" to autoSyncEnabled,
                    "webhookSecretHash" to webhookSecretHash.take(128),
                    "lastHeartbeatAtMs" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    suspend fun logEngagementEvent(event: EngagementEvent): Result<Unit> =
        runSafe(OperationType.CREATE, "users/{uid}/events") { uid ->
            val docId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            db.collection("users").document(uid).collection("events").document(docId).set(
                mapOf(
                    "ownerUid" to uid,
                    "accountId" to event.accountId.take(128),
                    "e164Number" to event.e164Number.take(80),
                    "commandTrigger" to event.commandTrigger.take(64),
                    "platform" to event.platform.take(64),
                    "promptPreview" to event.promptPreview.take(300),
                    "responsePreview" to event.responsePreview.take(500),
                    "latencyMs" to event.latencyMs.coerceIn(0L, 300000L),
                    "status" to event.status,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

    suspend fun logAdminAudit(
        actionType: String,
        targetAccountId: String,
        targetLabel: String,
        details: String
    ): Result<Unit> = runSafe(OperationType.CREATE, "users/{uid}/auditLogs") { uid ->
        val docId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        db.collection("users").document(uid).collection("auditLogs").document(docId).set(
            mapOf(
                "ownerUid" to uid,
                "actionType" to actionType.take(48),
                "targetAccountId" to targetAccountId.take(128),
                "targetLabel" to targetLabel.take(120),
                "details" to details.take(400),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    private suspend fun <T> runSafe(
        op: OperationType,
        path: String,
        block: suspend (String) -> T
    ): Result<T> = try {
        val uid = requireUserId()
        Result.success(block(uid))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, op, path)
        Result.failure(e)
    }

    private fun defaultCommands(): List<BotCommand> = listOf(
        BotCommand(
            id = "cmd_play",
            trigger = ".play",
            title = "Play Music & DJ Mixes",
            description = "Search songs/mixes across YouTube, SoundCloud & socials, download audio, and upload to WhatsApp chat/group.",
            category = CommandCategory.MEDIA_AUDIO.name,
            promptTemplate = "Find music track or DJ mix matching: {{query}}",
            usesWebSearch = true,
            commandEnabled = true,
            aliases = listOf(".song", ".music")
        ),
        BotCommand(
            id = "cmd_video",
            trigger = ".video",
            title = "Video Search & Upload",
            description = "Search trending videos across YouTube, TikTok, Instagram, Facebook & X and upload MP4 to chat or group.",
            category = CommandCategory.MEDIA_VIDEO.name,
            promptTemplate = "Find social video matching: {{query}}",
            usesWebSearch = true,
            commandEnabled = true,
            aliases = listOf(".mp4", ".vid")
        ),
        BotCommand(
            id = "cmd_dl",
            trigger = ".dl",
            title = "Universal Social Media Downloader",
            description = "Download media from any pasted TikTok, Instagram, Facebook, X, or YouTube link and upload to WhatsApp.",
            category = CommandCategory.MEDIA_VIDEO.name,
            promptTemplate = "Inspect social media URL for download: {{query}}",
            usesWebSearch = true,
            commandEnabled = true,
            aliases = listOf(".yt", ".ig", ".tiktok", ".fb", ".x")
        ),
        BotCommand(
            id = "cmd_menu",
            trigger = ".menu",
            title = "Bot Command Directory",
            description = "Display all active Honest Bot Pro commands, aliases, prefix, and linked fleet status.",
            category = CommandCategory.UTILITY.name,
            promptTemplate = "{{query}}",
            usesWebSearch = false,
            commandEnabled = true,
            aliases = listOf(".help", ".list")
        ),
        BotCommand(
            id = "cmd_ping",
            trigger = ".ping",
            title = "Latency & System Pulse",
            description = "Check bot response speed, active WhatsApp session mode, and API readiness.",
            category = CommandCategory.UTILITY.name,
            promptTemplate = "{{query}}",
            usesWebSearch = false,
            commandEnabled = true,
            aliases = listOf(".alive", ".status")
        ),
        BotCommand(
            id = "cmd_antilink",
            trigger = ".antilink",
            title = "Group Anti-Link Protection",
            description = "Toggle or set group anti-link protection mode (off, warn, delete).",
            category = CommandCategory.AUTOMATION.name,
            promptTemplate = "{{query}}",
            usesWebSearch = false,
            commandEnabled = true,
            aliases = listOf(".antidelete", ".anticall"),
            ownerOnly = true
        ),
        BotCommand(
            id = "cmd_welcome",
            trigger = ".welcome",
            title = "Welcome & Goodbye Automation",
            description = "Toggle automatic group welcome/goodbye messages with @user & @group tags.",
            category = CommandCategory.AUTOMATION.name,
            promptTemplate = "{{query}}",
            usesWebSearch = false,
            commandEnabled = true,
            aliases = listOf(".goodbye"),
            ownerOnly = true
        ),
        BotCommand(
            id = "cmd_sudo",
            trigger = ".sudo",
            title = "SUDO & Ban Access Control",
            description = "Grant or revoke SUDO admin access (.sudo +263...) or ban abusive numbers (.ban +263...).",
            category = CommandCategory.AUTOMATION.name,
            promptTemplate = "{{query}}",
            usesWebSearch = false,
            commandEnabled = true,
            aliases = listOf(".ban", ".unban"),
            ownerOnly = true
        ),
        BotCommand(
            id = "cmd_ask",
            trigger = "/ask",
            title = "Live OpenAI Web Intelligence",
            description = "Answer any question with real-time internet search and verified web citations.",
            category = CommandCategory.INTERNET.name,
            promptTemplate = "{{query}}",
            usesWebSearch = true,
            commandEnabled = true,
            aliases = listOf(".ai", ".gpt")
        ),
        BotCommand(
            id = "cmd_news",
            trigger = "/news",
            title = "Real-Time Breaking Updates",
            description = "Fetch breaking live news, crypto/FX rates, sports scores, and weather updates.",
            category = CommandCategory.INTERNET.name,
            promptTemplate = "Give concise real-time breaking updates with sources on: {{query}}",
            usesWebSearch = true,
            commandEnabled = true
        ),
        BotCommand(
            id = "cmd_social",
            trigger = "/social",
            title = "Cross-Platform Social Radar",
            description = "Pull live trends, viral threads, and creator updates from X, Instagram, TikTok & YouTube.",
            category = CommandCategory.SOCIAL.name,
            promptTemplate = "Summarize real-time social media trends and discussions across X, Instagram, YouTube, and TikTok for: {{query}}",
            usesWebSearch = true,
            commandEnabled = true
        ),
        BotCommand(
            id = "cmd_share",
            trigger = "/share",
            title = "Instant Share Package",
            description = "Compile requested guides, links, promos, or resources into a clean shareable WhatsApp message.",
            category = CommandCategory.SHARE.name,
            promptTemplate = "Prepare a clean, ready-to-forward WhatsApp share pack with verified links for: {{query}}",
            usesWebSearch = true,
            commandEnabled = true
        )
    )

    private fun defaultPlatforms(
        waConfigured: Boolean,
        mediaExtractorConfigured: Boolean
    ): List<PlatformIntegration> = listOf(
        PlatformIntegration(
            id = "plat_wa_cloud",
            platformKey = PlatformKey.WHATSAPP_CLOUD.name,
            name = "WhatsApp Business Cloud API",
            status = if (waConfigured) PlatformStatus.CONNECTED.name else PlatformStatus.NEEDS_KEYS.name,
            endpointOrHandle = "https://graph.facebook.com/v26.0",
            autoSyncEnabled = true,
            webhookSecretHash = "waba-v26-relay"
        ),
        PlatformIntegration(
            id = "plat_webpair",
            platformKey = PlatformKey.WEBPAIR_BRIDGE.name,
            name = "WebPair & Baileys Session Bridge",
            status = PlatformStatus.CONFIGURED.name,
            endpointOrHandle = "https://your-webpair-bridge.example.com",
            autoSyncEnabled = true,
            webhookSecretHash = "baileys-session-relay"
        ),
        PlatformIntegration(
            id = "plat_ig_fb",
            platformKey = PlatformKey.INSTAGRAM_FB.name,
            name = "Instagram & Facebook Messenger",
            status = PlatformStatus.CONFIGURED.name,
            endpointOrHandle = "@honesttechsupport",
            autoSyncEnabled = true,
            webhookSecretHash = "meta-omnichannel-bridge"
        ),
        PlatformIntegration(
            id = "plat_x_yt",
            platformKey = PlatformKey.X_YOUTUBE.name,
            name = "X (Twitter) & YouTube Feeds",
            status = PlatformStatus.CONNECTED.name,
            endpointOrHandle = "OpenAI Live Web Search + Social Feeds",
            autoSyncEnabled = true,
            webhookSecretHash = "live-social-index"
        ),
        PlatformIntegration(
            id = "plat_media_ext",
            platformKey = PlatformKey.MEDIA_EXTRACTOR.name,
            name = "Media Extractor Bridge (.play/.dl)",
            status = if (mediaExtractorConfigured) PlatformStatus.CONNECTED.name else PlatformStatus.CONFIGURED.name,
            endpointOrHandle = if (mediaExtractorConfigured) "Configured via Secrets" else "https://your-cobalt-extractor.example/api",
            autoSyncEnabled = true,
            webhookSecretHash = "media-stream-relay"
        ),
        PlatformIntegration(
            id = "plat_crm_hook",
            platformKey = PlatformKey.CRM_WEBHOOK.name,
            name = "CRM & Webhook Automations",
            status = PlatformStatus.CONFIGURED.name,
            endpointOrHandle = "https://httpbin.org/post",
            autoSyncEnabled = false,
            webhookSecretHash = "hmac-sha256-ready"
        )
    )
}
