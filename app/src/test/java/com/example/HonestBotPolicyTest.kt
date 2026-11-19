package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BotConfigGenerator
import com.example.data.PolicyEnforcer
import com.example.model.AccountRole
import com.example.model.ConnectedAccount
import com.example.model.ConnectionStatus
import com.example.model.GlobalFleetState
import com.example.model.OwnerPolicy
import com.example.model.PolicyCheckResult
import com.example.model.TargetType
import com.example.security.SessionCryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HonestBotPolicyTest {

    private val enforcer = PolicyEnforcer()

    private fun liveVipAccount() = ConnectedAccount(
        id = "acc_1",
        ownerUid = "owner_1",
        e164Number = "+263780549558",
        displayName = "Honest Tech Support",
        connectionStatus = ConnectionStatus.LIVE.name,
        role = AccountRole.VIP.name,
        botEnabled = true,
        mediaDownloadEnabled = true,
        webSearchEnabled = true,
        dailyQuota = 100L,
        messagesUsedToday = 5L
    )

    @Test
    fun `global kill switch blocks live accounts`() {
        val policy = OwnerPolicy(globalFleetState = GlobalFleetState.PAUSED_ALL.name)
        val res = enforcer.evaluateExecution(policy, liveVipAccount(), ".play", true, true)
        assertTrue(res is PolicyCheckResult.Denied)
    }

    @Test
    fun `vip lockdown allows vip and blocks standard account`() {
        val policy = OwnerPolicy(globalFleetState = GlobalFleetState.VIP_LOCKDOWN.name)
        val vipRes = enforcer.evaluateExecution(policy, liveVipAccount(), "/news", false, true)
        assertEquals(PolicyCheckResult.Allowed, vipRes)

        val stdAccount = liveVipAccount().copy(role = AccountRole.STANDARD.name)
        val stdRes = enforcer.evaluateExecution(policy, stdAccount, "/news", false, true)
        assertTrue(stdRes is PolicyCheckResult.Denied)
    }

    @Test
    fun `blocked command and disabled media downloads are enforced`() {
        val policy = OwnerPolicy(globalFleetState = GlobalFleetState.ONLINE.name)
        val restricted = liveVipAccount().copy(
            mediaDownloadEnabled = false,
            blockedCommands = listOf("/share")
        )
        assertTrue(enforcer.evaluateExecution(policy, restricted, ".play", true, true) is PolicyCheckResult.Denied)
        assertTrue(enforcer.evaluateExecution(policy, restricted, "/share", false, true) is PolicyCheckResult.Denied)
        assertEquals(PolicyCheckResult.Allowed, enforcer.evaluateExecution(policy, restricted, "/ask", false, true))
    }

    @Test
    fun `bannedNumbers, botMode, sudoNumbers and ownerOnly rules are enforced`() {
        val stdAccount = liveVipAccount().copy(
            role = AccountRole.STANDARD.name,
            e164Number = "+263771112222"
        )
        val bannedPolicy = OwnerPolicy(bannedNumbers = listOf("+263771112222"))
        assertTrue(enforcer.evaluateExecution(bannedPolicy, stdAccount, ".menu", false, false) is PolicyCheckResult.Denied)

        val privatePolicy = OwnerPolicy(botMode = "private", sudoNumbers = listOf("+263771112222"))
        assertEquals(PolicyCheckResult.Allowed, enforcer.evaluateExecution(privatePolicy, stdAccount, ".antilink", false, false, ownerOnly = true))

        val strictPrivate = OwnerPolicy(botMode = "private")
        assertTrue(enforcer.evaluateExecution(strictPrivate, stdAccount, ".menu", false, false) is PolicyCheckResult.Denied)

        val inboxOnly = OwnerPolicy(botMode = "inbox")
        val groupAcc = stdAccount.copy(targetType = TargetType.GROUP_TARGET.name, e164Number = "120363001@g.us")
        assertTrue(enforcer.evaluateExecution(inboxOnly, groupAcc, ".play", true, true) is PolicyCheckResult.Denied)
    }

    @Test
    fun `bridge config env generator exports Honest Bot Pro automation settings`() {
        val policy = OwnerPolicy(
            botName = "Honest Bot Pro",
            commandPrefix = ".",
            botMode = "public",
            antiLinkMode = "delete",
            antiCallEnabled = true,
            welcomeEnabled = true,
            sudoNumbers = listOf("+263780549558")
        )
        val env = BotConfigGenerator.generateBridgeConfigEnv(policy, listOf(liveVipAccount()))
        assertTrue(env.contains("BOT_NAME=\"Honest Bot Pro\""))
        assertTrue(env.contains("ANTI_LINK=\"delete\""))
        assertTrue(env.contains("ANTI_CALL=\"true\""))
        assertTrue(env.contains("WELCOME=\"true\""))
        assertTrue(env.contains("SUDO=\"+263780549558\""))
        assertFalse(env.contains("KHAN-MD"))
    }

    @Test
    fun `pairing code and hmac signature generation work deterministically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = SessionCryptoManager(context)
        val code = crypto.generatePairingCode()
        assertTrue(code.startsWith("HBP"))
        assertTrue(code.contains("-"))

        val sig = crypto.signWebhookPayload("secret", """{"ok":true}""")
        assertTrue(sig.startsWith("sha256="))
        assertEquals(71, sig.length)
    }
}
