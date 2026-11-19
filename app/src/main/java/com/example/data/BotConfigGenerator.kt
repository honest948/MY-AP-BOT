package com.example.data

import com.example.model.AccountRole
import com.example.model.ConnectedAccount
import com.example.model.OwnerPolicy

object BotConfigGenerator {

    fun generateBridgeConfigEnv(
        policy: OwnerPolicy,
        accounts: List<ConnectedAccount>
    ): String {
        val ownerPhone = accounts
            .firstOrNull { AccountRole.fromRaw(it.role) == AccountRole.VIP }
            ?.e164Number
            ?.filter { it.isDigit() }
            ?: "263780549558"

        val sessionId = policy.sessionId.ifBlank {
            accounts.firstOrNull { it.sessionId.isNotBlank() }?.sessionId.orEmpty()
        }

        val sudoCsv = policy.sudoNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")

        val bannedCsv = policy.bannedNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")

        return buildString {
            appendLine("# Honest Bot Pro — Baileys / Node.js Bridge Configuration")
            appendLine("# Powered by Honest Tech Support (+263 780549558)")
            appendLine("BOT_NAME=\"${sanitizeLine(policy.botName.ifBlank { "Honest Bot Pro" })}\"")
            appendLine("OWNER_NAME=\"Honest Tech Support\"")
            appendLine("OWNER_NUMBER=\"$ownerPhone\"")
            appendLine("PREFIX=\"${sanitizeLine(policy.commandPrefix.ifBlank { "." })}\"")
            appendLine("MODE=\"${policy.botMode}\"")
            appendLine("SESSION_ID=\"${sanitizeLine(sessionId)}\"")
            appendLine("WEBPAIR=\"${sanitizeLine(policy.webPairBridgeUrl)}\"")
            appendLine("SUDO=\"$sudoCsv\"")
            appendLine("BANNED=\"$bannedCsv\"")
            appendLine()
            appendLine("# Presence & Automation")
            appendLine("READ_MESSAGE=\"${policy.readMessages}\"")
            appendLine("ALWAYS_ONLINE=\"${policy.alwaysOnline}\"")
            appendLine("AUTO_TYPING=\"${policy.autoTyping}\"")
            appendLine("AUTO_RECORDING=\"${policy.autoRecording}\"")
            appendLine("AUTO_REACT=\"${policy.autoReact}\"")
            appendLine("OWNER_REACT=\"${policy.ownerReact}\"")
            appendLine("AUTO_VIEW_STATUS=\"${policy.autoViewStatus}\"")
            appendLine("AUTO_LIKE_STATUS=\"${policy.autoLikeStatus}\"")
            appendLine("AUTO_DOWNLOADER=\"${policy.autoDownloader}\"")
            appendLine()
            appendLine("# Anti-Abuse & Security")
            appendLine("ANTI_LINK=\"${policy.antiLinkMode}\"")
            appendLine("ANTI_STATUS=\"${policy.antiStatusEnabled}\"")
            appendLine("ANTI_DELETE=\"${policy.antiDeletePath != "off"}\"")
            appendLine("ANTI_DELETE_PATH=\"${policy.antiDeletePath}\"")
            appendLine("ANTI_EDIT=\"${policy.antiEditPath != "off"}\"")
            appendLine("ANTIEDIT_PATH=\"${policy.antiEditPath}\"")
            appendLine("ANTI_CALL=\"${policy.antiCallEnabled}\"")
            appendLine("REJECT_MSG=\"${sanitizeLine(policy.callRejectMessage)}\"")
            appendLine()
            appendLine("# Group Governance")
            appendLine("WELCOME=\"${policy.welcomeEnabled}\"")
            appendLine("GOODBYE=\"${policy.goodbyeEnabled}\"")
            appendLine("WELCOME_MESSAGE=\"${sanitizeLine(policy.welcomeMessage)}\"")
            appendLine("GOODBYE_MESSAGE=\"${sanitizeLine(policy.goodbyeMessage)}\"")
        }
    }

    private fun sanitizeLine(raw: String): String =
        raw.replace("\r", " ").replace("\n", " ").replace("\"", "\\\"")
}
