package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VaultCredentialEntity
import com.example.engine.BrowserEngineManager
import com.example.security.AuthenticatorAccount
import com.example.security.OtpAuthType
import com.example.security.OtpHashAlgorithm
import com.example.security.PasswordGenerator
import com.example.security.PasswordVaultRepository
import com.example.security.TotpAuthenticator
import com.example.ui.theme.*
import com.example.utils.BiometricAuthHelper
import com.example.viewmodel.DecentralViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PasswordVaultSheet(
    viewModel: DecentralViewModel,
    currentPageUrl: String?,
    onNavigateToUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val credentials by viewModel.vaultCredentials.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val breachedCredentials by viewModel.breachedCredentials.collectAsState()
    val isCheckingBreaches by viewModel.isCheckingBreaches.collectAsState()
    val offerToSavePasswords by viewModel.offerToSavePasswords.collectAsState()
    val autoCreatePasskeyEnabled by viewModel.autoCreatePasskeyEnabled.collectAsState()
    val allowPasskeyUpgradeEnabled by viewModel.allowPasskeyUpgradeEnabled.collectAsState()
    val autoSignInEnabled by viewModel.autoSignInEnabled.collectAsState()
    val passwordAlertsEnabled by viewModel.passwordAlertsEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") }
    var showGenerator by remember { mutableStateOf(false) }
    var showVaultSettings by remember { mutableStateOf(false) }
    var editingCredential by remember { mutableStateOf<VaultCredentialEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddAuthDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VaultCredentialEntity?>(null) }
    var pendingDeleteAuth by remember { mutableStateOf<AuthenticatorAccount?>(null) }
    var revealedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var authError by remember { mutableStateOf<String?>(null) }
    var addAuthExternalError by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val authAccounts by viewModel.authenticatorAccounts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        PasswordGenerator.init(context)
    }
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), key1 = filterMode) {
        if (filterMode == "AUTHENTICATOR") {
            while (true) {
                value = System.currentTimeMillis()
                delay(500L)
            }
        }
    }

    val hasDeviceLock = remember { BiometricAuthHelper.isDeviceLockAvailable(context) }

    val filtered = remember(credentials, searchQuery, filterMode, breachedCredentials) {
        credentials.filter { item ->
            val typeOk = when (filterMode) {
                "PASSWORDS" -> item.hasPassword
                "PASSKEYS" -> item.hasPasskey
                "ALERTS" -> (breachedCredentials[item.id] ?: 0) > 0
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val queryOk = q.isEmpty() ||
                item.siteName.lowercase().contains(q) ||
                item.host.lowercase().contains(q) ||
                item.username.lowercase().contains(q) ||
                item.passkeyRpId.lowercase().contains(q)
            typeOk && queryOk
        }
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
            .testTag("password_vault_dialog"),
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("vault_close_button")
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
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Password Vault",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Password & Passkey Vault",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "AES-256-GCM Encrypted · ${credentials.size} saved · ${authAccounts.size} 2FA",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isVaultUnlocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        revealedIds = emptySet()
                                        viewModel.lockVault()
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Lock Vault",
                                        tint = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (filterMode == "AUTHENTICATOR") {
                                            showAddAuthDialog = true
                                        } else {
                                            showAddDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("vault_add_button")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = if (filterMode == "AUTHENTICATOR") "Add 2FA Authenticator" else "Add Login",
                                        tint = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceCardBorder)
                }
            }

            if (!isVaultUnlocked) {
                // Biometric / Device Lock Gate (Full-page centered with safe bottom/horizontal insets)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Lock",
                                tint = ElectricCyan,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Unlock Encrypted Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Credentials are encrypted on-device with Android Keystore AES-256-GCM and isolated from websites.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        if (authError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = authError.orEmpty(),
                                fontSize = 12.sp,
                                color = RedTamper
                            )
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = {
                                authError = null
                                if (hasDeviceLock) {
                                    BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                        context = context,
                                        title = "Unlock Password Vault",
                                        subtitle = "Verify your identity to view or copy saved logins",
                                        onSuccess = { viewModel.unlockVault() },
                                        onError = { err -> authError = err }
                                    )
                                } else {
                                    viewModel.unlockVault()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 320.dp)
                                .height(48.dp)
                                .testTag("vault_unlock_button")
                        ) {
                            Icon(
                                imageVector = if (hasDeviceLock) Icons.Default.Fingerprint else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasDeviceLock) "Unlock with Biometrics / PIN" else "Unlock Vault (No Screen Lock)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!hasDeviceLock) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tip: Enable a device PIN or fingerprint in Android Settings for biometric vault gating.",
                                fontSize = 11.sp,
                                color = AmberCentral
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                            )
                            .padding(horizontal = 14.dp)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search sites, usernames, passkeys...", color = TextMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "ALL" to "All",
                                "AUTHENTICATOR" to "Authenticator",
                                "PASSWORDS" to "Passwords",
                                "PASSKEYS" to "Passkeys",
                                "ALERTS" to "Alerts"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = filterMode == key,
                                    onClick = { filterMode = key },
                                    leadingIcon = if (key == "AUTHENTICATOR") {
                                        { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                    } else null,
                                    label = {
                                        val countSuffix = when {
                                            key == "ALERTS" && breachedCredentials.isNotEmpty() -> " (${breachedCredentials.size})"
                                            key == "AUTHENTICATOR" && authAccounts.isNotEmpty() -> " (${authAccounts.size})"
                                            else -> ""
                                        }
                                        Text("$label$countSuffix", fontSize = 12.sp)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = when (key) {
                                            "ALERTS" -> RedTamper.copy(alpha = 0.22f)
                                            "AUTHENTICATOR" -> EmeraldMesh.copy(alpha = 0.2f)
                                            else -> ElectricCyan.copy(alpha = 0.2f)
                                        },
                                        selectedLabelColor = when (key) {
                                            "ALERTS" -> RedTamper
                                            "AUTHENTICATOR" -> EmeraldMesh
                                            else -> ElectricCyan
                                        },
                                        containerColor = SurfaceDark,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = if (key == "AUTHENTICATOR") Modifier.testTag("vault_authenticator_chip") else Modifier
                                )
                            }
                            FilterChip(
                                selected = showGenerator,
                                onClick = { showGenerator = !showGenerator },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Generator", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletBridge.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceDark,
                                    labelColor = TextSecondary
                                )
                            )
                            FilterChip(
                                selected = showVaultSettings,
                                onClick = { showVaultSettings = !showVaultSettings },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Options", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = SurfaceDark,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("vault_options_chip")
                            )
                        }

                        if (breachedCredentials.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RedTamper.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, RedTamper.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filterMode = "ALERTS" }
                                    .testTag("vault_breach_alert_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RedTamper, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Password Alert: ${breachedCredentials.size} compromised password(s)",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RedTamper
                                        )
                                        Text(
                                            text = "Found online in public data breaches. Update affected passwords now.",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.checkSavedPasswordsForBreaches(notifyOnNewBreach = false, userInitiated = true) },
                                        enabled = !isCheckingBreaches
                                    ) {
                                        Text(if (isCheckingBreaches) "..." else "Recheck", color = ElectricCyan, fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }

                        if (showVaultSettings) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SurfaceCard,
                                border = BorderStroke(1.dp, SurfaceCardBorder),
                                modifier = Modifier.fillMaxWidth().testTag("vault_options_panel")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VaultOptionSwitchRow(
                                        title = "Offer to save passwords",
                                        subtitle = "Prompt to save or update website logins",
                                        checked = offerToSavePasswords,
                                        onCheckedChange = { viewModel.toggleOfferToSavePasswords(it) },
                                        testTag = "vault_opt_offer_save"
                                    )
                                    VaultOptionSwitchRow(
                                        title = "Automatically create a passkey to sign in faster",
                                        subtitle = "Create passkeys seamlessly when supported",
                                        checked = autoCreatePasskeyEnabled,
                                        onCheckedChange = { viewModel.toggleAutoCreatePasskey(it) },
                                        testTag = "vault_opt_auto_create_passkey"
                                    )
                                    VaultOptionSwitchRow(
                                        title = "Allow passkey account upgrades",
                                        subtitle = "Allow sites and apps to upgrade existing accounts to use passkeys.",
                                        checked = allowPasskeyUpgradeEnabled,
                                        onCheckedChange = { viewModel.toggleAllowPasskeyUpgrade(it) },
                                        testTag = "vault_opt_allow_passkey_upgrade"
                                    )
                                    VaultOptionSwitchRow(
                                        title = "Auto sign-in",
                                        subtitle = "Automatically sign in using stored credentials. If disabled, asks confirmation every time.",
                                        checked = autoSignInEnabled,
                                        onCheckedChange = { viewModel.toggleAutoSignIn(it) },
                                        testTag = "vault_opt_auto_sign_in"
                                    )
                                    VaultOptionSwitchRow(
                                        title = "Password alerts",
                                        subtitle = "Password alerts will notify you when your saved passwords are found online",
                                        checked = passwordAlertsEnabled,
                                        onCheckedChange = { viewModel.togglePasswordAlerts(it) },
                                        testTag = "vault_opt_password_alerts"
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.checkSavedPasswordsForBreaches(notifyOnNewBreach = true, userInitiated = true) },
                                        enabled = !isCheckingBreaches && passwordAlertsEnabled,
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).testTag("vault_run_breach_audit_button")
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCheckingBreaches) "Checking saved passwords..." else "Check Saved Passwords for Breaches",
                                            fontSize = 12.sp,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }

                        if (showGenerator) {
                            Spacer(modifier = Modifier.height(8.dp))
                            VaultPasswordGeneratorCard(
                                onCopy = { pwd ->
                                    clipboard.setText(AnnotatedString(pwd))
                                    viewModel.setStatusMessage("Generated password copied to clipboard")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (filterMode == "AUTHENTICATOR") {
                            val q = searchQuery.trim().lowercase()
                            val filteredAuth = remember(authAccounts, q) {
                                if (q.isEmpty()) authAccounts else authAccounts.filter {
                                    it.issuer.lowercase().contains(q) || it.accountName.lowercase().contains(q)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live 2FA Authenticator",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldMesh
                                )
                                FilledTonalButton(
                                    onClick = { showAddAuthDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("vault_add_2fa_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enter setup key", fontSize = 12.sp)
                                }
                            }
                            if (filteredAuth.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = "2FA Authenticator",
                                            tint = EmeraldMesh.copy(alpha = 0.7f),
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (authAccounts.isEmpty()) "No 2FA keys added yet" else "No matching 2FA keys",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Enter an app or website's 2FA setup key with Time based or Counter based verification to generate real codes offline.",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { showAddAuthDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMesh, contentColor = ObsidianBg),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Enter 2FA Setup Key", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(filteredAuth, key = { it.id }) { acct ->
                                        val secret = remember(acct.id, acct.encryptedSecret) {
                                            viewModel.authenticatorRepository.decryptSecret(acct)
                                        }
                                        AuthenticatorAccountCard(
                                            account = acct,
                                            decryptedSecret = secret,
                                            nowMillis = nowMillis,
                                            onCopyCode = { rawCode ->
                                                clipboard.setText(AnnotatedString(rawCode))
                                                viewModel.setStatusMessage("2FA code copied for ${acct.issuer}")
                                            },
                                            onNextHotp = {
                                                viewModel.incrementAuthenticatorHotp(acct.id, acct.issuer)
                                            },
                                            onDelete = { pendingDeleteAuth = acct }
                                        )
                                    }
                                }
                            }
                        } else if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (credentials.isEmpty()) "No saved logins or passkeys yet" else "No matching credentials",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { showAddDialog = true },
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Login Credential", color = ElectricCyan)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(filtered, key = { it.id }) { item ->
                                    val isRevealed = revealedIds.contains(item.id)
                                    val decrypted = remember(item.id, item.encryptedPassword, isRevealed) {
                                        if (isRevealed && item.hasPassword) viewModel.decryptVaultPassword(item) else null
                                    }
                                    VaultCredentialCard(
                                        item = item,
                                        isRevealed = isRevealed,
                                        revealedPassword = decrypted,
                                        breachCount = breachedCredentials[item.id] ?: 0,
                                        onToggleReveal = {
                                            revealedIds = if (isRevealed) revealedIds - item.id else revealedIds + item.id
                                        },
                                        onCopyUsername = {
                                            clipboard.setText(AnnotatedString(item.username))
                                            viewModel.setStatusMessage("Username copied")
                                        },
                                        onCopyPassword = {
                                            val pw = viewModel.decryptVaultPassword(item)
                                            if (pw != null) {
                                                clipboard.setText(AnnotatedString(pw))
                                                viewModel.markVaultCredentialUsed(item.id)
                                                viewModel.setStatusMessage("Password copied to clipboard")
                                            } else {
                                                viewModel.setStatusMessage("Could not decrypt password")
                                            }
                                        },
                                        onOpenSite = {
                                            viewModel.markVaultCredentialUsed(item.id)
                                            onNavigateToUrl(item.origin)
                                            onDismiss()
                                        },
                                        onOpenCustomTab = {
                                            BrowserEngineManager.openInCustomTab(context, item.origin)
                                        },
                                        onEdit = { editingCredential = item },
                                        onDelete = { pendingDelete = item }
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Actions: Passkey Diagnostics & Android Autofill Settings
                Surface(
                    color = SurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = SurfaceCardBorder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .align(Alignment.CenterHorizontally)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.setShowWebAuthnRpIdDialog(true) },
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Passkey Status", fontSize = 12.sp, color = ElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    val launched = runCatching {
                                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                        true
                                    }.getOrDefault(false)
                                    if (!launched) {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        }
                                    }
                                },
                                border = BorderStroke(1.dp, SurfaceCardBorder),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OS Autofill", fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingCredential != null) {
        VaultEditCredentialDialog(
            initial = editingCredential,
            defaultUrl = currentPageUrl,
            onDismiss = {
                showAddDialog = false
                editingCredential = null
            },
            onSave = { siteName, url, user, rawPassword, notes ->
                val existing = editingCredential
                if (existing == null) {
                    viewModel.saveVaultPassword(
                        url = url,
                        siteName = siteName,
                        username = user,
                        rawPassword = rawPassword,
                        notes = notes
                    )
                } else {
                    viewModel.updateVaultCredential(
                        id = existing.id,
                        siteName = siteName,
                        originUrl = url,
                        username = user,
                        newRawPassword = rawPassword.takeIf { it.isNotBlank() },
                        notes = notes
                    )
                }
                showAddDialog = false
                editingCredential = null
            }
        )
    }

    pendingDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SurfaceCard,
            title = { Text("Delete Credential?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove saved login for ${toDelete.host} (${toDelete.username.ifBlank { "account" }}) from the encrypted vault?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVaultCredential(toDelete.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedTamper, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showAddAuthDialog) {
        AddAuthenticatorDialog(
            onDismiss = {
                addAuthExternalError = null
                showAddAuthDialog = false
            },
            externalError = addAuthExternalError,
            onSaveManual = { accountTitle, secret, type ->
                addAuthExternalError = null
                coroutineScope.launch {
                    val res = viewModel.saveAuthenticatorSecretSuspend(
                        accountTitle = accountTitle,
                        rawBase32Secret = secret,
                        type = type
                    )
                    if (res.isSuccess) {
                        showAddAuthDialog = false
                    } else {
                        addAuthExternalError = res.exceptionOrNull()?.message ?: "Unable to store 2FA key"
                    }
                }
                null
            }
        )
    }

    pendingDeleteAuth?.let { authDelete ->
        val label = if (authDelete.accountName.isNotBlank()) {
            "${authDelete.issuer} (${authDelete.accountName})"
        } else {
            authDelete.issuer
        }
        AlertDialog(
            onDismissRequest = { pendingDeleteAuth = null },
            containerColor = SurfaceCard,
            title = { Text("Remove 2FA Key?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete the 2FA Authenticator key for $label? Make sure you still have your backup codes or recovery method on that site.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAuthenticatorAccount(authDelete.id, authDelete.issuer)
                        pendingDeleteAuth = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedTamper, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAuth = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VaultOptionSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = subtitle, fontSize = 10.5.sp, color = TextSecondary, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = ElectricCyan
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun VaultCredentialCard(
    item: VaultCredentialEntity,
    isRevealed: Boolean,
    revealedPassword: String?,
    breachCount: Int = 0,
    onToggleReveal: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenCustomTab: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, if (breachCount > 0) RedTamper.copy(alpha = 0.65f) else SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.siteName.ifBlank { item.host },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.host,
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val typeLabel = when (item.credentialType) {
                    VaultCredentialEntity.TYPE_PASSKEY_AND_PASSWORD -> "Passkey + Login"
                    VaultCredentialEntity.TYPE_PASSKEY -> "Passkey"
                    else -> "Password"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (breachCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RedTamper.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "Breached",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RedTamper,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = (if (item.hasPasskey) EmeraldMesh else VioletBridge).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.hasPasskey) EmeraldMesh else Color(0xFFA5B4FC),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (breachCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Warning: Password appeared in $breachCount online breach exposure(s). Edit and rotate now.",
                    fontSize = 11.sp,
                    color = RedTamper
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.username.ifBlank { "(no username)" },
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopyUsername, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy username", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            if (item.hasPassword) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRevealed) (revealedPassword ?: "Decrypt error") else "••••••••••••",
                        color = if (isRevealed) TextPrimary else TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(onClick = onToggleReveal, modifier = Modifier.size(40.dp).testTag("vault_reveal_button")) {
                            Icon(
                                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isRevealed) "Hide password" else "Reveal password",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onCopyPassword, modifier = Modifier.size(40.dp).testTag("vault_copy_password_button")) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Copy password", tint = EmeraldMesh, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            } else if (item.passkeyRpId.isNotBlank()) {
                Text(
                    text = "RP ID: ${item.passkeyRpId}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenSite, modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp, color = ElectricCyan)
                    }
                    TextButton(onClick = onOpenCustomTab, modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Custom Tab", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit credential", tint = TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete credential", tint = RedTamper, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPasswordGeneratorCard(onCopy: (String) -> Unit) {
    var length by remember { mutableIntStateOf(24) }
    var includeSymbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }
    var generated by remember(length, includeSymbols, avoidAmbiguous) {
        mutableStateOf(
            PasswordGenerator.generateUnique(
                length = length,
                includeSymbols = includeSymbols,
                avoidAmbiguous = avoidAmbiguous
            )
        )
    }
    val strength = remember(generated) { PasswordGenerator.evaluateStrength(generated) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Collision-Free CSPRNG Generator", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Per-install 256-bit salt · SHA-512 DRBG · 0-repeat guard", fontSize = 10.sp, color = TextMuted)
                }
                Text("${strength.label} (${strength.entropyBits}b)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EmeraldMesh)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (strength.score / 4f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (strength.score >= 3) EmeraldMesh else AmberCentral,
                trackColor = SurfaceDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = generated,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            generated = PasswordGenerator.generateUnique(
                                length = length,
                                includeSymbols = includeSymbols,
                                avoidAmbiguous = avoidAmbiguous
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate password", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onCopy(generated) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy generated password", tint = EmeraldMesh, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Len: $length", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt().coerceIn(12, 48) },
                    valueRange = 12f..48f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { includeSymbols = !includeSymbols }
                ) {
                    Checkbox(checked = includeSymbols, onCheckedChange = { includeSymbols = it })
                    Text("!@#", fontSize = 11.sp, color = TextSecondary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { avoidAmbiguous = !avoidAmbiguous }
                ) {
                    Checkbox(checked = avoidAmbiguous, onCheckedChange = { avoidAmbiguous = it })
                    Text("No 0O", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
internal fun AuthenticatorAccountCard(
    account: AuthenticatorAccount,
    decryptedSecret: String?,
    nowMillis: Long,
    onCopyCode: (String) -> Unit,
    onNextHotp: () -> Unit,
    onDelete: () -> Unit
) {
    val totpSnapshot = remember(decryptedSecret, nowMillis, account.periodSeconds, account.digits, account.algorithm, account.type) {
        if (decryptedSecret != null && account.type == OtpAuthType.TOTP) {
            runCatching {
                TotpAuthenticator.computeTotpSnapshot(
                    base32Secret = decryptedSecret,
                    nowMillis = nowMillis,
                    periodSeconds = account.periodSeconds,
                    digits = account.digits,
                    algorithm = account.algorithm
                )
            }.getOrNull()
        } else {
            null
        }
    }
    val hotpCode = remember(decryptedSecret, account.counter, account.digits, account.algorithm, account.type) {
        if (decryptedSecret != null && account.type == OtpAuthType.HOTP) {
            runCatching {
                TotpAuthenticator.generateHotpCode(
                    base32Secret = decryptedSecret,
                    counter = account.counter,
                    digits = account.digits,
                    algorithm = account.algorithm
                )
            }.getOrNull()
        } else {
            null
        }
    }
    val rawCode = totpSnapshot?.code ?: hotpCode ?: "------"
    val displayCode = totpSnapshot?.formattedCode
        ?: hotpCode?.let { TotpAuthenticator.formatCodeForDisplay(it) }
        ?: "Decrypt error"
    val isExpiring = totpSnapshot != null && totpSnapshot.remainingSeconds <= 5
    val codeColor = when {
        decryptedSecret == null -> RedTamper
        isExpiring -> AmberCentral
        else -> ElectricCyan
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, if (isExpiring) AmberCentral.copy(alpha = 0.55f) else SurfaceCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = rawCode.all { it.isDigit() }) { onCopyCode(rawCode) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.issuer,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (account.accountName.isNotBlank() && !account.accountName.equals(account.issuer, ignoreCase = true)) {
                        Text(
                            text = account.accountName,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldMesh.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (account.type == OtpAuthType.TOTP) {
                            "Time based · ${account.periodSeconds}s"
                        } else {
                            "Counter based · #${account.counter}"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldMesh,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayCode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        letterSpacing = 2.sp,
                        color = codeColor
                    )
                    if (totpSnapshot != null && totpSnapshot.remainingSeconds <= 7) {
                        Text(
                            text = "Next: ${totpSnapshot.formattedNextCode}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (totpSnapshot != null) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isExpiring) AmberCentral else EmeraldMesh).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${totpSnapshot.remainingSeconds}s",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isExpiring) AmberCentral else EmeraldMesh,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    } else if (account.type == OtpAuthType.HOTP) {
                        OutlinedButton(
                            onClick = onNextHotp,
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Next Code", fontSize = 11.5.sp, color = ElectricCyan)
                        }
                    }
                    IconButton(
                        onClick = { if (rawCode.all { it.isDigit() }) onCopyCode(rawCode) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy 2FA code", tint = EmeraldMesh, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete 2FA key", tint = RedTamper, modifier = Modifier.size(17.dp))
                    }
                }
            }

            if (totpSnapshot != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { totpSnapshot.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (isExpiring) AmberCentral else EmeraldMesh,
                    trackColor = SurfaceCardBorder.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
internal fun AddAuthenticatorDialog(
    onDismiss: () -> Unit,
    externalError: String? = null,
    onSaveManual: (
        accountTitle: String,
        secret: String,
        type: OtpAuthType
    ) -> String?
) {
    var nameInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(OtpAuthType.TOTP) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewCode = remember(secretInput, type) {
        val clean = TotpAuthenticator.normalizeBase32Secret(secretInput)
        if (!TotpAuthenticator.isValidBase32Secret(clean)) null
        else runCatching {
            val code = if (type == OtpAuthType.TOTP) {
                TotpAuthenticator.generateTotpCode(clean, periodSeconds = 30, digits = 6, algorithm = OtpHashAlgorithm.SHA1)
            } else {
                TotpAuthenticator.generateHotpCode(clean, counter = 0L, digits = 6, algorithm = OtpHashAlgorithm.SHA1)
            }
            TotpAuthenticator.formatCodeForDisplay(code)
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Enter setup key",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add the app or site name and its 2FA key from your security settings.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it; errorMsg = null },
                    label = { Text("Account name") },
                    placeholder = { Text("App, site, or account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_name_input")
                )
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it; errorMsg = null },
                    label = { Text("Your key") },
                    placeholder = { Text("Paste 2FA setup key from app or site") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("auth_key_input")
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Type of key",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceDark,
                            border = BorderStroke(1.dp, SurfaceCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { typeMenuExpanded = true }
                                .testTag("auth_type_selector")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (type == OtpAuthType.TOTP) "Time based" else "Counter based",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select type of key",
                                    tint = TextSecondary
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false },
                            modifier = Modifier.background(SurfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Time based", color = TextPrimary) },
                                trailingIcon = if (type == OtpAuthType.TOTP) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldMesh) }
                                } else null,
                                onClick = {
                                    type = OtpAuthType.TOTP
                                    typeMenuExpanded = false
                                },
                                modifier = Modifier.testTag("auth_type_time_based")
                            )
                            DropdownMenuItem(
                                text = { Text("Counter based", color = TextPrimary) },
                                trailingIcon = if (type == OtpAuthType.HOTP) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldMesh) }
                                } else null,
                                onClick = {
                                    type = OtpAuthType.HOTP
                                    typeMenuExpanded = false
                                },
                                modifier = Modifier.testTag("auth_type_counter_based")
                            )
                        }
                    }
                }
                if (previewCode != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("auth_live_preview")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Verified 2FA Code:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = previewCode,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = EmeraldMesh
                            )
                        }
                    }
                }
                val shownError = errorMsg ?: externalError
                if (shownError != null) {
                    Text(shownError, fontSize = 11.5.sp, color = RedTamper)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = nameInput.trim()
                    val cleanSecret = TotpAuthenticator.normalizeBase32Secret(secretInput)
                    errorMsg = when {
                        trimmedName.isBlank() -> "Please enter an app, site, or account name."
                        !TotpAuthenticator.isValidBase32Secret(cleanSecret) ->
                            "Invalid 2FA key. Enter the Base32 setup key (letters A-Z and digits 2-7, at least 8 characters)."
                        else -> onSaveManual(trimmedName, cleanSecret, type)
                    }
                },
                enabled = nameInput.isNotBlank() && secretInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMesh, contentColor = ObsidianBg),
                modifier = Modifier.heightIn(min = 48.dp).testTag("auth_save_button")
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun VaultEditCredentialDialog(
    initial: VaultCredentialEntity?,
    defaultUrl: String?,
    onDismiss: () -> Unit,
    onSave: (siteName: String, url: String, username: String, rawPassword: String, notes: String) -> Unit
) {
    val defaultOrigin = remember(initial, defaultUrl) {
        initial?.origin ?: defaultUrl?.takeIf { it.startsWith("http", ignoreCase = true) } ?: "https://"
    }
    val defaultHost = remember(defaultOrigin) { PasswordVaultRepository.normalizeHost(defaultOrigin).orEmpty() }
    var siteName by remember { mutableStateOf(initial?.siteName ?: defaultHost) }
    var url by remember { mutableStateOf(defaultOrigin) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    val canSave = PasswordVaultRepository.normalizeHost(url) != null &&
        (initial != null || password.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = if (initial == null) "Add Saved Login" else "Edit Credential",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = siteName,
                    onValueChange = { siteName = it },
                    label = { Text("Site Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL or Domain") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initial == null) "Password" else "New Password (leave blank to keep)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        password = PasswordGenerator.generateUnique(length = 24, includeSymbols = true)
                        showPassword = true
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Strong Password", color = ElectricCyan, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(siteName, url, username, password, notes) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
