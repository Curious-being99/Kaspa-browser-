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
import com.example.data.VaultCredentialEntity
import com.example.engine.BrowserEngineManager
import com.example.security.PasswordGenerator
import com.example.security.PasswordVaultRepository
import com.example.ui.theme.*
import com.example.utils.BiometricAuthHelper
import com.example.viewmodel.DecentralViewModel

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
    var pendingDelete by remember { mutableStateOf<VaultCredentialEntity?>(null) }
    var revealedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var authError by remember { mutableStateOf<String?>(null) }

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
                                    text = "AES-256-GCM Encrypted · ${credentials.size} saved",
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
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("vault_add_button")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Login",
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
                            listOf("ALL" to "All", "PASSWORDS" to "Passwords", "PASSKEYS" to "Passkeys", "ALERTS" to "Alerts").forEach { (key, label) ->
                                FilterChip(
                                    selected = filterMode == key,
                                    onClick = { filterMode = key },
                                    label = {
                                        val countSuffix = if (key == "ALERTS" && breachedCredentials.isNotEmpty()) {
                                            " (${breachedCredentials.size})"
                                        } else {
                                            ""
                                        }
                                        Text("$label$countSuffix", fontSize = 12.sp)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (key == "ALERTS") RedTamper.copy(alpha = 0.22f) else ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = if (key == "ALERTS") RedTamper else ElectricCyan,
                                        containerColor = SurfaceDark,
                                        labelColor = TextSecondary
                                    )
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

                        if (filtered.isEmpty()) {
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
    var length by remember { mutableIntStateOf(20) }
    var includeSymbols by remember { mutableStateOf(true) }
    var generated by remember(length, includeSymbols) {
        mutableStateOf(PasswordGenerator.generate(length = length, includeSymbols = includeSymbols))
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
                Text("CSPRNG Password Generator", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${strength.label} (${strength.entropyBits} bits)", fontSize = 11.sp, color = EmeraldMesh)
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
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { generated = PasswordGenerator.generate(length = length, includeSymbols = includeSymbols) },
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
                Text("Length: $length", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(72.dp))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.toInt().coerceIn(10, 32) },
                    valueRange = 10f..32f,
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
            }
        }
    }
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
                        password = PasswordGenerator.generate(length = 20, includeSymbols = true)
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
