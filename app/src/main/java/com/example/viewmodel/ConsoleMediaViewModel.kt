package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BotFirestoreRepository
import com.example.data.MediaDownloadOutcome
import com.example.data.OpenAiAndMediaRepository
import com.example.data.PolicyEnforcer
import com.example.data.WhatsAppCloudRepository
import com.example.model.BotCommand
import com.example.model.ConnectedAccount
import com.example.model.EngagementEvent
import com.example.model.EventStatus
import com.example.model.MediaJob
import com.example.model.MediaJobStatus
import com.example.model.MediaSearchMatch
import com.example.model.MediaSourcePlatform
import com.example.model.MediaType
import com.example.model.OwnerPolicy
import com.example.model.PolicyCheckResult
import com.example.model.TargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConsoleMediaUiState(
    val selectedAccountId: String? = null,
    val isRunningCommand: Boolean = false,
    val isSearchingMedia: Boolean = false,
    val downloadingMatchId: String? = null,
    val downloadProgress: Float = 0f,
    val lastResult: com.example.model.ConsoleExecutionResult? = null,
    val mediaMatches: List<MediaSearchMatch> = emptyList(),
    val lastDownloadedFile: MediaDownloadOutcome? = null,
    val lastDownloadedTitle: String = "",
    val feedbackMessage: String? = null,
    val policyBlockedReason: String? = null
)

