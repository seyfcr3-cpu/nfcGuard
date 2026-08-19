package com.andebugulin.nfcguard.ui

import com.andebugulin.nfcguard.R
import com.andebugulin.nfcguard.data.AppLogger
import com.andebugulin.nfcguard.data.AppStateRepository
import com.andebugulin.nfcguard.ProtectionState
import com.andebugulin.nfcguard.Schedule
import com.andebugulin.nfcguard.service.ForegroundDetectorService
import com.andebugulin.nfcguard.ui.home.HomeScreen
import com.andebugulin.nfcguard.ui.info.InfoScreen
import com.andebugulin.nfcguard.ui.modes.ModesScreen
import com.andebugulin.nfcguard.ui.modes.UnlockDurationDialog
import com.andebugulin.nfcguard.ui.modes.UnlockModeInfo
import com.andebugulin.nfcguard.ui.nfc.NfcTagsScreen
import com.andebugulin.nfcguard.ui.schedules.SchedulesScreen

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.FilterQuality

enum class Screen {
    HOME, MODES, SCHEDULES, NFC_TAGS, INFO
}

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var scannedNfcTagId = mutableStateOf<String?>(null)
    private var wrongTagScanned = mutableStateOf(false)
    var nfcRegistrationMode = mutableStateOf(false)

    private var launchedFromNfc = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize logger first
        AppLogger.init(this)

        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        setContent {
            MinimalistTheme {
                val viewModel: GuardianViewModel = viewModel()
                MainNavigation(
                    viewModel = viewModel,
                    scannedNfcTagId = scannedNfcTagId,
                    wrongTagScanned = wrongTagScanned,
                    nfcRegistrationMode = nfcRegistrationMode,
                    launchedFromNfc = launchedFromNfc
                )
            }
        }

        handleNfcIntent(intent, fromCreate = true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent, fromCreate = false)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun handleNfcIntent(intent: Intent?, fromCreate: Boolean) {
        if (intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val tagId = it.id.joinToString("") { byte -> "%02x".format(byte) }
                android.util.Log.d("NFC_SCAN", "Scanned tag: $tagId")
                AppLogger.log("NFC", "Tag scanned: $tagId")

                launchedFromNfc = fromCreate

                try {
                    val appState = AppStateRepository.getInstance(this).current

                    if (fromCreate) {
                        val isRegistered = appState.registeredNfcTagId.isNotEmpty() && tagId == appState.registeredNfcTagId
                        val isLegacyValid = run {
                            val activeModes = appState.modes.filter { appState.activeModes.contains(it.id) }
                            val hasNfcLockedMode = activeModes.any { it.nfcTagIds.isNotEmpty() }
                            if (hasNfcLockedMode && !nfcRegistrationMode.value) {
                                activeModes.any { it.nfcTagIds.contains(tagId) || it.nfcTagIds.isEmpty() || it.nfcTagIds.contains("ANY") }
                            } else false
                        }

                        if ((appState.protectionState == ProtectionState.LOCKED && isRegistered) ||
                            (appState.protectionState == ProtectionState.UNLOCKED && isRegistered && appState.modes.isNotEmpty()) ||
                            isLegacyValid
                        ) {
                            val result = ProtectionLogic.toggleProtection(appState, tagId)
                            if (result is ProtectionLogic.ToggleProtectionResult.Toggled) {
                                lifecycleScope.launch { AppStateRepository.getInstance(this@MainActivity).update { result.newState } }
                                val msg = if (result.nowLocked) "BLOCKTAP ON" else "BLOCKTAP OFF"
                                android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
                                finish()
                                return@let
                            }
                        }

                        val isWrongTag = appState.protectionState == ProtectionState.LOCKED &&
                                appState.registeredNfcTagId.isNotEmpty() && tagId != appState.registeredNfcTagId
                        val isLegacyWrong = run {
                            val activeModes = appState.modes.filter { appState.activeModes.contains(it.id) }
                            val hasNfcLockedMode = activeModes.any { it.nfcTagIds.isNotEmpty() }
                            if (hasNfcLockedMode && !nfcRegistrationMode.value) {
                                val validTag = activeModes.any { it.nfcTagIds.contains(tagId) || it.nfcTagIds.isEmpty() || it.nfcTagIds.contains("ANY") }
                                !validTag && appState.activeModes.isNotEmpty()
                            } else false
                        }

                        if (isWrongTag || isLegacyWrong) {
                            android.widget.Toast.makeText(applicationContext, "WRONG BLOCKTAP", android.widget.Toast.LENGTH_LONG).show()
                            finish()
                            return@let
                        }

                        finish()
                        return@let
                    }

                    scannedNfcTagId.value = tagId
                } catch (e: Exception) {
                    android.util.Log.e("NFC_SCAN", "Error validating tag: ${e.message}")
                    if (fromCreate) finish()
                }
            }
        }
    }

}

@Composable
fun MinimalistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = GuardianTheme.BackgroundPrimary,
            surface = GuardianTheme.BackgroundSurface,
            primary = GuardianTheme.ButtonPrimary,
            secondary = GuardianTheme.TextSecondary,
            onBackground = GuardianTheme.TextPrimary,
            onSurface = GuardianTheme.TextPrimary,
        ),
        content = content
    )
}

