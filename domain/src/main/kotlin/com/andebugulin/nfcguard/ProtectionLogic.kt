package com.andebugulin.nfcguard

/**
 * Pure domain logic for the Brick-style protection state machine.
 *
 * Enforces that only the registered NFC tag can toggle protection
 * and unlock configuration. All mutations go through these functions
 * before reaching the repository.
 */
object ProtectionLogic {

    sealed class ToggleProtectionResult {
        data class Toggled(val newState: AppState, val nowLocked: Boolean) : ToggleProtectionResult()
        object NoRegisteredTag : ToggleProtectionResult()
        object WrongTag : ToggleProtectionResult()
    }

    /**
     * Toggle between LOCKED and UNLOCKED using the scanned NFC tag.
     *
     * - If no tag is registered, returns [NoRegisteredTag].
     * - If the scanned tag doesn't match the registered one, returns [WrongTag].
     * - If the tag matches, toggles [ProtectionState] and activates/deactivates
     *   all modes accordingly.
     *
     * When toggling to LOCKED: all modes are activated (blocking starts),
     * config is locked.
     * When toggling to UNLOCKED: all modes are deactivated (blocking stops),
     * config is unlocked (unless strictNfcMode with configLocked separately managed).
     */
    fun toggleProtection(
        state: AppState,
        scannedTagId: String
    ): ToggleProtectionResult {
        if (state.registeredNfcTagId.isEmpty()) return ToggleProtectionResult.NoRegisteredTag
        if (scannedTagId != state.registeredNfcTagId) return ToggleProtectionResult.WrongTag

        return when (state.protectionState) {
            ProtectionState.UNLOCKED -> {
                // Lock: activate all modes, freeze config
                val allModeIds = state.modes.map { it.id }.toSet()
                val newState = state.copy(
                    protectionState = ProtectionState.LOCKED,
                    activeModes = allModeIds,
                    manuallyActivatedModes = state.manuallyActivatedModes + allModeIds,
                    configLocked = true,
                    configEditable = false,
                    timedModeDeactivations = emptyMap(),
                    timedModeReactivations = emptyMap(),
                    pausedModeRemainingMs = emptyMap()
                )
                ToggleProtectionResult.Toggled(newState, nowLocked = true)
            }
            ProtectionState.LOCKED -> {
                // Unlock: deactivate all modes, unfreeze config
                val newState = state.copy(
                    protectionState = ProtectionState.UNLOCKED,
                    activeModes = emptySet(),
                    manuallyActivatedModes = emptySet(),
                    timedModeDeactivations = emptyMap(),
                    timedModeReactivations = emptyMap(),
                    pausedModeRemainingMs = emptyMap(),
                    activeSchedules = emptySet(),
                    configLocked = false,
                    configEditable = true
                )
                ToggleProtectionResult.Toggled(newState, nowLocked = false)
            }
        }
    }

    /**
     * Re-lock configuration after the user finishes editing while unlocked.
     * Called when the user saves config or explicitly re-locks.
     */
    fun relockConfig(state: AppState): AppState {
        return state.copy(configEditable = false)
    }

    /**
     * Check whether a configuration mutation is permitted.
     *
     * Returns null if allowed, or an error message if blocked.
     */
    fun rejectConfigMutation(state: AppState): String? {
        if (state.protectionState == ProtectionState.LOCKED && !state.configEditable) {
            return "Configuration locked. Tap your registered NFC key to modify protection settings."
        }
        return null
    }

    /**
     * Check whether the user can deactivate a mode directly (outside the toggle flow).
     * When LOCKED, direct deactivation is rejected.
     */
    fun rejectModeDeactivation(state: AppState): String? {
        if (state.protectionState == ProtectionState.LOCKED) {
            return "Apps are locked. Tap your NFC key to unlock."
        }
        return null
    }

    /**
     * Reject settings changes (strict mode toggle, uninstall protection, etc.)
     * while LOCKED.
     */
    fun rejectSettingsChange(state: AppState): String? {
        if (state.protectionState == ProtectionState.LOCKED && !state.configEditable) {
            return "Configuration locked. Tap your registered NFC key to modify protection settings."
        }
        return null
    }

    /**
     * Validate an emergency reset request.
     * Returns null if allowed, or an error message with remaining cooldown.
     */
    fun validateEmergencyReset(state: AppState, now: Long): String? {
        val cooldownRemaining = (EMERGENCY_COOLDOWN_MILLIS - (now - state.emergencyResetTime))
        if (cooldownRemaining > 0) {
            val minutes = (cooldownRemaining / 60_000).toInt()
            val seconds = ((cooldownRemaining % 60_000) / 1000).toInt()
            return "Emergency reset cooldown: ${minutes}m ${seconds}s remaining."
        }
        return null
    }

    /** All modes' blocked apps as a flat set, for use by BlockerService. */
    fun allBlockedApps(state: AppState): Set<String> {
        return state.modes.flatMap { it.blockedApps }.toSet()
    }

    /** Compute effective block mode when LOCKED. BLOCK_SELECTED takes precedence. */
    fun effectiveBlockMode(state: AppState): BlockMode {
        return if (state.modes.any { it.blockMode == BlockMode.ALLOW_SELECTED }) {
            BlockMode.ALLOW_SELECTED
        } else {
            BlockMode.BLOCK_SELECTED
        }
    }

    /** Compute effective blocked apps when LOCKED, respecting ALLOW_SELECTED modes. */
    fun effectiveBlockedAppsWhenLocked(state: AppState): Set<String> {
        val activeModes = state.modes
        val hasAllow = activeModes.any { it.blockMode == BlockMode.ALLOW_SELECTED }
        return if (hasAllow) {
            activeModes
                .filter { it.blockMode == BlockMode.ALLOW_SELECTED }
                .flatMap { it.blockedApps }
                .toSet()
        } else {
            activeModes.flatMap { it.blockedApps }.toSet()
        }
    }

    private const val EMERGENCY_COOLDOWN_MILLIS = 10 * 60 * 1000L // 10 minutes
}
