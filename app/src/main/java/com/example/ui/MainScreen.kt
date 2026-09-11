package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.R
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                androidx.compose.material3.Surface(
                    color = SurfaceDark,
                    shadowElevation = 0.dp,
                    modifier = Modifier.background(SurfaceDark).navigationBarsPadding()
                ) {
                    androidx.compose.foundation.layout.Column {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            contentColor = TextPrimary,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0.dp),
                            modifier = Modifier.testTag("main_bottom_nav").height(64.dp)
                        ) {
                    NavigationBarItem(
                        selected = activeTab == AppTab.BROWSER_GATEWAY,
                        onClick = { viewModel.setTab(AppTab.BROWSER_GATEWAY) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Gateway",
                                tint = if (activeTab == AppTab.BROWSER_GATEWAY) ElectricCyan else TextMuted
                            )
                        },
                        label = { Text("Gateway") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("tab_browser")
                    )

                    NavigationBarItem(
                        selected = activeTab == AppTab.MESH_RADAR,
                        onClick = { viewModel.setTab(AppTab.MESH_RADAR) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Network",
                                tint = if (activeTab == AppTab.MESH_RADAR) ElectricCyan else TextMuted
                            )
                        },
                        label = { Text("Network") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("tab_radar")
                    )

                     NavigationBarItem(
                        selected = activeTab == AppTab.TRAFFIC_AUDIT,
                        onClick = { viewModel.setTab(AppTab.TRAFFIC_AUDIT) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (activeTab == AppTab.TRAFFIC_AUDIT) ElectricCyan else TextMuted
                            )
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("tab_audit")
                    )
                }
                }
                }
            }
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
                }
            }
        }

        // PERSISTENT FIRST-LAUNCH ONLY SPLASH OVERLAY
        if (onboardingStep < 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1.2f))

                    if (onboardingStep == 0) {
                        // Kaspa Logo Card
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.kaspa_reverse_k_icon_1789061783750),
                                contentDescription = "Kaspa Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Typewriter style Italicised writing
                        Text(
                            text = "Kaspa blockdag",
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Text(
                            text = "Fast, secure peer-to-peer web gateway",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            color = TextMuted
                        )
                    } else if (onboardingStep == 1) {
                        Text(
                            text = "Our Vision",
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = "A decentralized, peer-to-peer web browsing experience powered by the speed and security of blockDAG technology. No centralized servers, no tracking, just pure peer-to-peer communication.",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(0.8f))

                    // Next/Enter Browser Button
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
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (onboardingStep == 0) "Next" else "Enter Browser",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
