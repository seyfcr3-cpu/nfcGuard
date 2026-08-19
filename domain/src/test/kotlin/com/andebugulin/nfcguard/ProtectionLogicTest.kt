package com.andebugulin.nfcguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Brick-style protection state machine.
 */
class ProtectionLogicTest {

    private fun state(
        modes: List<Mode> = emptyList(),
        protectionState: ProtectionState = ProtectionState.UNLOCKED,
        registeredNfcTagId: String = "",
        configLocked: Boolean = false,
        configEditable: Boolean = false,
        strictNfcMode: Boolean = true,
        uninstallProtection: Boolean = false,
        activeModes: Set<String> = emptySet()
    ) = AppState(
        modes = modes,
        protectionState = protectionState,
        registeredNfcTagId = registeredNfcTagId,
        configLocked = configLocked,
        configEditable = configEditable,
        strictNfcMode = strictNfcMode,
        uninstallProtection = uninstallProtection,
        activeModes = activeModes
    )

    private fun mode(
        id: String,
        blockedApps: List<String> = listOf("com.example.app"),
        blockMode: BlockMode = BlockMode.BLOCK_SELECTED
    ) = Mode(id = id, name = id, blockedApps = blockedApps, blockMode = blockMode)

    // ─── toggleProtection ──────────────────────────────────────────────

    @Test
    fun toggleProtection_noRegisteredTag_returnsNoRegisteredTag() {
        val s = state()
        val result = ProtectionLogic.toggleProtection(s, "any-tag")
        assertTrue(result is ProtectionLogic.ToggleProtectionResult.NoRegisteredTag)
    }

    @Test
    fun toggleProtection_wrongTag_returnsWrongTag() {
        val s = state(registeredNfcTagId = "correct-tag")
        val result = ProtectionLogic.toggleProtection(s, "wrong-tag")
        assertTrue(result is ProtectionLogic.ToggleProtectionResult.WrongTag)
    }

    @Test
    fun toggleProtection_correctTag_unlockedToLocked() {
        val m1 = mode("m1")
        val m2 = mode("m2")
        val s = state(
            modes = listOf(m1, m2),
            registeredNfcTagId = "key-tag",
            protectionState = ProtectionState.UNLOCKED
        )

        val result = ProtectionLogic.toggleProtection(s, "key-tag")
        assertTrue(result is ProtectionLogic.ToggleProtectionResult.Toggled)

        val toggled = result as ProtectionLogic.ToggleProtectionResult.Toggled
        assertTrue(toggled.nowLocked)
        assertEquals(ProtectionState.LOCKED, toggled.newState.protectionState)
        assertEquals(setOf("m1", "m2"), toggled.newState.activeModes)
        assertTrue(toggled.newState.configLocked)
        assertFalse(toggled.newState.configEditable)
    }

    @Test
    fun toggleProtection_correctTag_lockedToUnlocked() {
        val m1 = mode("m1")
        val s = state(
            modes = listOf(m1),
            registeredNfcTagId = "key-tag",
            protectionState = ProtectionState.LOCKED,
            configLocked = true,
            activeModes = setOf("m1")
        )

        val result = ProtectionLogic.toggleProtection(s, "key-tag")
        assertTrue(result is ProtectionLogic.ToggleProtectionResult.Toggled)

        val toggled = result as ProtectionLogic.ToggleProtectionResult.Toggled
        assertFalse(toggled.nowLocked)
        assertEquals(ProtectionState.UNLOCKED, toggled.newState.protectionState)
        assertTrue(toggled.newState.activeModes.isEmpty())
        assertFalse(toggled.newState.configLocked)
        assertTrue(toggled.newState.configEditable)
    }

    @Test
    fun toggleProtection_locked_clearsTimedState() {
        val m1 = mode("m1")
        val s = state(
            modes = listOf(m1),
            registeredNfcTagId = "key",
            protectionState = ProtectionState.LOCKED,
            activeModes = setOf("m1"),
            timedModeDeactivations = mapOf("m1" to 999L),
            timedModeReactivations = mapOf("m1" to 888L),
            pausedModeRemainingMs = mapOf("m1" to 777L)
        )

        val result = ProtectionLogic.toggleProtection(s, "key")
        val toggled = result as ProtectionLogic.ToggleProtectionResult.Toggled
        assertTrue(toggled.newState.timedModeDeactivations.isEmpty())
        assertTrue(toggled.newState.timedModeReactivations.isEmpty())
        assertTrue(toggled.newState.pausedModeRemainingMs.isEmpty())
    }

    // ─── rejectConfigMutation ──────────────────────────────────────────

    @Test
    fun rejectConfigMutation_unlocked_returnsNull() {
        val s = state(protectionState = ProtectionState.UNLOCKED)
        assertNull(ProtectionLogic.rejectConfigMutation(s))
    }

    @Test
    fun rejectConfigMutation_locked_notEditable_returnsError() {
        val s = state(protectionState = ProtectionState.LOCKED, configEditable = false)
        assertNotNull(ProtectionLogic.rejectConfigMutation(s))
    }

    @Test
    fun rejectConfigMutation_locked_editable_returnsNull() {
        val s = state(protectionState = ProtectionState.LOCKED, configEditable = true)
        assertNull(ProtectionLogic.rejectConfigMutation(s))
    }

    // ─── rejectModeDeactivation ────────────────────────────────────────

    @Test
    fun rejectModeDeactivation_unlocked_returnsNull() {
        val s = state(protectionState = ProtectionState.UNLOCKED)
        assertNull(ProtectionLogic.rejectModeDeactivation(s))
    }

