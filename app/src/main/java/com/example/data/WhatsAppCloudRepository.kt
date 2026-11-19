package com.example.data

import com.example.BuildConfig
import com.example.model.MediaType
import com.example.model.WebPairCodeResult
import com.example.model.WebhookTestOutcome
import com.example.model.WhatsAppConnectionResult
import com.example.security.SessionCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class WhatsAppDispatchResult(
    val messageId: String,
    val mediaId: String? = null
)

class WhatsAppCloudRepository(
    private val cryptoManager: SessionCryptoManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    fun isWhatsAppConfigured(): Boolean {
        val token = BuildConfig.WHATSAPP_ACCESS_TOKEN
        val phoneId = BuildConfig.WHATSAPP_PHONE_NUMBER_ID
        return token.isNotBlank() && !token.startsWith("YOUR_") &&
            phoneId.isNotBlank() && !phoneId.startsWith("YOUR_")
    }

    fun maskedPhoneNumberId(): String {
        val id = BuildConfig.WHATSAPP_PHONE_NUMBER_ID
        if (id.isBlank() || id.startsWith("YOUR_")) return "Not configured"
        return if (id.length <= 6) id else "••••${id.takeLast(4)}"
    }

    suspend fun verifyCloudConnection(customPhoneNumberId: String = ""): WhatsAppConnectionResult =
        withContext(Dispatchers.IO) {
            val token = BuildConfig.WHATSAPP_ACCESS_TOKEN
            val phoneId = customPhoneNumberId.trim().ifBlank { BuildConfig.WHATSAPP_PHONE_NUMBER_ID }
            if (token.isBlank() || token.startsWith("YOUR_") || phoneId.isBlank() || phoneId.startsWith("YOUR_")) {
                return@withContext WhatsAppConnectionResult(
                    connected = false,
                    message = "WhatsApp Cloud API credentials missing. Add WHATSAPP_ACCESS_TOKEN and WHATSAPP_PHONE_NUMBER_ID in AI Studio Secrets, or link using local pairing code + WhatsApp app fallback."
                )
            }

            val start = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("https://graph.facebook.com/$GRAPH_VERSION/$phoneId?fields=verified_name,display_phone_number,quality_rating")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext WhatsAppConnectionResult(
                            connected = false,
                            latencyMs = latency,
                            message = parseMetaError(resp.code, raw)
                        )
                    }
                    val json = JSONObject(raw)
                    val verifiedName = json.optString("verified_name").ifBlank { "WhatsApp Business" }
                    val displayPhone = json.optString("display_phone_number").ifBlank { phoneId }
                    val quality = json.optString("quality_rating").ifBlank { "ACTIVE" }
                    WhatsAppConnectionResult(
                        connected = true,
                        verifiedName = verifiedName,
                        displayPhoneNumber = displayPhone,
                        qualityRating = quality,
                        latencyMs = latency,
                        message = "Connected to $verifiedName ($displayPhone) in ${latency}ms • Quality: $quality"
                    )
                }
            } catch (e: Exception) {
                WhatsAppConnectionResult(
                    connected = false,
                    latencyMs = (System.currentTimeMillis() - start),
                    message = "Network connection error: ${e.localizedMessage ?: "Unable to reach graph.facebook.com"}"
                )
            }
        }

    suspend fun requestWebPairCode(
        bridgeUrl: String,
        e164Phone: String
    ): WebPairCodeResult = withContext(Dispatchers.IO) {
        val cleanBase = bridgeUrl.trim().trimEnd('/')
        if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
            return@withContext WebPairCodeResult(
                success = false,
                message = "Enter a valid WebPair Bridge server URL starting with http:// or https://"
            )
        }
        val digits = e164Phone.filter { it.isDigit() }
        if (digits.length < 6) {
            return@withContext WebPairCodeResult(
                success = false,
                message = "Phone number must contain at least 6 digits for WebPair pairing."
            )
        }

        val start = System.currentTimeMillis()
        val candidateUrls = listOf(
            "$cleanBase/pair?number=$digits",
            "$cleanBase/code?number=$digits"
        )
        var lastError = "Unable to reach WebPair bridge server"
        for (url in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    val raw = resp.body?.string().orEmpty().trim()
                    if (resp.isSuccessful && raw.isNotBlank()) {
                        var parsedCode = ""
                        var parsedSession = ""
                        if (raw.startsWith("{")) {
                            val json = JSONObject(raw)
                            parsedCode = json.optString("code")
                                .ifBlank { json.optString("pairingCode") }
                                .ifBlank { json.optString("pairCode") }
                            parsedSession = json.optString("sessionId")
                                .ifBlank { json.optString("SESSION_ID") }
                        } else if (raw.length in 6..20 && !raw.contains("<html", ignoreCase = true)) {
                            parsedCode = raw
                        }
                        if (parsedCode.isNotBlank()) {
                            return@withContext WebPairCodeResult(
                                success = true,
                                pairingCode = parsedCode,
                                sessionId = parsedSession,
                                latencyMs = latency,
                                message = "WebPair Bridge returned code $parsedCode in ${latency}ms"
                            )
                        }
                    } else {
                        lastError = "Bridge HTTP ${resp.code} at $url"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Bridge connection failed"
            }
        }
        WebPairCodeResult(
            success = false,
            latencyMs = (System.currentTimeMillis() - start),
            message = lastError
        )
    }

    suspend fun sendTextMessage(
        recipientE164OrGroupId: String,
        bodyText: String,
        customPhoneNumberId: String = ""
    ): Result<WhatsAppDispatchResult> = withContext(Dispatchers.IO) {
        val token = BuildConfig.WHATSAPP_ACCESS_TOKEN
        val phoneId = customPhoneNumberId.trim().ifBlank { BuildConfig.WHATSAPP_PHONE_NUMBER_ID }
        if (token.isBlank() || token.startsWith("YOUR_") || phoneId.isBlank() || phoneId.startsWith("YOUR_")) {
            return@withContext Result.failure(
                IllegalStateException("WhatsApp Cloud API credentials missing in AI Studio Secrets.")
            )
        }

        try {
            val cleanRecipient = sanitizeRecipient(recipientE164OrGroupId)
            val payload = JSONObject().apply {
                put("messaging_product", "whatsapp")
                put("recipient_type", if (recipientE164OrGroupId.contains("@g.us")) "group" else "individual")
                put("to", cleanRecipient)
                put("type", "text")
                put("text", JSONObject().apply {
                    put("preview_url", true)
                    put("body", bodyText.take(4096))
                })
            }

            val req = Request.Builder()
                .url("https://graph.facebook.com/$GRAPH_VERSION/$phoneId/messages")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(parseMetaError(resp.code, raw)))
                }
                val msgId = JSONObject(raw).optJSONArray("messages")
                    ?.optJSONObject(0)?.optString("id").orEmpty()
                Result.success(WhatsAppDispatchResult(messageId = msgId.ifBlank { "wa_msg_sent" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAndSendMedia(
        recipientE164OrGroupId: String,
        file: File,
        mimeType: String,
        requestedMediaType: MediaType,
        caption: String,
        customPhoneNumberId: String = ""
    ): Result<WhatsAppDispatchResult> = withContext(Dispatchers.IO) {
        val token = BuildConfig.WHATSAPP_ACCESS_TOKEN
        val phoneId = customPhoneNumberId.trim().ifBlank { BuildConfig.WHATSAPP_PHONE_NUMBER_ID }
        if (token.isBlank() || token.startsWith("YOUR_") || phoneId.isBlank() || phoneId.startsWith("YOUR_")) {
            return@withContext Result.failure(
                IllegalStateException("WhatsApp Cloud API credentials missing in AI Studio Secrets.")
            )
        }

        try {
            // WhatsApp Cloud API enforces 16MB cap for native audio/video, up to 100MB as document
            val effectiveType = if (file.length() > 16L * 1024L * 1024L) {
                MediaType.DOCUMENT
            } else {
                requestedMediaType
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("messaging_product", "whatsapp")
                .addFormDataPart("type", mimeType)
                .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
                .build()

            val uploadReq = Request.Builder()
                .url("https://graph.facebook.com/$GRAPH_VERSION/$phoneId/media")
                .addHeader("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            val mediaId = client.newCall(uploadReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(parseMetaError(resp.code, raw)))
                }
                JSONObject(raw).optString("id")
            }

            if (mediaId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Meta /media returned blank media ID"))
            }

            val waTypeKey = when (effectiveType) {
                MediaType.AUDIO -> "audio"
                MediaType.VIDEO -> "video"
                MediaType.DOCUMENT -> "document"
            }

            val mediaObj = JSONObject().apply {
                put("id", mediaId)
                if (effectiveType != MediaType.AUDIO) {
                    put("caption", caption.take(1024))
                }
                if (effectiveType == MediaType.DOCUMENT) {
                    put("filename", file.name)
                }
            }

            val msgPayload = JSONObject().apply {
                put("messaging_product", "whatsapp")
                put("recipient_type", if (recipientE164OrGroupId.contains("@g.us")) "group" else "individual")
                put("to", sanitizeRecipient(recipientE164OrGroupId))
                put("type", waTypeKey)
                put(waTypeKey, mediaObj)
            }

            val sendReq = Request.Builder()
                .url("https://graph.facebook.com/$GRAPH_VERSION/$phoneId/messages")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(msgPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(sendReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(parseMetaError(resp.code, raw)))
                }
                val msgId = JSONObject(raw).optJSONArray("messages")
                    ?.optJSONObject(0)?.optString("id").orEmpty()
                Result.success(
                    WhatsAppDispatchResult(
                        messageId = msgId.ifBlank { "wa_media_msg" },
                        mediaId = mediaId
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testOutboundWebhook(
        endpointUrl: String,
        secret: String,
        eventName: String,
        samplePayload: String
    ): WebhookTestOutcome = withContext(Dispatchers.IO) {
        if (!endpointUrl.startsWith("http")) {
            return@withContext WebhookTestOutcome(
                success = false,
                statusCode = 0,
                latencyMs = 0L,
                signaturePrefix = "",
                message = "Webhook URL must start with http:// or https://"
            )
        }
        val start = System.currentTimeMillis()
        try {
            val sig = cryptoManager.signWebhookPayload(secret, samplePayload)
            val req = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-HonestBot-Event", eventName)
                .addHeader("X-HonestBot-Signature-256", sig)
                .post(samplePayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                WebhookTestOutcome(
                    success = resp.isSuccessful,
                    statusCode = resp.code,
                    latencyMs = latency,
                    signaturePrefix = sig.take(18) + "...",
                    message = if (resp.isSuccessful) {
                        "HTTP ${resp.code} OK in ${latency}ms"
                    } else {
                        "HTTP ${resp.code} (${resp.message})"
                    }
                )
            }
        } catch (e: Exception) {
            WebhookTestOutcome(
                success = false,
                statusCode = 0,
                latencyMs = (System.currentTimeMillis() - start),
                signaturePrefix = "",
                message = e.localizedMessage ?: "Webhook connection failed"
            )
        }
    }

    private fun sanitizeRecipient(raw: String): String {
        if (raw.contains("@g.us")) return raw.trim()
        return raw.filter { it.isDigit() }
    }

    private fun parseMetaError(code: Int, raw: String): String {
        return try {
            val errObj = JSONObject(raw).optJSONObject("error")
            val errCode = errObj?.optInt("code", 0) ?: 0
            val msg = errObj?.optString("message").orEmpty()
            when (errCode) {
                190 -> "Invalid or expired WHATSAPP_ACCESS_TOKEN (HTTP $code). Update token in AI Studio Secrets."
                131030 -> "Recipient number not in Meta Sandbox Allowed List (#131030). Add it in Meta Developer Portal > WhatsApp > API Setup, or send via WhatsApp app."
                131047 -> "24-hour WhatsApp session window closed (#131047). Message your Business number from WhatsApp first or tap 'Send via WhatsApp'."
                100 -> "Invalid Phone Number ID or recipient format (#100): $msg"
                else -> if (msg.isNotBlank()) "WhatsApp API ($code/#$errCode): $msg" else "WhatsApp API HTTP $code"
            }
        } catch (_: Exception) {
            "WhatsApp API HTTP $code"
        }
    }

    private companion object {
        const val GRAPH_VERSION = "v26.0"
    }
}
