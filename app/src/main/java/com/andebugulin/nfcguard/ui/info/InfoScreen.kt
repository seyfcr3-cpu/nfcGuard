package com.andebugulin.nfcguard.ui.info

import com.andebugulin.nfcguard.ui.GuardianTheme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    onBack: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500), label = "alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "offsetY"
    )

    Scaffold(
        containerColor = GuardianTheme.BackgroundPrimary
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(GuardianTheme.BackgroundPrimary),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .alpha(alpha)
                    .offset(y = offsetY.dp)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = null,
                    tint = GuardianTheme.BrandOrange,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    "BLOCKTAP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = GuardianTheme.TextPrimary,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Made by Useful Objects",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GuardianTheme.TextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    "Made in Algeria",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = GuardianTheme.BrandOrange,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
