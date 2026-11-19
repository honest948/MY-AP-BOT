package com.example.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.BuildConfig
import com.example.model.MediaSearchMatch
import com.example.model.MediaSourcePlatform
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

data class OpenAiReply(
    val text: String,
    val citations: List<String>,
    val latencyMs: Long
)

data class MediaDownloadOutcome(
    val file: File,
    val mimeType: String,
    val byteCount: Long
)

class OpenAiAndMediaRepository(context: Context) {

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isOpenAiConfigured(): Boolean {
        val key = BuildConfig.OPENAI_API_KEY
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    fun isMediaExtractorConfigured(): Boolean {
        val url = BuildConfig.MEDIA_EXTRACTOR_URL
        return url.isNotBlank() && !url.startsWith("YOUR_") && url.startsWith("http")
    }

    suspend fun runLiveWebSearchCommand(
        prompt: String,
        systemInstructions: String,
        useWebSearch: Boolean = true
    ): Result<OpenAiReply> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (!isOpenAiConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("OPENAI_API_KEY is not configured in AI Studio Secrets.")
            )
        }

        val startMs = System.currentTimeMillis()
        try {
            val bodyJson = JSONObject().apply {
                put("model", "gpt-4o")
                put("input", prompt)
                put("instructions", systemInstructions)
                if (useWebSearch) {
                    put(
                        "tools",
                        JSONArray().put(
                            JSONObject().apply {
                                put("type", "web_search")
                            }
                        )
                    )
                }
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                            ?: "OpenAI HTTP ${response.code}"
                    } catch (_: Exception) {
                        "OpenAI HTTP ${response.code}"
                    }
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }

                val parsed = parseResponsesOutput(rawBody)
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                Result.success(
                    OpenAiReply(
                        text = parsed.first.ifBlank { "No text response returned from OpenAI." },
                        citations = parsed.second.distinct(),
                        latencyMs = latency
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSocialMediaTracks(
        query: String,
        preferredType: MediaType,
        preferredPlatform: MediaSourcePlatform? = null
    ): Result<List<MediaSearchMatch>> = withContext(Dispatchers.IO) {
        if (!isOpenAiConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("OPENAI_API_KEY is required in AI Studio Secrets to search live songs, mixes, and social videos.")
            )
        }

        val platformHint = preferredPlatform?.label ?: "YouTube, SoundCloud, TikTok, Instagram, Facebook, and X"
        val mediaHint = if (preferredType == MediaType.AUDIO) "music track, DJ mix, or audio stream" else "video clip or reel"
        val prompt = """
            Search the live internet for "$query" ($mediaHint) across $platformHint.
            Return ONLY a valid JSON array (no markdown fences) of up to 4 real results found via web search, each object having exact keys:
            "title", "artistOrChannel", "platform" (one of YOUTUBE, TIKTOK, INSTAGRAM, FACEBOOK, X, SOUNDCLOUD, WEB), "durationLabel", "sourceUrl", "directStreamUrl" (null unless a direct .mp3/.m4a/.mp4 link was found), "summary".
        """.trimIndent()

        val replyResult = runLiveWebSearchCommand(
            prompt = prompt,
            systemInstructions = "You are Honest Bot Pro Media Finder. Always use web_search to return real URLs and metadata in strict JSON array format.",
            useWebSearch = true
        )

        replyResult.mapCatching { reply ->
            val jsonCandidate = extractJsonArray(reply.text)
            val arr = JSONArray(jsonCandidate)
            val list = mutableListOf<MediaSearchMatch>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sourceUrl = obj.optString("sourceUrl").ifBlank { reply.citations.getOrNull(i).orEmpty() }
                if (sourceUrl.isBlank()) continue
                val rawPlat = obj.optString("platform", "WEB").uppercase()
                val platform = MediaSourcePlatform.entries.firstOrNull { it.name == rawPlat }
                    ?: inferPlatformFromUrl(sourceUrl)
                val direct = obj.optString("directStreamUrl").takeIf {
                    it.isNotBlank() && it != "null" && it.startsWith("http")
                }
                list.add(
                    MediaSearchMatch(
                        id = UUID.randomUUID().toString().take(8),
                        title = obj.optString("title", query).ifBlank { query },
                        artistOrChannel = obj.optString("artistOrChannel", platform.label),
                        platform = platform,
                        mediaType = preferredType,
                        durationLabel = obj.optString("durationLabel", "Live"),
                        sourceUrl = sourceUrl,
                        directStreamUrl = direct,
                        summary = obj.optString("summary", "Verified via OpenAI live web search")
                    )
                )
            }
            if (list.isEmpty() && reply.citations.isNotEmpty()) {
                reply.citations.take(3).forEachIndexed { idx, url ->
                    val platform = inferPlatformFromUrl(url)
                    list.add(
                        MediaSearchMatch(
                            id = "cit_$idx",
                            title = "$query (${platform.label})",
                            artistOrChannel = platform.label,
                            platform = platform,
                            mediaType = preferredType,
                            durationLabel = "Web Match",
                            sourceUrl = url,
                            directStreamUrl = url.takeIf { isDirectMediaFileUrl(it) },
                            summary = "Discovered via live internet search"
                        )
                    )
                }
            }
            list
        }
    }

