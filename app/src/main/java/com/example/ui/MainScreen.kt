package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel

@Composable
fun MainScreen(viewModel: DecentralViewModel = viewModel()) {
    val activeTab by viewModel.activeTab.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var onboardingStep by remember {
        mutableStateOf(if (sharedPrefs.getBoolean("has_seen_onboarding_v3", true)) 2 else 2)
    }

    val defaultBrowserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    fun requestDefaultBrowser() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.content.Context.ROLE_SERVICE) as? android.app.role.RoleManager
            roleManager?.let {
                if (it.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                    !it.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    try {
                        val intent = it.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                        defaultBrowserLauncher.launch(intent)
                    } catch (e: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (activeTab) {
                    AppTab.BROWSER_GATEWAY -> BrowserGatewayScreen(viewModel = viewModel)
                    AppTab.MESH_RADAR -> MeshRadarScreen(viewModel = viewModel)
                    AppTab.TRAFFIC_AUDIT -> TrafficAuditScreen(viewModel = viewModel)
                    AppTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                }
            }
        }

        // PERSISTENT FIRST-LAUNCH ONLY SPLASH OVERLAY (Minimalist with all card boxes and writeups removed)
        if (onboardingStep < 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                ) {
                    // Top Bar with Skip Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                sharedPrefs.edit().putBoolean("has_seen_onboarding_v2", true).apply()
                                onboardingStep = 2
                                requestDefaultBrowser()
                            }
                        ) {
                            Text(
                                text = "Skip",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Step 0: Minimal Welcome Screen
                    if (onboardingStep == 0) {
                        Spacer(modifier = Modifier.weight(1f))

                        SplashLaunchLogo(sizeDp = 88)

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Kaspa Browser",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.4.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "High-Speed Web Decentralized Gateway",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan,
                            letterSpacing = 0.3.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Step Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ElectricCyan)
                            )
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceCardBorder)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Next Button
                        Button(
                            onClick = { onboardingStep = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(min = 160.dp, max = 220.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Next",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    } else {
                        // Step 1: Next Tutorial Screen with Missions Write-up (Card-free)
                        Spacer(modifier = Modifier.weight(0.3f))

                        SplashLaunchLogo(sizeDp = 64)

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Core Missions",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 0.3.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Sovereign, Autonomous & Peer-to-Peer",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan,
                            letterSpacing = 0.2.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Clean mission statements without card boxes
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 380.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            MissionRow(
                                icon = Icons.Default.Hub,
                                title = "Decentralized Mesh Routing",
                                description = "Distributed peer-to-peer content delivery and resilient lookup without centralized DNS gatekeepers."
                            )
                            MissionRow(
                                icon = Icons.Default.Speed,
                                title = "HTTPS/3 QUIC Transport",
                                description = "Zero round-trip handshake multiplexing for next-gen latency and encrypted streams."
                            )
                            MissionRow(
                                icon = Icons.Default.Security,
                                title = "Zero-Surveillance Privacy",
                                description = "Zero trackers, no telemetry, and local cryptographic key custody for complete browsing autonomy."
                            )
                        }

                        Spacer(modifier = Modifier.weight(0.5f))

                        // Step Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceCardBorder)
                            )
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ElectricCyan)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Enter Browser Button
                        Button(
                            onClick = {
                                sharedPrefs.edit().putBoolean("has_seen_onboarding_v2", true).apply()
                                onboardingStep = 2
                                requestDefaultBrowser()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(min = 160.dp, max = 220.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Enter Browser",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { onboardingStep = 0 },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Back",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Splash Tutorial Logo displaying clean reverse K Kaspa logo without cardboard container.
 */
@Composable
private fun SplashLaunchLogo(
    modifier: Modifier = Modifier,
    sizeDp: Int = 72
) {
    Image(
        painter = painterResource(id = R.drawable.ic_kaspa_reverse_k),
        contentDescription = "Kaspa Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(sizeDp.dp)
    )
}

/**
 * Clean, card-free mission row highlighting core decentralized browser objectives.
 */
@Composable
private fun MissionRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = if (description != null) Alignment.Top else Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.2.sp
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 16.5.sp
                )
            }
        }
    }
}

