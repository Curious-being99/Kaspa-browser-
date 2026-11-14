package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultCredentialEntity
import com.example.engine.BrowserEngineManager
import com.example.network.PasskeyConfirmationRequest
import com.example.network.PasskeyPromptKind
import com.example.network.PasskeyRecoveryEvent
import com.example.security.PasswordGenerator
import com.example.security.PasswordVaultRepository
import com.example.security.PendingCredentialSave
import com.example.ui.theme.*
import com.example.utils.BiometricAuthHelper
import com.example.viewmodel.DecentralViewModel

/**
 * Full-page Passkey / WebAuthn Diagnostics & Custom Tab Handoff screen.
 * Wired to DecentralViewModel.showWebAuthnRpIdDialog so users can verify Kazview's active
 * WebAuthn mode (FOR_BROWSER vs system-policy restriction) and hand off strict FIDO2 logins
 * to Android Custom Tabs when a website enforces privileged browser assetlinks.
 */
@Composable
fun WebAuthnDiagnosticsDialog(
    currentUrl: String?,
    viewModel: DecentralViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val engineStatus by BrowserEngineManager.engineStatus.collectAsState()
    val webAuthEnabled by viewModel.webAuthEnabled.collectAsState()
    val rpHost = remember(currentUrl) { PasswordVaultRepository.normalizeHost(currentUrl) }
    val isHttpUrl = remember(currentUrl) {
        currentUrl?.startsWith("http://", ignoreCase = true) == true ||
            currentUrl?.startsWith("https://", ignoreCase = true) == true
    }

    LaunchedEffect(Unit) {
        BrowserEngineManager.refreshEngineStatus(context)
    }

    BackHandler(enabled = true) {
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("webauthn_diagnostics_screen"),
        color = ObsidianBg
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Full-width top bar padded cleanly below status bar / display cutout
            Surface(
                color = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("passkey_diag_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Passkey Diagnostics",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kazview & Passkeys",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "FIDO2 / WebAuthn & Runtime Diagnostics",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = SurfaceCardBorder)
                }
            }

            // Scrollable diagnostics body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Kazview & FIDO2 Status",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                            HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.6f))
                            DiagnosticRow("Browser Core", "${engineStatus.engineName} (${engineStatus.engineArchitecture})")
                            DiagnosticRow("Active RP Origin", rpHost ?: "No active site")
                            DiagnosticRow("WebView Runtime", "${engineStatus.webViewPackage} (${engineStatus.webViewVersion})")
                            DiagnosticRow(
                                "Process Isolation",
                                if (engineStatus.isMultiProcess) "Multi-Process Sandbox" else "Single-Process Fallback"
                            )
                            DiagnosticRow("WebAuthn Mode", engineStatus.passkeySupportMode)
                            DiagnosticRow(
                                "WebKit FIDO2 API",
                                if (engineStatus.supportsWebAuthn) "Supported (FOR_BROWSER)" else "Unavailable on WebView"
                            )
                            DiagnosticRow(
                                "BFCache & Ad-Privacy",
                                "${if (engineStatus.backForwardCacheEnabled) "BFCache On" else "Std Nav"} · ${if (engineStatus.attributionPrivacyDisabled) "Attribution Off" else "Default"}"
                            )
                            DiagnosticRow(
                                "QUIC & Preconnect",
                                "${engineStatus.quicHintsCount} QUIC hints · ${engineStatus.preconnectCount} preconnects"
                            )
                            DiagnosticRow(
                                "Stealth Profile",
                                if (engineStatus.stealthProfileActive) {
                                    "Isolated MULTI_PROFILE Active"
                                } else if (engineStatus.multiProfileSupported) {
                                    "Ready for Stealth Tabs"
                                } else {
                                    "Standard Cookie Isolation"
                                }
                            )
                            DiagnosticRow(
                                "DocStart / MsgBridge",
                                "${if (engineStatus.supportsDocumentStartScript) "DocStart" else "Std"} · ${if (engineStatus.supportsWebMessageListener) "Origin-Checked" else "Unavailable"}"
                            )
                            DiagnosticRow(
                                "Custom Tabs Handoff",
                                engineStatus.customTabsProvider ?: "System Browser Fallback"
                            )
                        }
                    }

                    if (engineStatus.passkeyDowngradedBySystem) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmberCentral.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "System WebView restriction warning",
                                    tint = AmberCentral,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Android's WebView provider restricted WebAuthn browser mode (${engineStatus.passkeySupportMode}). Use 'Continue in System Browser Tab' below if a site rejects embedded WebView passkeys.",
                                    fontSize = 12.sp,
                                    color = AmberCentral,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Enable Passkeys (FIDO2)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = webAuthEnabled,
                                onCheckedChange = { viewModel.toggleWebAuth(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("passkey_fido2_switch")
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "How Passkeys Work in KaspaBrowser",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "1. Standard WebAuthn: Websites supporting WebView FIDO2 invoke Android Credential Manager directly inside the tab.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = "2. Privileged Browser Checks: Sites enforcing Chrome/Play Services Digital Asset Links can be completed seamlessly with one tap via System Browser Custom Tab handoff.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = "3. Encrypted Vault Tracking: Passwords use AES-256-GCM hardware-backed Keystore encryption with host-verified autofill; passkey RP origins are indexed in your Vault.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Pinned bottom actions respecting navigation bar / gesture pill insets
            Surface(
                color = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(color = SurfaceCardBorder)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isHttpUrl && !currentUrl.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    BrowserEngineManager.openInCustomTab(context, currentUrl)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("passkey_custom_tab_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Continue in System Browser Tab", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                viewModel.setShowPasswordVaultSheet(true)
                            },
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("passkey_open_vault_button")
                        ) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Password & Passkey Vault", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.weight(0.44f, fill = false)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Confirmation prompt shown before sign-in (when Auto sign-in is disabled) or before
 * creating/upgrading a passkey (when Auto-create passkey is disabled).
 */
@Composable
fun PasskeyConfirmationDialog(
    request: PasskeyConfirmationRequest,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val isSignIn = request.kind == PasskeyPromptKind.SIGN_IN
    val title = when {
        isSignIn -> "Confirm Passkey Sign-In"
        request.isUpgrade -> "Upgrade Account to Passkey?"
        else -> "Create Passkey for ${request.host}?"
    }
    val body = when {
        isSignIn ->
            "Auto sign-in is off. Confirm that you want to sign in to ${request.host} (RP: ${request.rpId}) using a passkey."
        request.isUpgrade ->
            "Allow ${request.host}${request.username.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} to upgrade your existing account to sign in faster with a passkey?"
        else ->
            "Create and save a passkey for ${request.host}${request.username.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}?"
    }
    val confirmLabel = when {
        isSignIn -> "Sign In"
        request.isUpgrade -> "Upgrade"
        else -> "Create Passkey"
    }

    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = SurfaceCard,
        icon = {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                text = body,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("passkey_confirm_approve_button")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.testTag("passkey_confirm_decline_button")
            ) {
                Text(if (isSignIn) "Cancel" else "Not now", color = TextSecondary)
            }
        }
    )
}