    suspend fun resolveAndDownloadMedia(
        match: MediaSearchMatch,
        customExtractorUrl: String? = null,
        onProgress: (Float) -> Unit
    ): Result<MediaDownloadOutcome> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.08f)
            val candidateUrl = resolveDownloadStreamUrl(match, customExtractorUrl)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "No direct binary stream returned for ${match.platform.label}. Configure a Cobalt-compatible Media Extractor URL in Platforms or Secrets to download protected platform streams."
                    )
                )

            val safeName = match.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(42)
            val ext = when {
                candidateUrl.contains(".mp4", ignoreCase = true) || match.mediaType == MediaType.VIDEO -> "mp4"
                candidateUrl.contains(".m4a", ignoreCase = true) -> "m4a"
                candidateUrl.contains(".ogg", ignoreCase = true) -> "ogg"
                else -> "mp3"
            }
            val cacheDir = File(appContext.cacheDir, "honest_media").apply { mkdirs() }
            val outFile = File(cacheDir, "${safeName}_${System.currentTimeMillis()}.$ext")

            val req = Request.Builder()
                .url(candidateUrl)
                .addHeader("User-Agent", "HonestBotPro/1.0 (Android)")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Media download failed (HTTP ${resp.code})")
                    )
                }
                val body = resp.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty media response body")
                )
                val mimeType = resp.header("Content-Type")
                    ?.substringBefore(";")
                    ?.ifBlank { null }
                    ?: if (ext == "mp4") "video/mp4" else "audio/mpeg"

                val totalBytes = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress((0.15f + 0.85f * (downloaded.toFloat() / totalBytes)).coerceIn(0.15f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
                Result.success(
                    MediaDownloadOutcome(
                        file = outFile,
                        mimeType = mimeType,
                        byteCount = outFile.length()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToDeviceDownloads(file: File, mimeType: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HonestBotPro")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = appContext.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext Result.failure(IllegalStateException("MediaStore insert failed"))
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Result.success("Saved to Downloads/HonestBotPro/${file.name}")
                } else {
                    Result.success("Cached securely at ${file.name}")
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun resolveDownloadStreamUrl(match: MediaSearchMatch, customExtractorUrl: String?): String? {
        if (!match.directStreamUrl.isNullOrBlank()) return match.directStreamUrl
        if (isDirectMediaFileUrl(match.sourceUrl)) return match.sourceUrl

        val configuredUrl = customExtractorUrl?.takeIf { it.startsWith("http") }
            ?: BuildConfig.MEDIA_EXTRACTOR_URL.takeIf { isMediaExtractorConfigured() }
            ?: return null

        val payload = JSONObject().apply {
            put("url", match.sourceUrl)
            put("downloadMode", if (match.mediaType == MediaType.AUDIO) "audio" else "auto")
            put("audioFormat", "mp3")
            put("videoQuality", "720")
        }

        val req = Request.Builder()
            .url(configuredUrl)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            val json = JSONObject(raw)
            val directUrl = json.optString("url")
            if (directUrl.startsWith("http")) return directUrl
            val picker = json.optJSONArray("picker")
            if (picker != null && picker.length() > 0) {
                val first = picker.optJSONObject(0)?.optString("url").orEmpty()
                if (first.startsWith("http")) return first
            }
        }
        return null
    }

    private fun parseResponsesOutput(rawBody: String): Pair<String, List<String>> {
        val root = JSONObject(rawBody)
        val citations = mutableListOf<String>()
        val textParts = mutableListOf<String>()

        val topOutputText = root.optString("output_text")
        if (topOutputText.isNotBlank()) textParts.add(topOutputText)

        val outputArr = root.optJSONArray("output")
        if (outputArr != null) {
            for (i in 0 until outputArr.length()) {
                val item = outputArr.optJSONObject(i) ?: continue
                val contentArr = item.optJSONArray("content") ?: continue
                for (j in 0 until contentArr.length()) {
                    val content = contentArr.optJSONObject(j) ?: continue
                    val txt = content.optString("text")
                    if (txt.isNotBlank() && topOutputText.isBlank()) textParts.add(txt)
                    val annotations = content.optJSONArray("annotations") ?: continue
                    for (k in 0 until annotations.length()) {
                        val ann = annotations.optJSONObject(k) ?: continue
                        val url = ann.optString("url")
                        if (url.startsWith("http")) citations.add(url)
                    }
                }
            }
        }
        return textParts.joinToString("\n\n") to citations
    }

    private fun extractJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else "[]"
    }

    private fun isDirectMediaFileUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".mp4") || lower.endsWith(".ogg")
    }

    private fun inferPlatformFromUrl(url: String): MediaSourcePlatform {
        val lower = url.lowercase()
        return when {
            "youtube.com" in lower || "youtu.be" in lower -> MediaSourcePlatform.YOUTUBE
            "tiktok.com" in lower -> MediaSourcePlatform.TIKTOK
            "instagram.com" in lower -> MediaSourcePlatform.INSTAGRAM
            "facebook.com" in lower || "fb.watch" in lower -> MediaSourcePlatform.FACEBOOK
            "x.com" in lower || "twitter.com" in lower -> MediaSourcePlatform.X
            "soundcloud.com" in lower -> MediaSourcePlatform.SOUNDCLOUD
            else -> MediaSourcePlatform.WEB
        }
    }
}
