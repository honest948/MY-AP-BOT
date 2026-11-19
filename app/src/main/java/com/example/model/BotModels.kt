package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GlobalFleetState(val label: String, val description: String) {
    ONLINE("ONLINE", "All linked accounts active & processing"),
    VIP_LOCKDOWN("VIP LOCKDOWN", "Only VIP accounts allowed; new links paused"),
    MAINTENANCE("MAINTENANCE", "Auto-replies with owner maintenance notice"),
    PAUSED_ALL("PAUSED ALL", "Emergency kill-switch: all bot operations halted");

    companion object {
        fun fromRaw(raw: String): GlobalFleetState =
            entries.firstOrNull { it.name == raw } ?: ONLINE
    }
}

enum class TargetType(val label: String) {
    PERSONAL_INBOX("Personal Inbox"),
    GROUP_TARGET("Group / Broadcast");

    companion object {
        fun fromRaw(raw: String): TargetType =
            entries.firstOrNull { it.name == raw } ?: PERSONAL_INBOX
    }
}

enum class ConnectionStatus(val label: String) {
    LIVE("LIVE • LINKED"),
    AWAITING_CODE("AWAITING CODE"),
    PAUSED("PAUSED"),
    SUSPENDED("SUSPENDED"),
    REVOKED("REVOKED");

    companion object {
        fun fromRaw(raw: String): ConnectionStatus =
            entries.firstOrNull { it.name == raw } ?: AWAITING_CODE
    }
}

enum class OperatingMode(val label: String) {
    FULL_AI_MEDIA("Full AI + Media"),
    COMMANDS_ONLY("Commands Only"),
    VIP_CONCIERGE("VIP Concierge"),
    MONITOR_ONLY("Silent Monitor");

    companion object {
        fun fromRaw(raw: String): OperatingMode =
            entries.firstOrNull { it.name == raw } ?: FULL_AI_MEDIA
    }
}

enum class AccountRole(val label: String) {
    VIP("VIP"),
    STANDARD("Standard"),
    RESTRICTED("Restricted"),
    SUSPENDED("Suspended"),
    BANNED("Banned");