/**
 * Non-blocking in-tab recovery bar displayed when embedded WebView WebAuthn/Passkey closes
 * or cannot complete due to Android origin/provider restrictions, offering one-tap System
 * Browser Tab handoff or saved-password fallback.
 */
@Composable
fun PasskeyRecoveryBar(
    event: PasskeyRecoveryEvent,
    matchingPasswordCredentials: List<VaultCredentialEntity>,
    onContinueInCustomTab: () -> Unit,
    onUseSavedPassword: (VaultCredentialEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("passkey_recovery_bar"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Passkey Recovery",
                    tint = AmberCentral,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Passkey couldn't complete in WebView",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = event.reason,
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("passkey_recovery_dismiss_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onContinueInCustomTab,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .testTag("passkey_recovery_custom_tab_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use System Browser", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val fallbackCred = matchingPasswordCredentials.firstOrNull { it.hasPassword }
                if (fallbackCred != null) {
                    OutlinedButton(
                        onClick = { onUseSavedPassword(fallbackCred) },
                        border = BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("passkey_recovery_password_button")
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Use Password", fontSize = 12.sp, color = EmeraldMesh, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Compact bottom Autofill & Credential Capture bar shown inside WebView viewports when a
 * password field is detected or login credentials were submitted.
 */
@Composable
fun BrowserAutofillBar(
    pendingSave: PendingCredentialSave?,
    matchingCredentials: List<VaultCredentialEntity>,
    showGeneratorSuggestion: Boolean,
    isVaultUnlocked: Boolean,
    autoSignInEnabled: Boolean = true,
    offerToSavePasswords: Boolean = true,
    breachedCredentials: Map<String, Int> = emptyMap(),
    onUnlockAndFill: (VaultCredentialEntity) -> Unit,
    onFillGeneratedPassword: (String) -> Unit,
    onSavePending: (PendingCredentialSave) -> Unit,
    onDismissPendingSave: () -> Unit,
    onDismissAutofill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasDeviceLock = remember { BiometricAuthHelper.isDeviceLockAvailable(context) }
    var pendingConfirmCred by remember { mutableStateOf<VaultCredentialEntity?>(null) }

    if (pendingSave != null && offerToSavePasswords) {
        val isExistingUpdate = remember(pendingSave, matchingCredentials) {
            matchingCredentials.any { cred ->
                cred.hasPassword &&
                    cred.username.equals(pendingSave.username.trim(), ignoreCase = true)
            }
        }
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("autofill_save_prompt_bar"),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Save Password",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isExistingUpdate) {
                            "Update password for ${pendingSave.host}?"
                        } else {
                            "Save login for ${pendingSave.host}?"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pendingSave.username.ifBlank { "Encrypted in Kaspa Vault" },
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onDismissPendingSave, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Not now", color = TextMuted, fontSize = 12.sp)
                }
                Button(
                    onClick = { onSavePending(pendingSave) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 40.dp).testTag("autofill_confirm_save_button")
                ) {
                    Text(if (isExistingUpdate) "Update" else "Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        return
    }

    val passwordMatches = remember(matchingCredentials) {
        matchingCredentials.filter { it.hasPassword }
    }
    if (passwordMatches.isEmpty() && !showGeneratorSuggestion) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("autofill_fill_bar"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = "Autofill",
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                passwordMatches.forEach { cred ->
                    val breachCount = breachedCredentials[cred.id] ?: 0
                    FilledTonalButton(
                        onClick = {
                            val triggerAuthOrFill = {
                                if (isVaultUnlocked || !hasDeviceLock) {
                                    onUnlockAndFill(cred)
                                } else {
                                    BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                        context = context,
                                        title = "Sign in to ${cred.host}",
                                        subtitle = "Verify identity to fill saved password",
                                        onSuccess = { onUnlockAndFill(cred) },
                                        onError = {}
                                    )
                                }
                            }
                            if (!autoSignInEnabled) {
                                pendingConfirmCred = cred
                            } else {
                                triggerAuthOrFill()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 40.dp).testTag("autofill_credential_pill")
                    ) {
                        Icon(
                            imageVector = if (breachCount > 0) Icons.Default.WarningAmber else Icons.Default.Fingerprint,
                            contentDescription = if (breachCount > 0) "Breached password warning" else null,
                            tint = if (breachCount > 0) RedTamper else ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = cred.username.ifBlank { cred.siteName },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (showGeneratorSuggestion) {
                    OutlinedButton(
                        onClick = {
                            val generated = PasswordGenerator.generate(length = 20, includeSymbols = true)
                            onFillGeneratedPassword(generated)
                        },
                        border = BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Generate Strong Password", fontSize = 12.sp, color = EmeraldMesh)
                    }
                }
            }
            IconButton(onClick = onDismissAutofill, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss autofill bar", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }

    pendingConfirmCred?.let { cred ->
        AlertDialog(
            onDismissRequest = { pendingConfirmCred = null },
            containerColor = SurfaceCard,
            icon = {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = ElectricCyan)
            },
            title = {
                Text(
                    text = "Confirm Sign-In",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Auto sign-in is disabled. Sign in to ${cred.host} as ${cred.username.ifBlank { cred.siteName }} using your stored credentials?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmCred = null
                        if (!hasDeviceLock) {
                            onUnlockAndFill(cred)
                        } else {
                            BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                context = context,
                                title = "Confirm Sign-In: ${cred.host}",
                                subtitle = "Verify identity to sign in with stored credentials",
                                onSuccess = { onUnlockAndFill(cred) },
                                onError = {}
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("autofill_manual_confirm_button")
                ) {
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmCred = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
