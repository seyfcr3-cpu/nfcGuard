package com.andebugulin.nfcguard.ui

import com.andebugulin.nfcguard.ui.modes.ModeEditorScreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object GuardianTheme {

    // ============ BACKGROUNDS ============
    val BackgroundPrimary = Color(0xFFFFFFFF)
    val BackgroundSurface = Color(0xFFF5F5F5)
    val BackgroundSurfaceVariant = Color(0xFFEEEEEE)
    val SurfaceDim = Color(0xFFE0E0E0)

    // ============ TEXT COLORS ============
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF666666)
    val TextTertiary = Color(0xFF999999)
    val TextDisabled = Color(0xFFBBBBBB)

    // ============ BRAND ============
    val BrandOrange = Color(0xFFFF6D00)
    val BrandOrangeLight = Color(0xFFFF9E40)
    val BrandOrangeDark = Color(0xFFE65100)
    val BrandOrangeSurface = Color(0xFFFFF3E0)

    // ============ INTERACTIVE ELEMENTS ============
    val ButtonPrimary = Color(0xFFFF6D00)
    val ButtonPrimaryText = Color(0xFFFFFFFF)
    val ButtonSecondary = Color(0xFFF5F5F5)
    val ButtonSecondaryText = Color(0xFF1A1A1A)
    val ButtonDisabledContainer = Color(0xFFE0E0E0)
    val ButtonDisabledText = Color(0xFFAAAAAA)

    // ============ BORDERS & DIVIDERS ============
    val BorderFocused = Color(0xFFFF6D00)
    val BorderUnfocused = Color(0xFFCCCCCC)
    val BorderSubtle = Color(0xFFE8E8E8)
    val Divider = Color(0xFFE0E0E0)

    // ============ STATUS COLORS ============
    val StatusActive = Color(0xFFFF6D00)
    val StatusActiveText = Color(0xFFFFFFFF)
    val StatusActiveIndicator = Color(0xFFFF6D00)

    val StatusInactive = Color(0xFFF5F5F5)
    val StatusInactiveText = Color(0xFF666666)

    val StatusDeactivated = Color(0xFFF5F5F5)
    val StatusDeactivatedText = Color(0xFFAAAAAA)
    val StatusDeactivatedBorder = Color(0xFFE0E0E0)

    // ============ SEMANTIC COLORS ============
    val Error = Color(0xFFD32F2F)
    val ErrorDark = Color(0xFFFFEBEE)
    val ErrorText = Color(0xFFD32F2F)
    val ErrorTextEmphasized = Color(0xFFC62828)
    val Warning = Color(0xFFFF9800)
    val WarningBackground = Color(0xFFFFF8E1)
    val WarningAccent = Color(0xFFF57C00)
    val WarningAccentDim = Color(0xFFE65100)
    val WarningTextMuted = Color(0xFF996600)
    val Success = Color(0xFF388E3C)
    val SuccessBackground = Color(0xFFE8F5E9)
    val HighlightAccent = Color(0xFFFF6D00)
    val HighlightAccentEmphasized = Color(0xFFE65100)

    // ============ LIGHT-SURFACE PALETTE ============
    val OnLightSurfaceSecondaryText = Color(0xFF555555)
    val OnLightSurfaceBorder = Color(0xFFCCCCCC)

    // ============ DIALOG BORDERS ============
    val DialogBorderDelete = Color(0xFFFFCDD2)
    val DialogBorderEdit = Color(0xFFFFE0B2)
    val DialogBorderWarning = Color(0xFFFFE0B2)
    val DialogBorderInfo = Color(0xFFE0E0E0)
    val DialogBorderWidth = 1.dp

    // ============ ICONS ============
    val IconPrimary = Color(0xFF1A1A1A)
    val IconSecondary = Color(0xFF666666)
    val IconDisabled = Color(0xFFBBBBBB)

    // ============ INPUT FIELDS ============
    val InputBackground = Color(0xFFF5F5F5)
    val InputText = Color(0xFF1A1A1A)
    val InputCursor = Color(0xFFFF6D00)
    val InputPlaceholder = Color(0xFFAAAAAA)

    // ============ SPECIAL ELEMENTS ============
    val NfcIcon = Color(0xFFFF6D00)
    val NfcIconSubtle = Color(0xFF999999)
    val OverlayBackground = Color(0x88000000)

    // ============ BLOCKER SCREEN ============
    val BlockerBackground = Color(0xFFFF6D00)
    val BlockerText = Color(0xFFFFFFFF)
    val BlockerSubtext = Color(0xFFFFF3E0)
}