class ConsoleMediaViewModel(
    private val firestoreRepo: BotFirestoreRepository,
    private val openAiMediaRepo: OpenAiAndMediaRepository,
    private val whatsAppRepo: WhatsAppCloudRepository,
    private val policyEnforcer: PolicyEnforcer,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsoleMediaUiState())
    val uiState: StateFlow<ConsoleMediaUiState> = _uiState.asStateFlow()

    fun selectAccount(accountId: String) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId, policyBlockedReason = null)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, policyBlockedReason = null)
    }

    fun executeInput(
        rawInput: String,
        policy: OwnerPolicy,
        account: ConnectedAccount?,
        commands: List<BotCommand>,
        dispatchToWhatsApp: Boolean,
        customExtractorUrl: String? = null
    ) {
        val input = rawInput.trim()
        if (input.isBlank()) return

        val configuredPrefix = policy.commandPrefix.ifBlank { "." }
        val firstToken = input.substringBefore(" ").lowercase()
        val hasPrefix = firstToken.startsWith(".") || firstToken.startsWith("/") ||
            firstToken.startsWith("!") || firstToken.startsWith(configuredPrefix.lowercase())
        val baseCmdName = when {
            firstToken.startsWith(configuredPrefix.lowercase()) -> firstToken.removePrefix(configuredPrefix.lowercase())
            firstToken.startsWith(".") || firstToken.startsWith("/") || firstToken.startsWith("!") -> firstToken.drop(1)
            else -> firstToken
        }

        val audioCmds = setOf("play", "song", "music")
        val videoCmds = setOf("video", "mp4", "vid", "dl", "yt", "ig", "tiktok", "fb", "x")

        if (hasPrefix && (baseCmdName in audioCmds || baseCmdName in videoCmds)) {
            val trigger = ".$baseCmdName"
            val query = input.substringAfter(" ", "").trim()
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Provide a song, mix, or social link after $firstToken (e.g. ${configuredPrefix}play dj onilo mix)"
                )
                return
            }
            val preferredType = if (baseCmdName in audioCmds) MediaType.AUDIO else MediaType.VIDEO
            val preferredPlatform = when (baseCmdName) {
                "yt" -> MediaSourcePlatform.YOUTUBE
                "ig" -> MediaSourcePlatform.INSTAGRAM
                "tiktok" -> MediaSourcePlatform.TIKTOK
                "fb" -> MediaSourcePlatform.FACEBOOK
                "x" -> MediaSourcePlatform.X
                else -> null
            }
            searchMediaForPlay(
                trigger = trigger,
                query = query,
                preferredType = preferredType,
                preferredPlatform = preferredPlatform,
                policy = policy,
                account = account,
                matchingCommand = commands.firstOrNull { cmd ->
                    cmd.trigger.equals(trigger, ignoreCase = true) ||
                        cmd.aliases.any { it.equals(trigger, ignoreCase = true) }
                }
            )
            return
        }

        // Standard / Utility / Automation / AI commands
        val trigger = if (hasPrefix) firstToken else "/ask"
        val query = if (hasPrefix) input.substringAfter(" ", "").trim() else input

        val matchingCmd = commands.firstOrNull { cmd ->
            cmd.trigger.equals(trigger, ignoreCase = true) ||
                cmd.trigger.drop(1).equals(baseCmdName, ignoreCase = true) ||
                cmd.aliases.any { it.equals(trigger, ignoreCase = true) || it.drop(1).equals(baseCmdName, ignoreCase = true) }
        }
        if (matchingCmd != null && !matchingCmd.commandEnabled) {
            _uiState.value = _uiState.value.copy(
                policyBlockedReason = "Command ${matchingCmd.trigger} is globally disabled by owner."
            )
            return
        }

        val builtInProUtilityCmds = setOf(
            "menu", "help", "list", "ping", "alive", "status",
            "antilink", "antidelete", "anticall", "welcome", "goodbye", "sudo", "ban", "unban"
        )
        val isBuiltInUtility = hasPrefix && baseCmdName in builtInProUtilityCmds
        val isOwnerOnlyCmd = (matchingCmd?.ownerOnly == true) ||
            baseCmdName in setOf("antilink", "antidelete", "anticall", "welcome", "goodbye", "sudo", "ban", "unban")
        val usesWebSearch = if (isBuiltInUtility) false else (matchingCmd?.usesWebSearch ?: true)

        val gate = policyEnforcer.evaluateExecution(
            policy = policy,
            account = account,
            commandTrigger = trigger,
            requiresMediaDownload = false,
            requiresWebSearch = usesWebSearch,
            ownerOnly = isOwnerOnlyCmd
        )
        if (gate is PolicyCheckResult.Denied) {
            _uiState.value = _uiState.value.copy(policyBlockedReason = gate.reason)
            viewModelScope.launch {
                firestoreRepo.logEngagementEvent(
                    EngagementEvent(
                        ownerUid = currentUserId,
                        accountId = account?.id.orEmpty(),
                        e164Number = account?.e164Number ?: "none",
                        commandTrigger = trigger,
                        platform = "POLICY_GATE",
                        promptPreview = input.take(280),
                        responsePreview = gate.reason.take(480),
                        latencyMs = 0L,
                        status = EventStatus.BLOCKED.name
                    )
                )
            }
            return
        }

        val activeAccount = account!!
        if (isBuiltInUtility) {
            executeBuiltInProCommand(
                baseCmdName = baseCmdName,
                trigger = trigger,
                rawInput = input,
                arg = query,
                policy = policy,
                account = activeAccount,
                commands = commands,
                matchingCmd = matchingCmd,
                dispatchToWhatsApp = dispatchToWhatsApp
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningCommand = true,
                policyBlockedReason = null,
                feedbackMessage = null
            )

            val promptText = matchingCmd?.promptTemplate?.replace("{{query}}", query) ?: query
            val instructions = buildString {
                append("You are Honest Bot Pro (powered by Honest Tech Support +263 780549558). ")
                append("Operate behind WhatsApp account ${activeAccount.e164Number} (${activeAccount.displayName}). ")
                append("Use real-time web_search when answering for current internet and social media updates. ")
                append("Format clearly for WhatsApp (*bold*, short bullet points, direct source links).")
            }

            val aiResult = openAiMediaRepo.runLiveWebSearchCommand(
                prompt = promptText,
                systemInstructions = instructions,
                useWebSearch = usesWebSearch
            )

            aiResult.onSuccess { reply ->
                var sent = false
                var waMsgId: String? = null
                if (dispatchToWhatsApp && whatsAppRepo.isWhatsAppConfigured()) {
                    whatsAppRepo.sendTextMessage(
                        recipientE164OrGroupId = activeAccount.e164Number,
                        bodyText = reply.text,
                        customPhoneNumberId = activeAccount.customPhoneNumberId
                    ).onSuccess {
                        sent = true
                        waMsgId = it.messageId
                    }
                }

                firestoreRepo.incrementAccountUsage(activeAccount.id)
                if (matchingCmd != null) firestoreRepo.incrementCommandCount(matchingCmd.id)
                firestoreRepo.logEngagementEvent(
                    EngagementEvent(
                        ownerUid = currentUserId,
                        accountId = activeAccount.id,
                        e164Number = activeAccount.e164Number,
                        commandTrigger = trigger,
                        platform = if (sent) "WHATSAPP_CLOUD" else "OPENAI_WEB",
                        promptPreview = input.take(280),
                        responsePreview = reply.text.take(480),
                        latencyMs = reply.latencyMs,
                        status = if (sent) EventStatus.DELIVERED.name else EventStatus.PROCESSED.name
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isRunningCommand = false,
                    lastResult = com.example.model.ConsoleExecutionResult(
                        commandUsed = trigger,
                        prompt = input,
                        responseText = reply.text,
                        citations = reply.citations,
                        latencyMs = reply.latencyMs,
                        dispatchedViaWhatsApp = sent,
                        whatsappMessageId = waMsgId,
                        targetLabel = activeAccount.e164Number
                    ),
                    feedbackMessage = if (sent) "Dispatched to ${activeAccount.e164Number} via WhatsApp Cloud API" else null
                )
            }.onFailure { err ->
                firestoreRepo.logEngagementEvent(
                    EngagementEvent(
                        ownerUid = currentUserId,
                        accountId = activeAccount.id,
                        e164Number = activeAccount.e164Number,
                        commandTrigger = trigger,
                        platform = "OPENAI_WEB",
                        promptPreview = input.take(280),
                        responsePreview = (err.localizedMessage ?: "Failed").take(480),
                        latencyMs = 0L,
                        status = EventStatus.FAILED.name
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isRunningCommand = false,
                    feedbackMessage = err.localizedMessage ?: "OpenAI request failed"
                )
            }
        }
    }

    private fun executeBuiltInProCommand(
        baseCmdName: String,
        trigger: String,
        rawInput: String,
        arg: String,
        policy: OwnerPolicy,
        account: ConnectedAccount,
        commands: List<BotCommand>,
        matchingCmd: BotCommand?,
        dispatchToWhatsApp: Boolean
    ) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(
                isRunningCommand = true,
                policyBlockedReason = null,
                feedbackMessage = null
            )

            val normalizedArg = arg.trim().lowercase()
            var updatedPolicy: OwnerPolicy? = null
            val responseText = when (baseCmdName) {
                "menu", "help", "list" -> buildString {
                    appendLine("*${policy.botName.ifBlank { "Honest Bot Pro" }} • Command Directory*")
                    appendLine("Prefix: `${policy.commandPrefix}` • Mode: `${policy.botMode.uppercase()}` • Fleet: `${policy.globalFleetState}`")
                    appendLine("Active Link: ${account.displayName} (${account.e164Number})")
                    appendLine()
                    commands.filter { it.commandEnabled }
                        .groupBy { it.category }
                        .forEach { (cat, cmds) ->
                            appendLine("*$cat*")
                            cmds.forEach { cmd ->
                                val aliasText = if (cmd.aliases.isNotEmpty()) " (${cmd.aliases.joinToString(", ")})" else ""
                                appendLine("• `${cmd.trigger}`$aliasText — ${cmd.title}")
                            }
                            appendLine()
                        }
                    append("_Powered by Honest Tech Support (+263 780549558)_")
                }
                "ping", "alive", "status" -> {
                    val pulse = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    buildString {
                        appendLine("*${policy.botName.ifBlank { "Honest Bot Pro" }} is ONLINE*")
                        appendLine("• Pulse: ${pulse}ms • Prefix: `${policy.commandPrefix}` • Mode: `${policy.botMode}`")
                        appendLine("• Account: ${account.displayName} (${account.e164Number}) [${account.linkingMethod}]")
                        appendLine("• Anti-Link: ${policy.antiLinkMode} | Anti-Delete: ${policy.antiDeletePath} | Anti-Call: ${if (policy.antiCallEnabled) "ON" else "OFF"}")
                        appendLine("• Auto-View Status: ${if (policy.autoViewStatus) "ON" else "OFF"} | Always Online: ${if (policy.alwaysOnline) "ON" else "OFF"}")
                        append("_Honest Tech Support (+263 780549558)_")
                    }
                }
                "antilink" -> {
                    val mode = when (normalizedArg) {
                        "off", "false" -> "off"
                        "warn" -> "warn"
                        "on", "true", "delete", "" -> if (policy.antiLinkMode == "off" || normalizedArg == "delete") "delete" else if (normalizedArg.isBlank()) "off" else "delete"
                        else -> "delete"
                    }
                    updatedPolicy = policy.copy(antiLinkMode = mode)
                    "*Anti-Link Protection* updated to `$mode`."
                }
                "antidelete" -> {
                    val path = when (normalizedArg) {
                        "off", "false" -> "off"
                        "group" -> "group"
                        "all" -> "all"
                        "inbox", "on", "true", "" -> if (normalizedArg.isBlank() && policy.antiDeletePath != "off") "off" else "inbox"
                        else -> "inbox"
                    }
                    updatedPolicy = policy.copy(antiDeletePath = path)
                    "*Anti-Delete Recovery* updated to `$path`."
                }
                "anticall" -> {
                    val enabled = when (normalizedArg) {
                        "off", "false" -> false
                        "on", "true" -> true
                        else -> !policy.antiCallEnabled
                    }
                    updatedPolicy = policy.copy(antiCallEnabled = enabled)
                    "*Anti-Call Blocker* is now `${if (enabled) "ENABLED" else "DISABLED"}`."
                }
                "welcome" -> {
                    val enabled = when (normalizedArg) {
                        "off", "false" -> false
                        "on", "true" -> true
                        else -> !policy.welcomeEnabled
                    }
                    updatedPolicy = policy.copy(welcomeEnabled = enabled)
                    "*Group Welcome Automation* is now `${if (enabled) "ENABLED" else "DISABLED"}`."
                }
                "goodbye" -> {
                    val enabled = when (normalizedArg) {
                        "off", "false" -> false
                        "on", "true" -> true
                        else -> !policy.goodbyeEnabled
                    }
                    updatedPolicy = policy.copy(goodbyeEnabled = enabled)
                    "*Group Goodbye Automation* is now `${if (enabled) "ENABLED" else "DISABLED"}`."
                }
                "sudo" -> {
                    val num = arg.trim()
                    if (num.length < 5) {
                        "Provide a phone number after `$trigger` (e.g. `.sudo +263780549558`)."
                    } else {
                        val updatedList = (policy.sudoNumbers + num).distinct()
                        updatedPolicy = policy.copy(sudoNumbers = updatedList)
                        "Added `$num` to Owner SUDO Admins (${updatedList.size} total)."
                    }
                }
                "ban" -> {
                    val num = arg.trim()
                    if (num.length < 5) {
                        "Provide a phone number after `$trigger` (e.g. `.ban +263770000000`)."
                    } else {
                        val updatedList = (policy.bannedNumbers + num).distinct()
                        updatedPolicy = policy.copy(bannedNumbers = updatedList)
                        "Banned `$num` from Honest Bot Pro (${updatedList.size} total banned)."
                    }
                }
                "unban" -> {
                    val num = arg.trim()
                    val updatedList = policy.bannedNumbers.filterNot { it.equals(num, ignoreCase = true) }
                    updatedPolicy = policy.copy(bannedNumbers = updatedList)
                    "Removed `$num` from Banned Numbers."
                }
                else -> "Command $trigger executed."
            }

            updatedPolicy?.let { firestoreRepo.updateFullOwnerPolicy(it) }

            var sent = false
            var waMsgId: String? = null
            if (dispatchToWhatsApp && whatsAppRepo.isWhatsAppConfigured()) {
                whatsAppRepo.sendTextMessage(
                    recipientE164OrGroupId = account.e164Number,
                    bodyText = responseText,
                    customPhoneNumberId = account.customPhoneNumberId
                ).onSuccess {
                    sent = true
                    waMsgId = it.messageId
                }
            }

            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            firestoreRepo.incrementAccountUsage(account.id)
            if (matchingCmd != null) firestoreRepo.incrementCommandCount(matchingCmd.id)
            firestoreRepo.logEngagementEvent(
                EngagementEvent(
                    ownerUid = currentUserId,
                    accountId = account.id,
                    e164Number = account.e164Number,
                    commandTrigger = trigger,
                    platform = if (sent) "WHATSAPP_CLOUD" else "BOT_CORE",
                    promptPreview = rawInput.take(280),
                    responsePreview = responseText.take(480),
                    latencyMs = latency,
                    status = if (sent) EventStatus.DELIVERED.name else EventStatus.PROCESSED.name
                )
            )

            _uiState.value = _uiState.value.copy(
                isRunningCommand = false,
                lastResult = com.example.model.ConsoleExecutionResult(
                    commandUsed = trigger,
                    prompt = rawInput,
                    responseText = responseText,
                    citations = emptyList(),
                    latencyMs = latency,
                    dispatchedViaWhatsApp = sent,
                    whatsappMessageId = waMsgId,
                    targetLabel = account.e164Number
                ),
                feedbackMessage = if (sent) "Dispatched $trigger output to ${account.e164Number}" else null
            )
        }
    }

    private fun searchMediaForPlay(
        trigger: String,
        query: String,
        preferredType: MediaType,
        preferredPlatform: MediaSourcePlatform?,
        policy: OwnerPolicy,
        account: ConnectedAccount?,
        matchingCommand: BotCommand?
    ) {
        val gate = policyEnforcer.evaluateExecution(
            policy = policy,
            account = account,
            commandTrigger = trigger,
            requiresMediaDownload = true,
            requiresWebSearch = true
        )
        if (gate is PolicyCheckResult.Denied) {
            _uiState.value = _uiState.value.copy(policyBlockedReason = gate.reason)
            return
        }

        val activeAccount = account!!
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingMedia = true,
                policyBlockedReason = null,
                feedbackMessage = null
            )

            val start = System.currentTimeMillis()
            val searchRes = openAiMediaRepo.searchSocialMediaTracks(
                query = query,
                preferredType = preferredType,
                preferredPlatform = preferredPlatform
            )
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)

            searchRes.onSuccess { matches ->
                if (matchingCommand != null) firestoreRepo.incrementCommandCount(matchingCommand.id)
                firestoreRepo.logEngagementEvent(
                    EngagementEvent(
                        ownerUid = currentUserId,
                        accountId = activeAccount.id,
                        e164Number = activeAccount.e164Number,
                        commandTrigger = trigger,
                        platform = preferredPlatform?.name ?: "MULTI_SOCIAL",
                        promptPreview = "$trigger $query".take(280),
                        responsePreview = "Found ${matches.size} media results".take(480),
                        latencyMs = latency,
                        status = EventStatus.PROCESSED.name
                    )
                )
                _uiState.value = _uiState.value.copy(
                    isSearchingMedia = false,
                    mediaMatches = matches,
                    feedbackMessage = if (matches.isEmpty()) "No playable results found for \"$query\"" else "Found ${matches.size} tracks/videos across social platforms"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSearchingMedia = false,
                    feedbackMessage = err.localizedMessage ?: "Media search failed"
                )
            }
        }
    }

    fun downloadAndUploadMatch(
        match: MediaSearchMatch,
        policy: OwnerPolicy,
        targetAccount: ConnectedAccount?,
        uploadToWhatsApp: Boolean,
        customExtractorUrl: String? = null
    ) {
        val gate = policyEnforcer.evaluateExecution(
            policy = policy,
            account = targetAccount,
            commandTrigger = if (match.mediaType == MediaType.AUDIO) ".play" else ".video",
            requiresMediaDownload = true,
            requiresWebSearch = false
        )
        if (gate is PolicyCheckResult.Denied) {
            _uiState.value = _uiState.value.copy(policyBlockedReason = gate.reason)
            return
        }

        val account = targetAccount!!
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingMatchId = match.id,
                downloadProgress = 0.05f,
                policyBlockedReason = null
            )

            val jobIdResult = firestoreRepo.createMediaJob(
                MediaJob(
                    ownerUid = currentUserId,
                    accountId = account.id,
                    targetLabel = account.e164Number,
                    targetType = account.targetType,
                    commandTrigger = if (match.mediaType == MediaType.AUDIO) ".play" else ".video",
                    query = match.title,
                    sourcePlatform = match.platform.name,
                    mediaType = match.mediaType.name,
                    title = match.title,
                    sourceUrl = match.sourceUrl,
                    status = MediaJobStatus.DOWNLOADING.name
                )
            )
            val jobId = jobIdResult.getOrNull().orEmpty()

            val dlRes = openAiMediaRepo.resolveAndDownloadMedia(
                match = match,
                customExtractorUrl = customExtractorUrl,
                onProgress = { p ->
                    _uiState.value = _uiState.value.copy(downloadProgress = p)
                }
            )

            dlRes.onSuccess { outcome ->
                _uiState.value = _uiState.value.copy(
                    lastDownloadedFile = outcome,
                    lastDownloadedTitle = match.title
                )

                if (uploadToWhatsApp && whatsAppRepo.isWhatsAppConfigured()) {
                    if (jobId.isNotBlank()) {
                        firestoreRepo.updateMediaJobStatus(
                            jobId = jobId,
                            status = MediaJobStatus.UPLOADING.name,
                            localCachePath = outcome.file.absolutePath,
                            fileSizeBytes = outcome.byteCount
                        )
                    }
                    val waUploadRes = whatsAppRepo.uploadAndSendMedia(
                        recipientE164OrGroupId = account.e164Number,
                        file = outcome.file,
                        mimeType = outcome.mimeType,
                        requestedMediaType = match.mediaType,
                        caption = "${match.title} • Shared by Honest Bot Pro (+263 780549558)",
                        customPhoneNumberId = account.customPhoneNumberId
                    )
                    waUploadRes.onSuccess { wa ->
                        if (jobId.isNotBlank()) {
                            firestoreRepo.updateMediaJobStatus(
                                jobId = jobId,
                                status = MediaJobStatus.DELIVERED.name,
                                localCachePath = outcome.file.absolutePath,
                                fileSizeBytes = outcome.byteCount,
                                whatsappMediaId = wa.mediaId.orEmpty()
                            )
                        }
                        firestoreRepo.incrementAccountUsage(account.id)
                        val destLabel = if (TargetType.fromRaw(account.targetType) == TargetType.GROUP_TARGET) "Group" else "Inbox"
                        _uiState.value = _uiState.value.copy(
                            downloadingMatchId = null,
                            downloadProgress = 1f,
                            feedbackMessage = "Uploaded ${match.title} to WhatsApp $destLabel (${account.e164Number})!"
                        )
                    }.onFailure { waErr ->
                        if (jobId.isNotBlank()) {
                            firestoreRepo.updateMediaJobStatus(jobId, MediaJobStatus.FAILED.name, outcome.file.absolutePath, outcome.byteCount)
                        }
                        _uiState.value = _uiState.value.copy(
                            downloadingMatchId = null,
                            feedbackMessage = "Downloaded locally (${outcome.byteCount / 1024} KB), WhatsApp upload failed: ${waErr.message}"
                        )
                    }
                } else {
                    if (jobId.isNotBlank()) {
                        firestoreRepo.updateMediaJobStatus(
                            jobId = jobId,
                            status = MediaJobStatus.DELIVERED.name,
                            localCachePath = outcome.file.absolutePath,
                            fileSizeBytes = outcome.byteCount
                        )
                    }
                    // Also dispatch share link if WhatsApp configured and binary upload was skipped
                    _uiState.value = _uiState.value.copy(
                        downloadingMatchId = null,
                        downloadProgress = 1f,
                        feedbackMessage = "Downloaded ${match.title} (${outcome.byteCount / 1024} KB) ready for upload or device save."
                    )
                }
            }.onFailure { err ->
                // Fallback: if direct binary stream unavailable (no extractor configured), allow sharing rich track card to WhatsApp
                if (uploadToWhatsApp && whatsAppRepo.isWhatsAppConfigured()) {
                    val shareCard = "🎵 *${match.title}*\nSource: ${match.platform.label}\nLink: ${match.sourceUrl}\n\n_Powered by Honest Bot Pro • +263 780549558_"
                    whatsAppRepo.sendTextMessage(account.e164Number, shareCard, account.customPhoneNumberId)
                        .onSuccess {
                            if (jobId.isNotBlank()) firestoreRepo.updateMediaJobStatus(jobId, MediaJobStatus.DELIVERED.name)
                            _uiState.value = _uiState.value.copy(
                                downloadingMatchId = null,
                                feedbackMessage = "Sent ${match.title} track card to ${account.e164Number}. Add MEDIA_EXTRACTOR_URL in Platforms for direct MP3/MP4 binary upload."
                            )
                            return@launch
                        }
                }
                if (jobId.isNotBlank()) firestoreRepo.updateMediaJobStatus(jobId, MediaJobStatus.FAILED.name)
                _uiState.value = _uiState.value.copy(
                    downloadingMatchId = null,
                    feedbackMessage = err.localizedMessage ?: "Download failed"
                )
            }
        }
    }

    fun saveLastDownloadedToDevice() {
        val dl = _uiState.value.lastDownloadedFile ?: return
        viewModelScope.launch {
            openAiMediaRepo.exportToDeviceDownloads(dl.file, dl.mimeType)
                .onSuccess { msg -> _uiState.value = _uiState.value.copy(feedbackMessage = msg) }
                .onFailure { err -> _uiState.value = _uiState.value.copy(feedbackMessage = err.localizedMessage) }
        }
    }

    fun sendMediaLinkFallback(match: MediaSearchMatch, targetAccount: ConnectedAccount?) {
        val account = targetAccount ?: return
        viewModelScope.launch {
            val body = "🎵 *${match.title}*\nPlatform: ${match.platform.label}\nLink: ${match.sourceUrl}\n\n_Shared by Honest Bot Pro (+263 780549558)_"
            whatsAppRepo.sendTextMessage(account.e164Number, body, account.customPhoneNumberId)
                .onSuccess {
                    firestoreRepo.incrementAccountUsage(account.id)
                    _uiState.value = _uiState.value.copy(
                        feedbackMessage = "Shared ${match.title} link to ${account.e164Number}"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        feedbackMessage = it.localizedMessage ?: "WhatsApp send failed"
                    )
                }
        }
    }
}
