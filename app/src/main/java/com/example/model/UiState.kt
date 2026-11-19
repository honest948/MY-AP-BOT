package com.example.model

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object AppRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_LINKING = "linking"
    const val ROUTE_CONSOLE = "console"
    const val ROUTE_PLATFORMS = "platforms"
    const val ROUTE_VAULT = "vault"
}

data class MediaSearchMatch(
    val id: String,
    val title: String,
    val artistOrChannel: String,
    val platform: MediaSourcePlatform,
    val mediaType: MediaType,
    val durationLabel: String,
    val sourceUrl: String,
    val directStreamUrl: String?,
    val summary: String
)

data class ConsoleExecutionResult(
    val commandUsed: String,
    val prompt: String,
    val responseText: String,
    val citations: List<String>,
    val latencyMs: Long,
    val dispatchedViaWhatsApp: Boolean,
    val whatsappMessageId: String?,
    val targetLabel: String
)

data class ApiDiagnostics(
    val openAiConfigured: Boolean,
    val whatsAppConfigured: Boolean,
    val mediaExtractorConfigured: Boolean,
    val phoneNumberIdMasked: String
)

data class WebhookTestOutcome(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val signaturePrefix: String,
    val message: String
)

data class WhatsAppConnectionResult(
    val connected: Boolean,
    val verifiedName: String = "",
    val displayPhoneNumber: String = "",
    val qualityRating: String = "",
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class WebPairCodeResult(
    val success: Boolean,
    val pairingCode: String = "",
    val sessionId: String = "",
    val latencyMs: Long = 0L,
    val message: String = ""
)

sealed interface PolicyCheckResult {
    data object Allowed : PolicyCheckResult
    data class Denied(val reason: String) : PolicyCheckResult
}