    @Test
    fun rejectModeDeactivation_locked_returnsError() {
        val s = state(protectionState = ProtectionState.LOCKED)
        assertNotNull(ProtectionLogic.rejectModeDeactivation(s))
    }

    // ─── rejectSettingsChange ──────────────────────────────────────────

    @Test
    fun rejectSettingsChange_unlocked_returnsNull() {
        val s = state(protectionState = ProtectionState.UNLOCKED)
        assertNull(ProtectionLogic.rejectSettingsChange(s))
    }

    @Test
    fun rejectSettingsChange_locked_notEditable_returnsError() {
        val s = state(protectionState = ProtectionState.LOCKED, configEditable = false)
        assertNotNull(ProtectionLogic.rejectSettingsChange(s))
    }

    @Test
    fun rejectSettingsChange_locked_editable_returnsNull() {
        val s = state(protectionState = ProtectionState.LOCKED, configEditable = true)
        assertNull(ProtectionLogic.rejectSettingsChange(s))
    }

    // ─── validateEmergencyReset ────────────────────────────────────────

    @Test
    fun validateEmergencyReset_noPreviousReset_returnsNull() {
        val s = state(emergencyResetTime = 0)
        assertNull(ProtectionLogic.validateEmergencyReset(s, now = 1_000_000L))
    }

    @Test
    fun validateEmergencyReset_withinCooldown_returnsError() {
        val s = state(emergencyResetTime = 1_000_000L)
        val result = ProtectionLogic.validateEmergencyReset(s, now = 1_000_000L + 60_000L) // 1 min after
        assertNotNull(result)
        assertTrue(result!!.contains("cooldown"))
    }

    @Test
    fun validateEmergencyReset_afterCooldown_returnsNull() {
        val s = state(emergencyResetTime = 1_000_000L)
        val result = ProtectionLogic.validateEmergencyReset(s, now = 1_000_000L + 11 * 60_000L) // 11 min after
        assertNull(result)
    }

    // ─── effectiveBlockedAppsWhenLocked ─────────────────────────────────

    @Test
    fun effectiveBlockedApps_blockSelected() {
        val m1 = mode("m1", blockedApps = listOf("a", "b"), blockMode = BlockMode.BLOCK_SELECTED)
        val m2 = mode("m2", blockedApps = listOf("c"), blockMode = BlockMode.BLOCK_SELECTED)
        val s = state(modes = listOf(m1, m2))
        assertEquals(setOf("a", "b", "c"), ProtectionLogic.effectiveBlockedAppsWhenLocked(s))
    }

    @Test
    fun effectiveBlockedApps_allowSelected() {
        val m1 = mode("m1", blockedApps = listOf("allowed"), blockMode = BlockMode.ALLOW_SELECTED)
        val s = state(modes = listOf(m1))
        assertEquals(setOf("allowed"), ProtectionLogic.effectiveBlockedAppsWhenLocked(s))
    }

    // ─── relockConfig ──────────────────────────────────────────────────

    @Test
    fun relockConfig_setsConfigEditableFalse() {
        val s = state(configEditable = true)
        val result = ProtectionLogic.relockConfig(s)
        assertFalse(result.configEditable)
    }

    // ─── full toggle cycle ─────────────────────────────────────────────

    @Test
    fun fullCycle_unlock_lock_unlock() {
        val m1 = mode("m1")
        var s = state(
            modes = listOf(m1),
            registeredNfcTagId = "key",
            protectionState = ProtectionState.UNLOCKED
        )

        // Lock
        val lockResult = ProtectionLogic.toggleProtection(s, "key") as ProtectionLogic.ToggleProtectionResult.Toggled
        assertTrue(lockResult.nowLocked)
        assertEquals(ProtectionState.LOCKED, lockResult.newState.protectionState)
        assertEquals(setOf("m1"), lockResult.newState.activeModes)
        s = lockResult.newState

        // Unlock
        val unlockResult = ProtectionLogic.toggleProtection(s, "key") as ProtectionLogic.ToggleProtectionResult.Toggled
        assertFalse(unlockResult.nowLocked)
        assertEquals(ProtectionState.UNLOCKED, unlockResult.newState.protectionState)
        assertTrue(unlockResult.newState.activeModes.isEmpty())
        s = unlockResult.newState

        // Lock again
        val lockAgain = ProtectionLogic.toggleProtection(s, "key") as ProtectionLogic.ToggleProtectionResult.Toggled
        assertTrue(lockAgain.nowLocked)
        assertEquals(ProtectionState.LOCKED, lockAgain.newState.protectionState)
    }

    @Test
    fun fullCycle_wrongTagRejectedAtEveryStage() {
        val m1 = mode("m1")
        val s = state(
            modes = listOf(m1),
            registeredNfcTagId = "key",
            protectionState = ProtectionState.UNLOCKED
        )

        // Wrong tag while unlocked
        val wrong1 = ProtectionLogic.toggleProtection(s, "wrong")
        assertTrue(wrong1 is ProtectionLogic.ToggleProtectionResult.WrongTag)

        // Lock first
        val locked = (ProtectionLogic.toggleProtection(s, "key") as ProtectionLogic.ToggleProtectionResult.Toggled).newState

        // Wrong tag while locked
        val wrong2 = ProtectionLogic.toggleProtection(locked, "wrong")
        assertTrue(wrong2 is ProtectionLogic.ToggleProtectionResult.WrongTag)
    }
}
