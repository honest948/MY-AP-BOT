package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BotConfigGenerator
import com.example.data.BotFirestoreRepository
import com.example.data.OpenAiAndMediaRepository
import com.example.data.WhatsAppCloudRepository
import com.example.model.AccountRole
import com.example.model.AdminAuditLog
import com.example.model.ApiDiagnostics
import com.example.model.BotCommand
import com.example.model.CommandCategory
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.EngagementEvent
import com.example.model.GlobalFleetState
import com.example.model.LinkingMethod
import com.example.model.MediaJob
import com.example.model.OperatingMode
import com.example.model.OwnerPolicy
import com.example.model.PlatformIntegration
import com.example.model.TargetType
import com.example.model.UiState
import com.example.model.WebPairCodeResult
import com.example.model.WebhookTestOutcome
import com.example.model.WhatsAppConnectionResult
import com.example.security.SessionCryptoManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommandCenterViewModel(
    private val firestoreRepo: BotFirestoreRepository,
    private val openAiMediaRepo: OpenAiAndMediaRepository,
    private val whatsAppRepo: WhatsAppCloudRepository,
    private val cryptoManager: SessionCryptoManager,
    private val currentUserId: String
) : ViewModel() {

    val ownerPolicyState: StateFlow<UiState<OwnerPolicy>> =
        firestoreRepo.observeOwnerPolicy().toUiState("ownerPolicy")

    val accountsState: StateFlow<UiState<List<ConnectedAccount>>> =
        firestoreRepo.observeAccounts().toUiState("accounts")

    val commandsState: StateFlow<UiState<List<BotCommand>>> =
        firestoreRepo.observeCommands().toUiState("commands")

    val mediaJobsState: StateFlow<UiState<List<MediaJob>>> =
        firestoreRepo.observeMediaJobs().toUiState("mediaJobs")

    val platformsState: StateFlow<UiState<List<PlatformIntegration>>> =
        firestoreRepo.observePlatforms().toUiState("platforms")

    val eventsState: StateFlow<UiState<List<EngagementEvent>>> =
        firestoreRepo.observeEvents().toUiState("events")

    val auditLogsState: StateFlow<UiState<List<AdminAuditLog>>> =
        firestoreRepo.observeAuditLogs().toUiState("auditLogs")

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _linkingError = MutableStateFlow<String?>(null)
    val linkingError: StateFlow<String?> = _linkingError.asStateFlow()

    private val _whatsAppConnection = MutableStateFlow<WhatsAppConnectionResult?>(null)
    val whatsAppConnection: StateFlow<WhatsAppConnectionResult?> = _whatsAppConnection.asStateFlow()

    private val _webhookTestOutcome = MutableStateFlow<WebhookTestOutcome?>(null)
    val webhookTestOutcome: StateFlow<WebhookTestOutcome?> = _webhookTestOutcome.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val apiDiagnostics: ApiDiagnostics = ApiDiagnostics(
        openAiConfigured = openAiMediaRepo.isOpenAiConfigured(),
        whatsAppConfigured = whatsAppRepo.isWhatsAppConfigured(),
        mediaExtractorConfigured = openAiMediaRepo.isMediaExtractorConfigured(),
        phoneNumberIdMasked = whatsAppRepo.maskedPhoneNumberId()
    )

    val isHardwareVaultReady: Boolean = cryptoManager.isHardwareKeystoreReady()

    init {
        retryDefaultsSync()
    }

    fun retryDefaultsSync() {
        viewModelScope.launch {
            _linkingError.value = null
            val code = cryptoManager.generatePairingCode()
            val fp = cryptoManager.generateSessionFingerprint(currentUserId, "+263780549558", code)
            cryptoManager.storeEncryptedSessionToken("acc_honest_support", "$code:$fp")
            firestoreRepo.ensureDefaultsSeeded(
                defaultFingerprint = fp,
                defaultPairingCode = code,
                waConfigured = whatsAppRepo.isWhatsAppConfigured(),
                mediaExtractorConfigured = openAiMediaRepo.isMediaExtractorConfigured()
            ).onFailure { err ->
                val msg = err.localizedMessage ?: "Firestore sync error. Check internet connection."
                _linkingError.value = msg
                _statusMessage.value = msg
            }
        }
    }

    fun clearLinkingError() {
        _linkingError.value = null
    }

    fun testWhatsAppCloudConnection(customPhoneNumberId: String = "") {
        viewModelScope.launch {
            _isBusy.value = true
            _linkingError.value = null
            val result = whatsAppRepo.verifyCloudConnection(customPhoneNumberId)
            _whatsAppConnection.value = result
            _isBusy.value = false
            if (!result.connected) {
                _linkingError.value = result.message
            } else {
                _statusMessage.value = result.message
            }
        }
    }

    fun pingAccountConnection(account: ConnectedAccount) {
        viewModelScope.launch {
            _isBusy.value = true
            _linkingError.value = null
            if (whatsAppRepo.isWhatsAppConfigured()) {
                val res = whatsAppRepo.verifyCloudConnection(account.customPhoneNumberId)
                _whatsAppConnection.value = res
                if (res.connected) {
                    firestoreRepo.updateAccountFields(
                        account.id,
                        mapOf("lastActiveAtMs" to System.currentTimeMillis())
                    )
                    _statusMessage.value = "${account.displayName} verified via ${res.displayPhoneNumber} (${res.latencyMs}ms)"
                } else {
                    _linkingError.value = res.message
                    _statusMessage.value = res.message
                }
            } else {
                val hasVaultToken = cryptoManager.readEncryptedSessionToken(account.id) != null
                if (!hasVaultToken) {
                    cryptoManager.storeEncryptedSessionToken(
                        account.id,
                        "${account.pairingCode}:${account.sessionFingerprint}"
                    )
                }
                firestoreRepo.updateAccountFields(
                    account.id,
                    mapOf("lastActiveAtMs" to System.currentTimeMillis())
                ).onSuccess {
                    _statusMessage.value = "Encrypted Vault Session active for ${account.e164Number} (add WhatsApp Secrets for live Cloud API ping)"
                }.onFailure { err ->
                    _linkingError.value = err.localizedMessage ?: "Failed to verify Firestore session"
                }
            }
            _isBusy.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setGlobalFleetState(newState: GlobalFleetState, currentPolicy: OwnerPolicy) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = firestoreRepo.updateGlobalPolicy(
                fleetState = newState,
                maintenanceMessage = currentPolicy.maintenanceMessage,
                allowNewLinkRequests = if (newState == GlobalFleetState.VIP_LOCKDOWN) false else currentPolicy.allowNewLinkRequests,
                defaultDailyQuota = currentPolicy.defaultDailyQuota,
                maxMediaSizeMb = currentPolicy.maxMediaSizeMb
            )
            _isBusy.value = false
            result.onSuccess {
                firestoreRepo.logAdminAudit(
                    actionType = "GLOBAL_STATE_CHANGE",
                    targetAccountId = "FLEET",
                    targetLabel = "Global Fleet",
                    details = "Owner switched global bot fleet mode to ${newState.label}"
                )
                _statusMessage.value = "Fleet mode updated to ${newState.label}"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update fleet state"
            }
        }
    }

    fun saveBotAutomationPolicy(updatedPolicy: OwnerPolicy) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = firestoreRepo.updateFullOwnerPolicy(updatedPolicy)
            _isBusy.value = false
            res.onSuccess {
                firestoreRepo.logAdminAudit(
                    actionType = "BOT_AUTOMATION_SYNC",
                    targetAccountId = "FLEET",
                    targetLabel = updatedPolicy.botName.ifBlank { "Honest Bot Pro" },
                    details = "Prefix=${updatedPolicy.commandPrefix}, Mode=${updatedPolicy.botMode}, AntiLink=${updatedPolicy.antiLinkMode}, AntiDel=${updatedPolicy.antiDeletePath}, Welcome=${updatedPolicy.welcomeEnabled}"
                )
                _statusMessage.value = "Pro bot automation & anti-protection policy saved"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to save bot automation policy"
            }
        }
    }

    fun buildBridgeConfigEnv(policy: OwnerPolicy, accounts: List<ConnectedAccount>): String =
        BotConfigGenerator.generateBridgeConfigEnv(policy, accounts)

    fun saveGlobalPolicySettings(
        fleetState: GlobalFleetState,
        maintenanceMessage: String,
        allowNewLinkRequests: Boolean,
        defaultDailyQuota: Long,
        maxMediaSizeMb: Long
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = firestoreRepo.updateGlobalPolicy(
                fleetState = fleetState,
                maintenanceMessage = maintenanceMessage,
                allowNewLinkRequests = allowNewLinkRequests,
                defaultDailyQuota = defaultDailyQuota,
                maxMediaSizeMb = maxMediaSizeMb
            )
            _isBusy.value = false
            res.onSuccess {
                firestoreRepo.logAdminAudit(
                    actionType = "GLOBAL_STATE_CHANGE",
                    targetAccountId = "FLEET",
                    targetLabel = "Policy Settings",
                    details = "Updated default quota ($defaultDailyQuota), max media (${maxMediaSizeMb}MB), new links=$allowNewLinkRequests"
                )
                _statusMessage.value = "Owner fleet policy saved"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to save policy"
            }
        }
    }

    fun normalizeSubscriberDigits(countryCode: String, rawNumber: String): String {
        val codeDigits = countryCode.filter { it.isDigit() }
        var digits = rawNumber.filter { it.isDigit() }
        if (codeDigits.isNotEmpty() && digits.startsWith(codeDigits) && digits.length >= codeDigits.length + 6) {
            digits = digits.removePrefix(codeDigits)
        }
        digits = digits.trimStart('0')
        return digits
    }

    fun createAccountLink(
        targetType: TargetType,
        countryCode: String,
        rawNumberOrGroupId: String,
        displayName: String,
        role: AccountRole,
        operatingMode: OperatingMode,
        customPhoneNumberId: String,
        dailyQuota: Long,
        dispatchCodeViaWhatsApp: Boolean,
        linkingMethod: LinkingMethod = LinkingMethod.CLOUD_API,
        webPairBridgeUrl: String = "",
        sessionIdInput: String = "",
        onCreated: (ConnectedAccount, String?) -> Unit
    ) {
        _linkingError.value = null
        val cleanCode = countryCode.trim().let {
            val digitsOnly = it.filter { ch -> ch.isDigit() }
            if (digitsOnly.isNotEmpty()) "+$digitsOnly" else "+263"
        }
        val cleanDigits = if (targetType == TargetType.PERSONAL_INBOX) {
            normalizeSubscriberDigits(cleanCode, rawNumberOrGroupId)
        } else {
            rawNumberOrGroupId.trim()
        }

        if (targetType == TargetType.PERSONAL_INBOX && cleanDigits.length < 6) {
            val msg = "Enter a valid WhatsApp subscriber number (at least 6 digits after $cleanCode)."
            _linkingError.value = msg
            _statusMessage.value = msg
            return
        }
        if (targetType == TargetType.GROUP_TARGET && cleanDigits.length < 5) {
            val msg = "Enter a valid WhatsApp Group ID or broadcast handle (at least 5 characters)."
            _linkingError.value = msg
            _statusMessage.value = msg
            return
        }

        val e164 = if (targetType == TargetType.PERSONAL_INBOX) {
            "$cleanCode$cleanDigits"
        } else {
            cleanDigits
        }

        viewModelScope.launch {
            _isBusy.value = true
            var webPairNote: String? = null
            var effectivePairingCode = cryptoManager.generatePairingCode()
            var effectiveSessionId = sessionIdInput.trim()

            if (linkingMethod == LinkingMethod.WEB_PAIR && webPairBridgeUrl.isNotBlank() && targetType == TargetType.PERSONAL_INBOX) {
                val bridgeRes: WebPairCodeResult = whatsAppRepo.requestWebPairCode(webPairBridgeUrl, e164)
                if (bridgeRes.success && bridgeRes.pairingCode.isNotBlank()) {
                    effectivePairingCode = bridgeRes.pairingCode
                    if (bridgeRes.sessionId.isNotBlank()) {
                        effectiveSessionId = bridgeRes.sessionId
                    }
                    webPairNote = "Live WebPair Bridge code received: $effectivePairingCode (${bridgeRes.latencyMs}ms). Enter this in WhatsApp > Linked Devices > Link with phone number."
                } else {
                    webPairNote = "WebPair Bridge unavailable (${bridgeRes.message}). Generated local encrypted fallback code $effectivePairingCode."
                }
            }

            val fingerprint = cryptoManager.generateSessionFingerprint(currentUserId, e164, effectivePairingCode)
            val newAccount = ConnectedAccount(
                ownerUid = currentUserId,
                targetType = targetType.name,
                countryCode = cleanCode,
                phoneNumber = cleanDigits,
                e164Number = e164,
                displayName = displayName.trim().ifBlank { e164 },
                pairingCode = effectivePairingCode,
                sessionFingerprint = fingerprint,
                pairingExpiresAtMs = System.currentTimeMillis() + 15 * 60 * 1000L,
                connectionStatus = if (effectiveSessionId.isNotBlank() && linkingMethod == LinkingMethod.WEB_PAIR) {
                    ConnectionStatus.LIVE.name
                } else {
                    ConnectionStatus.AWAITING_CODE.name
                },
                operatingMode = operatingMode.name,
                customPhoneNumberId = customPhoneNumberId.trim(),
                role = role.name,
                botEnabled = true,
                autoReplyEnabled = true,
                mediaDownloadEnabled = role != AccountRole.RESTRICTED,
                webSearchEnabled = true,
                groupDispatchEnabled = true,
                blockedCommands = emptyList(),
                dailyQuota = dailyQuota,
                messagesUsedToday = 0L,
                lastActiveAtMs = System.currentTimeMillis(),
                linkingMethod = linkingMethod.name,
                sessionId = effectiveSessionId,
                antiLinkEnabled = true,
                antiDeleteEnabled = true,
                welcomeEnabled = targetType == TargetType.GROUP_TARGET
            )

            val res = firestoreRepo.createLinkedAccount(newAccount)
            res.onSuccess { newId ->
                val createdAccount = newAccount.copy(id = newId)
                cryptoManager.storeEncryptedSessionToken(newId, "$effectivePairingCode:$fingerprint:$effectiveSessionId")
                firestoreRepo.logAdminAudit(
                    actionType = "CREATE_LINK",
                    targetAccountId = newId,
                    targetLabel = e164,
                    details = "Linked via ${linkingMethod.label} code=$effectivePairingCode (${role.label})"
                )

                var deliveryNote: String? = webPairNote
                if (dispatchCodeViaWhatsApp && linkingMethod == LinkingMethod.CLOUD_API) {
                    if (whatsAppRepo.isWhatsAppConfigured()) {
                        val waRes = whatsAppRepo.sendTextMessage(
                            recipientE164OrGroupId = e164,
                            bodyText = "Honest Bot Pro Linking Code: *$effectivePairingCode*\nFingerprint: `${fingerprint.take(12)}`\nReply or confirm in dashboard to activate your encrypted bot session. Support: +263 780549558",
                            customPhoneNumberId = customPhoneNumberId.trim()
                        )
                        waRes.onSuccess {
                            deliveryNote = "Linking code $effectivePairingCode sent to $e164 via WhatsApp Cloud API."
                            _statusMessage.value = deliveryNote
                        }.onFailure { waErr ->
                            deliveryNote = "Code $effectivePairingCode saved! Cloud API dispatch note: ${waErr.message}"
                            _linkingError.value = deliveryNote
                            _statusMessage.value = deliveryNote
                        }
                    } else {
                        deliveryNote = "Code $effectivePairingCode created! WhatsApp Cloud API Secrets aren't set yet — tap 'Send via WhatsApp' below or verify directly."
                        _statusMessage.value = "Encrypted linking code $effectivePairingCode generated for $e164"
                    }
                } else if (deliveryNote == null) {
                    deliveryNote = if (effectiveSessionId.isNotBlank()) {
                        "Linked $e164 live with Session ID."
                    } else {
                        "Code $effectivePairingCode generated locally for $e164."
                    }
                    _statusMessage.value = deliveryNote
                } else {
                    _statusMessage.value = deliveryNote
                }
                _isBusy.value = false
                onCreated(createdAccount, deliveryNote)
            }.onFailure { err ->
                _isBusy.value = false
                val msg = err.localizedMessage ?: "Failed to create link in Firestore"
                _linkingError.value = msg
                _statusMessage.value = msg
            }
        }
    }

    fun approveAndVerifyLink(account: ConnectedAccount, enteredCode: String) {
        viewModelScope.launch {
            _linkingError.value = null
            val normalizedInput = enteredCode.replace("-", "").replace(" ", "").trim().uppercase()
            val normalizedExpected = account.pairingCode.replace("-", "").replace(" ", "").trim().uppercase()
            if (normalizedInput.isNotBlank() && normalizedInput != normalizedExpected) {
                val msg = "Code mismatch. Expected ${account.pairingCode}."
                _linkingError.value = msg
                _statusMessage.value = msg
                return@launch
            }
            _isBusy.value = true
            val res = firestoreRepo.updateAccountFields(
                account.id,
                mapOf(
                    "connectionStatus" to ConnectionStatus.LIVE.name,
                    "botEnabled" to true,
                    "lastActiveAtMs" to System.currentTimeMillis()
                )
            )
            _isBusy.value = false
            res.onSuccess {
                cryptoManager.storeEncryptedSessionToken(
                    account.id,
                    "${account.pairingCode}:${account.sessionFingerprint}"
                )
                firestoreRepo.logAdminAudit(
                    actionType = "APPROVE_LINK",
                    targetAccountId = account.id,
                    targetLabel = account.e164Number,
                    details = "Owner verified linking code ${account.pairingCode}; bot active behind account."
                )
                _statusMessage.value = "${account.displayName} is now LIVE • LINKED"
            }.onFailure {
                val msg = it.localizedMessage ?: "Failed to verify link"
                _linkingError.value = msg
                _statusMessage.value = msg
            }
        }
    }

    fun rotatePairingCode(account: ConnectedAccount) {
        viewModelScope.launch {
            val newCode = cryptoManager.generatePairingCode()
            val newFp = cryptoManager.generateSessionFingerprint(currentUserId, account.e164Number, newCode)
            val res = firestoreRepo.updateAccountFields(
                account.id,
                mapOf(
                    "pairingCode" to newCode,
                    "sessionFingerprint" to newFp,
                    "pairingExpiresAtMs" to (System.currentTimeMillis() + 15 * 60 * 1000L),
                    "connectionStatus" to ConnectionStatus.AWAITING_CODE.name
                )
            )
            res.onSuccess {
                cryptoManager.storeEncryptedSessionToken(account.id, "$newCode:$newFp")
                firestoreRepo.logAdminAudit(
                    actionType = "ROTATE_CODE",
                    targetAccountId = account.id,
                    targetLabel = account.e164Number,
                    details = "Rotated session key & linking code to $newCode"
                )
                _statusMessage.value = "New linking code: $newCode"
            }
        }
    }

    fun updateOwnerAccountControls(
        account: ConnectedAccount,
        connectionStatus: ConnectionStatus,
        role: AccountRole,
        operatingMode: OperatingMode,
        botEnabled: Boolean,
        autoReplyEnabled: Boolean,
        mediaDownloadEnabled: Boolean,
        webSearchEnabled: Boolean,
        groupDispatchEnabled: Boolean,
        dailyQuota: Long,
        blockedCommands: List<String>,
        antiLinkEnabled: Boolean = account.antiLinkEnabled,
        antiDeleteEnabled: Boolean = account.antiDeleteEnabled,
        welcomeEnabled: Boolean = account.welcomeEnabled,
        sessionId: String = account.sessionId
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = firestoreRepo.updateAccountFields(
                account.id,
                mapOf(
                    "connectionStatus" to connectionStatus.name,
                    "role" to role.name,
                    "operatingMode" to operatingMode.name,
                    "botEnabled" to botEnabled,
                    "autoReplyEnabled" to autoReplyEnabled,
                    "mediaDownloadEnabled" to mediaDownloadEnabled,
                    "webSearchEnabled" to webSearchEnabled,
                    "groupDispatchEnabled" to groupDispatchEnabled,
                    "dailyQuota" to dailyQuota.coerceIn(0L, 100000L),
                    "blockedCommands" to blockedCommands.take(25),
                    "antiLinkEnabled" to antiLinkEnabled,
                    "antiDeleteEnabled" to antiDeleteEnabled,
                    "welcomeEnabled" to welcomeEnabled,
                    "sessionId" to sessionId.trim().take(500)
                )
            )
            _isBusy.value = false
            res.onSuccess {
                val action = when (role) {
                    AccountRole.BANNED -> "BAN_USER"
                    AccountRole.SUSPENDED -> "SUSPEND_USER"
                    else -> "TOGGLE_FEATURE"
                }
                firestoreRepo.logAdminAudit(
                    actionType = action,
                    targetAccountId = account.id,
                    targetLabel = account.e164Number,
                    details = "Status=${connectionStatus.label}, Role=${role.label}, Media=$mediaDownloadEnabled, AntiLink=$antiLinkEnabled, Quota=$dailyQuota"
                )
                _statusMessage.value = "Admin controls saved for ${account.displayName}"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Update failed"
            }
        }
    }

    fun resetAccountDailyQuota(account: ConnectedAccount) {
        viewModelScope.launch {
            firestoreRepo.updateAccountFields(account.id, mapOf("messagesUsedToday" to 0L))
                .onSuccess {
                    firestoreRepo.logAdminAudit(
                        actionType = "RESET_QUOTA",
                        targetAccountId = account.id,
                        targetLabel = account.e164Number,
                        details = "Owner reset daily message counter to 0"
                    )
                    _statusMessage.value = "Daily usage reset for ${account.displayName}"
                }
        }
    }

    fun revokeAndDeleteAccount(account: ConnectedAccount) {
        viewModelScope.launch {
            cryptoManager.removeEncryptedSessionToken(account.id)
            firestoreRepo.deleteAccount(account.id).onSuccess {
                firestoreRepo.logAdminAudit(
                    actionType = "REVOKE_SESSION",
                    targetAccountId = account.id,
                    targetLabel = account.e164Number,
                    details = "Permanently revoked & deleted linked WhatsApp account"
                )
                _statusMessage.value = "Removed ${account.displayName}"
            }
        }
    }

    fun runBulkAdminAction(
        selectedAccounts: List<ConnectedAccount>,
        targetStatus: ConnectionStatus,
        botEnabled: Boolean
    ) {
        if (selectedAccounts.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            selectedAccounts.forEach { acc ->
                firestoreRepo.updateAccountFields(
                    acc.id,
                    mapOf(
                        "connectionStatus" to targetStatus.name,
                        "botEnabled" to botEnabled
                    )
                )
            }
            firestoreRepo.logAdminAudit(
                actionType = "BULK_ADMIN",
                targetAccountId = "MULTI_${selectedAccounts.size}",
                targetLabel = "${selectedAccounts.size} accounts",
                details = "Bulk set status=${targetStatus.label}, botEnabled=$botEnabled"
            )
            _isBusy.value = false
            _statusMessage.value = "Updated ${selectedAccounts.size} accounts to ${targetStatus.label}"
        }
    }

    fun sendAdminBroadcast(accounts: List<ConnectedAccount>, messageText: String) {
        if (messageText.isBlank()) {
            _statusMessage.value = "Enter a broadcast message first."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val activeTargets = accounts.filter {
                ConnectionStatus.fromRaw(it.connectionStatus) == ConnectionStatus.LIVE
            }
            var sentViaApi = 0
            var failedApi = 0
            activeTargets.forEach { acc ->
                if (whatsAppRepo.isWhatsAppConfigured()) {
                    whatsAppRepo.sendTextMessage(acc.e164Number, messageText, acc.customPhoneNumberId)
                        .onSuccess { sentViaApi++ }
                        .onFailure { failedApi++ }
                }
            }
            firestoreRepo.logAdminAudit(
                actionType = "BROADCAST",
                targetAccountId = "FLEET_${activeTargets.size}",
                targetLabel = "${activeTargets.size} LIVE targets",
                details = "Owner broadcast: \"${messageText.take(80)}\" (API sent=$sentViaApi, failed=$failedApi)"
            )
            _isBusy.value = false
            _statusMessage.value = if (whatsAppRepo.isWhatsAppConfigured()) {
                "Broadcast sent: $sentViaApi delivered, $failedApi failed across ${activeTargets.size} live accounts."
            } else {
                "Broadcast logged for ${activeTargets.size} live accounts (add WHATSAPP_ACCESS_TOKEN in Secrets to dispatch live)."
            }
        }
    }

    fun saveCustomCommand(
        trigger: String,
        title: String,
        description: String,
        category: CommandCategory,
        promptTemplate: String,
        usesWebSearch: Boolean
    ) {
        val cleanTrigger = trigger.trim().let {
            if (it.startsWith("/") || it.startsWith(".")) it else "/$it"
        }
        viewModelScope.launch {
            firestoreRepo.saveCustomCommand(
                BotCommand(
                    trigger = cleanTrigger,
                    title = title.ifBlank { cleanTrigger },
                    description = description,
                    category = category.name,
                    promptTemplate = promptTemplate.ifBlank { "{{query}}" },
                    usesWebSearch = usesWebSearch,
                    commandEnabled = true
                )
            ).onSuccess {
                firestoreRepo.logAdminAudit(
                    actionType = "COMMAND_UPDATE",
                    targetAccountId = "COMMANDS",
                    targetLabel = cleanTrigger,
                    details = "Added custom command $cleanTrigger (${category.label})"
                )
                _statusMessage.value = "Command $cleanTrigger saved"
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to save command"
            }
        }
    }

    fun toggleCommand(command: BotCommand, enabled: Boolean) {
        viewModelScope.launch {
            firestoreRepo.toggleCommandEnabled(command.id, enabled)
        }
    }

    fun updatePlatformConfig(
        platform: PlatformIntegration,
        endpointOrHandle: String,
        autoSyncEnabled: Boolean,
        secretInput: String
    ) {
        viewModelScope.launch {
            val hash = if (secretInput.isNotBlank()) {
                cryptoManager.sha256Short(secretInput)
            } else {
                platform.webhookSecretHash
            }
            firestoreRepo.updatePlatform(
                platformId = platform.id,
                status = if (endpointOrHandle.isNotBlank()) "CONNECTED" else "CONFIGURED",
                endpointOrHandle = endpointOrHandle,
                autoSyncEnabled = autoSyncEnabled,
                webhookSecretHash = hash
            ).onSuccess {
                _statusMessage.value = "${platform.name} updated"
            }
        }
    }

    fun testPlatformWebhook(url: String, secret: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val payload = """{"app":"Honest Bot Pro","support":"+263780549558","event":"ping","timestamp":${System.currentTimeMillis()}}"""
            val outcome = whatsAppRepo.testOutboundWebhook(url, secret, "honest_bot.ping", payload)
            _webhookTestOutcome.value = outcome
            _isBusy.value = false
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private companion object {
        const val TAG = "CommandCenterVM"
    }
}