    companion object {
        fun fromRaw(raw: String): AccountRole =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

enum class LinkingMethod(val label: String) {
    CLOUD_API("WhatsApp Cloud API"),
    WEB_PAIR("WebPair / Session Bridge"),
    LOCAL_VAULT("Encrypted Vault Code");

    companion object {
        fun fromRaw(raw: String): LinkingMethod =
            entries.firstOrNull { it.name == raw } ?: CLOUD_API
    }
}

enum class CommandCategory(val label: String) {
    MEDIA_AUDIO("Music / .play"),
    MEDIA_VIDEO("Video / .dl"),
    INTERNET("Live Web"),
    SOCIAL("Social Feeds"),
    SHARE("Share Pack"),
    BUSINESS("Business Ops"),
    AUTOMATION("Security & Auto"),
    UTILITY("Menu & Ping");

    companion object {
        fun fromRaw(raw: String): CommandCategory =
            entries.firstOrNull { it.name == raw } ?: INTERNET
    }
}

enum class MediaSourcePlatform(val label: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    X("X (Twitter)"),
    SOUNDCLOUD("SoundCloud"),
    WEB("Web Audio/Video");

    companion object {
        fun fromRaw(raw: String): MediaSourcePlatform =
            entries.firstOrNull { it.name == raw } ?: WEB
    }
}

enum class MediaType(val label: String) {
    AUDIO("Audio"),
    VIDEO("Video"),
    DOCUMENT("Document");

    companion object {
        fun fromRaw(raw: String): MediaType =
            entries.firstOrNull { it.name == raw } ?: AUDIO
    }
}

enum class MediaJobStatus(val label: String) {
    SEARCHING("Searching"),
    DOWNLOADING("Downloading"),
    UPLOADING("Uploading"),
    DELIVERED("Delivered"),
    FAILED("Failed");

    companion object {
        fun fromRaw(raw: String): MediaJobStatus =
            entries.firstOrNull { it.name == raw } ?: SEARCHING
    }
}

enum class PlatformKey(val label: String) {
    WHATSAPP_CLOUD("WhatsApp Cloud API"),
    WEBPAIR_BRIDGE("WebPair & Baileys Bridge"),
    INSTAGRAM_FB("Instagram & Messenger"),
    X_YOUTUBE("X (Twitter) & YouTube"),
    MEDIA_EXTRACTOR("Media Extractor API"),
    CRM_WEBHOOK("CRM & Webhooks");

    companion object {
        fun fromRaw(raw: String): PlatformKey =
            entries.firstOrNull { it.name == raw } ?: WHATSAPP_CLOUD
    }
}

enum class PlatformStatus(val label: String) {
    CONNECTED("CONNECTED"),
    CONFIGURED("CONFIGURED"),
    NEEDS_KEYS("NEEDS KEYS"),
    PAUSED("PAUSED");

    companion object {
        fun fromRaw(raw: String): PlatformStatus =
            entries.firstOrNull { it.name == raw } ?: CONFIGURED
    }
}

enum class EventStatus(val label: String) {
    DELIVERED("DELIVERED"),
    PROCESSED("PROCESSED"),
    BLOCKED("BLOCKED"),
    FAILED("FAILED");

    companion object {
        fun fromRaw(raw: String): EventStatus =
            entries.firstOrNull { it.name == raw } ?: PROCESSED
    }
}

@Keep
@IgnoreExtraProperties
data class OwnerPolicy(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val globalFleetState: String = GlobalFleetState.ONLINE.name,
    val maintenanceMessage: String = "Honest Bot Pro is currently under scheduled maintenance. Contact +263 780549558 for urgent support.",
    val allowNewLinkRequests: Boolean = true,
    val defaultDailyQuota: Long = 250L,
    val maxMediaSizeMb: Long = 16L,
    val botName: String = "Honest Bot Pro",
    val commandPrefix: String = ".",
    val botMode: String = "public",
    val webPairBridgeUrl: String = "",
    val sessionId: String = "",
    val readMessages: Boolean = false,
    val alwaysOnline: Boolean = false,
    val autoTyping: String = "off",
    val autoRecording: String = "off",
    val autoReact: Boolean = false,
    val ownerReact: Boolean = false,
    val autoViewStatus: Boolean = true,
    val autoLikeStatus: Boolean = false,
    val autoDownloader: Boolean = false,
    val antiLinkMode: String = "delete",
    val antiDeletePath: String = "inbox",
    val antiEditPath: String = "inbox",
    val antiCallEnabled: Boolean = false,
    val callRejectMessage: String = "*Call not allowed on this bot number. Send a text command instead.*",
    val antiStatusEnabled: Boolean = true,
    val welcomeEnabled: Boolean = false,
    val goodbyeEnabled: Boolean = false,
    val welcomeMessage: String = "*_@user joined @group, welcome!_*",
    val goodbyeMessage: String = "*_@user left @group._*",
    val sudoNumbers: List<String> = emptyList(),
    val bannedNumbers: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConnectedAccount(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val targetType: String = TargetType.PERSONAL_INBOX.name,
    val countryCode: String = "+263",
    val phoneNumber: String = "",
    val e164Number: String = "",
    val displayName: String = "",
    val pairingCode: String = "",
    val sessionFingerprint: String = "",
    val pairingExpiresAtMs: Long = 0L,
    val connectionStatus: String = ConnectionStatus.AWAITING_CODE.name,
    val operatingMode: String = OperatingMode.FULL_AI_MEDIA.name,
    val customPhoneNumberId: String = "",
    val role: String = AccountRole.STANDARD.name,
    val botEnabled: Boolean = true,
    val autoReplyEnabled: Boolean = true,
    val mediaDownloadEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val groupDispatchEnabled: Boolean = true,
    val blockedCommands: List<String> = emptyList(),
    val dailyQuota: Long = 250L,
    val messagesUsedToday: Long = 0L,
    val lastActiveAtMs: Long = 0L,
    val linkingMethod: String = LinkingMethod.CLOUD_API.name,
    val sessionId: String = "",
    val antiLinkEnabled: Boolean = true,
    val antiDeleteEnabled: Boolean = true,
    val welcomeEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BotCommand(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val trigger: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = CommandCategory.INTERNET.name,
    val promptTemplate: String = "",
    val usesWebSearch: Boolean = true,
    val commandEnabled: Boolean = true,
    val executionCount: Long = 0L,
    val aliases: List<String> = emptyList(),
    val ownerOnly: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MediaJob(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val accountId: String = "",
    val targetLabel: String = "",
    val targetType: String = TargetType.PERSONAL_INBOX.name,
    val commandTrigger: String = "",
    val query: String = "",
    val sourcePlatform: String = MediaSourcePlatform.YOUTUBE.name,
    val mediaType: String = MediaType.AUDIO.name,
    val title: String = "",
    val sourceUrl: String = "",
    val localCachePath: String = "",
    val fileSizeBytes: Long = 0L,
    val whatsappMediaId: String = "",
    val status: String = MediaJobStatus.SEARCHING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlatformIntegration(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val platformKey: String = PlatformKey.WHATSAPP_CLOUD.name,
    val name: String = "",
    val status: String = PlatformStatus.CONFIGURED.name,
    val endpointOrHandle: String = "",
    val autoSyncEnabled: Boolean = true,
    val webhookSecretHash: String = "",
    val lastHeartbeatAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EngagementEvent(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val accountId: String = "",
    val e164Number: String = "",
    val commandTrigger: String = "",
    val platform: String = "",
    val promptPreview: String = "",
    val responsePreview: String = "",
    val latencyMs: Long = 0L,
    val status: String = EventStatus.PROCESSED.name,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val actionType: String = "",
    val targetAccountId: String = "",
    val targetLabel: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CountryCodeOption(
    val code: String,
    val country: String,
    val flag: String
)

object CountryCodes {
    val presets = listOf(
        CountryCodeOption("+263", "Zimbabwe", "ZW"),
        CountryCodeOption("+27", "South Africa", "ZA"),
        CountryCodeOption("+260", "Zambia", "ZM"),
        CountryCodeOption("+267", "Botswana", "BW"),
        CountryCodeOption("+258", "Mozambique", "MZ"),
        CountryCodeOption("+254", "Kenya", "KE"),
        CountryCodeOption("+234", "Nigeria", "NG"),
        CountryCodeOption("+233", "Ghana", "GH"),
        CountryCodeOption("+255", "Tanzania", "TZ"),
        CountryCodeOption("+256", "Uganda", "UG"),
        CountryCodeOption("+20", "Egypt", "EG"),
        CountryCodeOption("+1", "United States / Canada", "US"),
        CountryCodeOption("+44", "United Kingdom", "GB"),
        CountryCodeOption("+91", "India", "IN"),
        CountryCodeOption("+971", "UAE", "AE"),
        CountryCodeOption("+49", "Germany", "DE"),
        CountryCodeOption("+33", "France", "FR"),
        CountryCodeOption("+55", "Brazil", "BR"),
        CountryCodeOption("+61", "Australia", "AU"),
        CountryCodeOption("+86", "China", "CN")
    )
}