@Composable
fun MainNavigation(
    viewModel: GuardianViewModel,
    scannedNfcTagId: MutableState<String?>,
    wrongTagScanned: MutableState<Boolean>,
    nfcRegistrationMode: MutableState<Boolean>,
    launchedFromNfc: Boolean = false
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE) }
    var hasSeenOnboarding by remember {
        mutableStateOf(prefs.getBoolean("has_seen_onboarding", false))
    }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val appState by viewModel.appState.collectAsState()
    val pendingUnlock by viewModel.pendingUnlock.collectAsState()

    // Handle NFC tag scans when modes are active (for unlocking)
    LaunchedEffect(scannedNfcTagId.value, appState.activeModes) {
        val tagId = scannedNfcTagId.value
        if (tagId != null && !nfcRegistrationMode.value) {
            // If LOCKED, this is a protection toggle
            if (appState.protectionState == ProtectionState.LOCKED) {
                viewModel.handleProtectionNfcTag(tagId)
            } else if (appState.activeModes.isNotEmpty()) {
                viewModel.handleNfcTag(tagId)
            }
            scannedNfcTagId.value = null
        }
    }

    // Track protection feedback for in-app scan animation
    val protectionFeedback by viewModel.protectionFeedback.collectAsState()
    var showScanAnimation by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(protectionFeedback) {
        val fb = protectionFeedback
        if (fb != null && (fb.contains("locked", ignoreCase = true) ||
                fb.contains("unlocked", ignoreCase = true) ||
                fb.contains("Wrong", ignoreCase = true))) {
            showScanAnimation = fb
        }
    }

    // Show unlock duration dialog when pending
    pendingUnlock?.let { pending ->
        val unlockModes = pending.modeIds.mapNotNull { id ->
            val mode = appState.modes.find { it.id == id }
            if (mode != null) UnlockModeInfo(id, mode.name, pending.modeLimits[id])
            else null
        }
        UnlockDurationDialog(
            modes = unlockModes,
            onDismiss = { viewModel.dismissUnlock() },
            onConfirm = { reactivateAtMillis, selectedModeIds ->
                viewModel.confirmUnlock(reactivateAtMillis, selectedModeIds)
            }
        )
    }

    // Show the permission-setup flow after onboarding completes (or on
    // subsequent launches if it was never finished). The `remember` key is
    // `hasSeenOnboarding` so completing OnboardingScreen re-evaluates and
    // triggers the dialog flow without manual postDelayed plumbing.
    var showPermissionOnboarding by remember(hasSeenOnboarding) {
        mutableStateOf(com.andebugulin.nfcguard.ui.onboarding.shouldShowOnboarding(context))
    }
    if (showPermissionOnboarding) {
        com.andebugulin.nfcguard.ui.onboarding.PermissionOnboarding(
            onDone = { showPermissionOnboarding = false }
        )
    }

    // Back from a sub-screen returns to Home instead of exiting to the
    // launcher. On Home, Back is left unhandled so the system exits normally.
    BackHandler(enabled = hasSeenOnboarding && currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasSeenOnboarding) {
            OnboardingScreen(
                onComplete = {
                    prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                    hasSeenOnboarding = true
                }
            )
        } else {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigate = { screen -> currentScreen = screen }
                )
                Screen.MODES -> ModesScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.HOME }
                )
                Screen.SCHEDULES -> SchedulesScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.HOME }
                )
                Screen.NFC_TAGS -> NfcTagsScreen(
                    viewModel = viewModel,
                    scannedNfcTagId = scannedNfcTagId,
                    nfcRegistrationMode = nfcRegistrationMode,
                    onBack = { currentScreen = Screen.HOME }
                )
                Screen.INFO -> InfoScreen(
                    onBack = { currentScreen = Screen.HOME }
                )
            }

            // Show wrong tag feedback
            if (wrongTagScanned.value) {
                WrongTagFeedback()
            }

            // Show BlockTap scan animation when app is open and tag is scanned
            showScanAnimation?.let { msg ->
                BlockTapScanFeedback(
                    message = msg,
                    onDismiss = {
                        showScanAnimation = null
                        viewModel.clearFeedback()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            title = "BLOCKTAP",
            subtitle = "DIGITAL WELLBEING",
            description = "Break free from mindless scrolling. BlockTap blocks distracting apps until you physically unlock them with your BlockTap.",
            icon = "shield"
        ),
        OnboardingPage(
            title = "MODES",
            subtitle = "FLEXIBLE CONTROL",
            description = "Create blocking modes for any situation:\n\n" +
                    "•  BLOCK — block the specific apps that distract you\n" +
                    "•  ALLOW ONLY — block everything except the apps you choose",
            icon = "modes"
        ),
        OnboardingPage(
            title = "BLOCKTAP LOCKS",
            subtitle = "PHYSICAL FRICTION",
            description =                     "Add a BlockTap as a physical key to unlock your modes.\n\n" +
                    "Keep a tag somewhere inconvenient — a drawer, the kitchen, another room — so opening a blocked app takes real, deliberate effort.\n\n" +
                    "This is an optional extra layer; modes work fine without it.",
            icon = "nfc"
        ),
        OnboardingPage(
            title = "SCHEDULES",
            subtitle = "AUTOMATION",
            description = "Let modes turn on by themselves, on the days and times you set:\n\n" +
                    "•  Work hours on weekdays\n" +
                    "•  Sleep schedule overnight\n" +
                    "•  Deep-work blocks on weekends",
            icon = "schedule"
        ),
        OnboardingPage(
            title = "READY",
            subtitle = "LET'S GET STARTED",
            description = "BlockTap needs a few permissions to do its job. We'll walk through each one and explain why:\n\n" +
                    "•  Notifications (optional) — show which modes are active\n" +
                    "•  Usage access — see which app is open\n" +
                    "•  Display over apps — show the block screen\n" +
                    "•  Battery optimization — keep running reliably\n" +
                    "•  Pause app activity — must be turned off for BlockTap\n" +
                    "•  Accessibility — more reliable, instant blocking\n\n" +
                    "Let's set them up.",
            icon = "ready"
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianTheme.BackgroundPrimary)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OnboardingPageContent(pages[currentPage])
            }

            // Progress indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { index ->
                    if (index == currentPage) {
                        // Active page - filled white circle
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .padding(2.dp)
                                .background(
                                    GuardianTheme.TextPrimary,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    } else {
                        // Inactive page - hollow circle with white border
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .padding(2.dp)
                                .border(
                                    width = 1.dp,
                                    color = GuardianTheme.TextPrimary,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    TextButton(
                        onClick = { currentPage-- },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = GuardianTheme.TextSecondary
                        )
                    ) {
                        Text("BACK", letterSpacing = 1.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }

                Button(
                    onClick = {
                        if (currentPage < pages.size - 1) {
                            currentPage++
                        } else {
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GuardianTheme.ButtonPrimary,
                        contentColor = GuardianTheme.ButtonPrimaryText
                    ),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.height(48.dp).widthIn(min = 120.dp)
                ) {
                    Text(
                        if (currentPage < pages.size - 1) "NEXT" else "GET STARTED",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Icon
        when (page.icon) {
            "shield" -> {
                // Use actual app icon
                val context = LocalContext.current
                val appIcon = remember {
                    context.packageManager.getApplicationIcon(context.applicationInfo)
                }
                // AFTER
                Image(
                    bitmap = appIcon.toBitmap(512, 512).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    filterQuality = FilterQuality.High
                )
            }
            "modes" -> Icon(
                Icons.Default.DarkMode,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = GuardianTheme.IconPrimary
            )
            "nfc" -> Icon(
                Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = GuardianTheme.IconPrimary
            )
            "schedule" -> Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = GuardianTheme.IconPrimary
            )
            "ready" -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = GuardianTheme.IconPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            page.title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = GuardianTheme.TextPrimary,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center
        )

        // Subtitle
        Text(
            page.subtitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = GuardianTheme.TextSecondary,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description — left-aligned so multi-line bullet lists line up
        // cleanly instead of rendering ragged under centered alignment.
        Text(
            page.description,
            fontSize = 14.sp,
            color = GuardianTheme.TextPrimary,
            letterSpacing = 0.5.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: String
)

@Composable
fun WrongTagFeedback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianTheme.OverlayBackground),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = GuardianTheme.ErrorDark,
            modifier = Modifier.padding(48.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = GuardianTheme.TextPrimary,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "WRONG BLOCKTAP",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GuardianTheme.TextPrimary,
                    letterSpacing = 2.sp
                )
                Text(
                    "This BlockTap protection requires\nyour registered BlockTap",
                    fontSize = 14.sp,
                    color = Color(0xFFFFCCCC),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun BlockTapScanFeedback(
    message: String,
    onDismiss: () -> Unit
) {
    val isOn = message.contains("ON", ignoreCase = true)
    val isError = message.contains("WRONG", ignoreCase = true) || message.contains("Error", ignoreCase = true)

    val bgColor by animateColorAsState(
        targetValue = if (isError) GuardianTheme.ErrorDark else Color.Black,
        animationSpec = tween(durationMillis = 300), label = "bg"
    )

    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2200)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianTheme.OverlayBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            val logoPainter = painterResource(id = R.drawable.blocktap_logo)

            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = logoPainter,
                    contentDescription = "BlockTap",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.9f
                        },
                    contentScale = ContentScale.Fit
                )

                val infiniteTransition = rememberInfiniteTransition(label = "scan")
                val scanY by infiniteTransition.animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanY"
                )
                val scanAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .offset(y = (scanY * 80).dp)
                        .background(
                            Color(0xFF00FF41).copy(alpha = scanAlpha),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                if (isError) "WRONG BLOCKTAP" else message.uppercase(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (isError) GuardianTheme.Error else Color.White,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (isError) "This BlockTap protection requires\nyour registered BlockTap"
                else if (isOn) "Protection activated"
                else "Protection deactivated",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}
