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
        mutableStateOf(if (sharedPrefs.getBoolean("has_seen_onboarding_v2", false)) 2 else 0)
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

        // PERSISTENT FIRST-LAUNCH ONLY SPLASH OVERLAY
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
                        .padding(horizontal = 24.dp)
                ) {
                    // Top Bar with Skip Action (positioned down from top edge/status bar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 8.dp),
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.6f))

                    // Step 0: Welcome / Gateway Introduction
                    if (onboardingStep == 0) {
                        SplashLaunchLogo(sizeDp = 72)

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Kaspa BlockDAG",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            letterSpacing = 0.3.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Decentralized L1 Web Gateway",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan,
                            letterSpacing = 0.2.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "High-speed peer-to-peer web browsing anchored by Proof-of-Work consensus. Sub-second finality, sovereign domains, and zero surveillance.",
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Micro feature chips - uniform rectangular cards
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            SplashFeaturePill(icon = Icons.Default.Bolt, label = "10 BPS DAG", modifier = Modifier.weight(1f))
                            SplashFeaturePill(icon = Icons.Default.Security, label = "Zero Trackers", modifier = Modifier.weight(1f))
                            SplashFeaturePill(icon = Icons.Default.Hub, label = "P2P Mesh", modifier = Modifier.weight(1f))
                        }
                    } else {
                        // Step 1: Decentralized Vision & Features
                        SplashLaunchLogo(sizeDp = 72)

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Decentralized Vision",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            letterSpacing = 0.3.sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Sovereign, Autonomous & Peer-to-Peer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElectricCyan,
                            letterSpacing = 0.2.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SplashVisionRow(
                                icon = Icons.Default.Bolt,
                                title = "BlockDAG Instant Finality",
                                description = "Direct .k decentralized domain lookup and native Kaspa L1 micro-settlements."
                            )
                            SplashVisionRow(
                                icon = Icons.Default.Security,
                                title = "Cryptographic Privacy",
                                description = "Zero central telemetry, zero tracking cookies, and strictly local client-side signing."
                            )
                            SplashVisionRow(
                                icon = Icons.Default.Hub,
                                title = "P2P Mesh Resiliency",
                                description = "Discover and access cached decentralized web content over peer-to-peer mesh."
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.5f))

                    // Polished Step Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(2) { index ->
                            val isSelected = onboardingStep == index
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .width(if (isSelected) 18.dp else 5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) ElectricCyan else SurfaceCardBorder)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compact, Reduced Professional Enter / Next Action Button
                    Button(
                        onClick = {
                            if (onboardingStep == 0) {
                                onboardingStep = 1
                            } else {
                                sharedPrefs.edit().putBoolean("has_seen_onboarding_v2", true).apply()
                                onboardingStep = 2
                                requestDefaultBrowser()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .widthIn(min = 132.dp, max = 164.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (onboardingStep == 0) "Next" else "Enter Browser",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Optional back navigation on Step 1 (moved up with dedicated bottom spacing)
                    if (onboardingStep > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { onboardingStep = 0 },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Back",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    } else {
                        Spacer(modifier = Modifier.height(54.dp))
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

@Composable
private fun SplashFeaturePill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun SplashVisionRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

