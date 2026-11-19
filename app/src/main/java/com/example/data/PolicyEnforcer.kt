package com.example.data

import com.example.model.AccountRole
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.GlobalFleetState
import com.example.model.OperatingMode
import com.example.model.OwnerPolicy
import com.example.model.PolicyCheckResult
import com.example.model.TargetType

class PolicyEnforcer {

    fun evaluateExecution(
        policy: OwnerPolicy,
        account: ConnectedAccount?,
        commandTrigger: String,
        requiresMediaDownload: Boolean,
        requiresWebSearch: Boolean,
        ownerOnly: Boolean = false
    ): PolicyCheckResult {
        val fleetState = GlobalFleetState.fromRaw(policy.globalFleetState)
        if (fleetState == GlobalFleetState.PAUSED_ALL) {
            return PolicyCheckResult.Denied("Global Kill-Switch active: all bot operations are paused by owner.")
        }
        if (fleetState == GlobalFleetState.MAINTENANCE) {
            return PolicyCheckResult.Denied("Maintenance Mode active: ${policy.maintenanceMessage}")
        }
        if (account == null) {
            return PolicyCheckResult.Denied("Select or link a WhatsApp account in Linking before running bot operations.")
        }

        val status = ConnectionStatus.fromRaw(account.connectionStatus)
        if (status != ConnectionStatus.LIVE) {
            return PolicyCheckResult.Denied("Account ${account.e164Number} is ${status.label}. Verify or resume link to operate.")
        }

        val role = AccountRole.fromRaw(account.role)
        if (role == AccountRole.BANNED || role == AccountRole.SUSPENDED) {
            return PolicyCheckResult.Denied("Account ${account.e164Number} is ${role.label} by owner administration.")
        }

        val accountDigits = account.e164Number.filter { it.isDigit() }
        val isBannedByPolicy = policy.bannedNumbers.any { banned ->
            val clean = banned.trim()
            val cleanDigits = clean.filter { it.isDigit() }
            clean.equals(account.e164Number, ignoreCase = true) ||
                (cleanDigits.length >= 6 && accountDigits == cleanDigits)
        }
        if (isBannedByPolicy) {
            return PolicyCheckResult.Denied("Account ${account.e164Number} is in Owner Banned Numbers list.")
        }

        val isSudo = role == AccountRole.VIP || policy.sudoNumbers.any { sudo ->
            val clean = sudo.trim()
            val cleanDigits = clean.filter { it.isDigit() }
            clean.equals(account.e164Number, ignoreCase = true) ||
                (cleanDigits.length >= 6 && accountDigits == cleanDigits)
        }

        if (fleetState == GlobalFleetState.VIP_LOCKDOWN && !isSudo) {
            return PolicyCheckResult.Denied("VIP Lockdown active: only VIP or SUDO accounts may execute commands right now.")
        }

        val targetType = TargetType.fromRaw(account.targetType)
        when (policy.botMode.lowercase()) {
            "private" -> if (!isSudo) {
                return PolicyCheckResult.Denied("Private Bot Mode active: only Owner VIP or SUDO numbers can run commands.")
            }
            "inbox" -> if (targetType == TargetType.GROUP_TARGET) {
                return PolicyCheckResult.Denied("Inbox-Only Mode active: group target execution is blocked by owner policy.")
            }
            "groups" -> if (targetType == TargetType.PERSONAL_INBOX && !isSudo) {
                return PolicyCheckResult.Denied("Groups-Only Mode active: personal inbox execution is blocked by owner policy.")
            }
        }

        if (ownerOnly && !isSudo) {
            return PolicyCheckResult.Denied("Command $commandTrigger is Owner/SUDO only.")
        }

        if (!account.botEnabled) {
            return PolicyCheckResult.Denied("Bot engine is toggled OFF for ${account.e164Number} by owner.")
        }

        val mode = OperatingMode.fromRaw(account.operatingMode)
        if (mode == OperatingMode.MONITOR_ONLY) {
            return PolicyCheckResult.Denied("Account ${account.e164Number} is in Silent Monitor mode (responses disabled).")
        }

        val normalizedTrigger = commandTrigger.trim().lowercase().substringBefore(" ")
        if (account.blockedCommands.any { it.lowercase() == normalizedTrigger }) {
            return PolicyCheckResult.Denied("Command $normalizedTrigger is restricted for ${account.displayName} by owner.")
        }
        if (requiresMediaDownload && !account.mediaDownloadEnabled) {
            return PolicyCheckResult.Denied("Media downloads (.play / .video / .dl) are disabled for ${account.displayName}.")
        }
        if (requiresWebSearch && !account.webSearchEnabled) {
            return PolicyCheckResult.Denied("Live web search is disabled for ${account.displayName}.")
        }
        if (targetType == TargetType.GROUP_TARGET && !account.groupDispatchEnabled) {
            return PolicyCheckResult.Denied("Group dispatch is disabled for ${account.displayName} by owner.")
        }
        if (account.messagesUsedToday >= account.dailyQuota && account.dailyQuota > 0) {
            return PolicyCheckResult.Denied("Daily quota (${account.dailyQuota}) reached for ${account.e164Number}. Reset quota in Admin controls.")
        }

        return PolicyCheckResult.Allowed
    }
}
