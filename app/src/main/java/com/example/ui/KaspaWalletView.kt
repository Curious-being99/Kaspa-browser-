package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountEntity
import com.example.model.KaspaWalletState
import com.example.network.CryptoUtils
import com.example.network.kaspa.KaspaTransactionEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaspaWalletView(
    activeAccount: AccountEntity?,
    walletState: KaspaWalletState,
    onRefresh: () -> Unit,
    onSendKaspa: (recipient: String, amount: Double) -> Unit,
    onOpenUrl: (url: String) -> Unit,
    onNavigateToCreateOrImport: () -> Unit = {},
    onNavigateToDomains: () -> Unit = {},
    onSignOut: () -> Unit = {},
    isWalletLocked: Boolean = false,
    hasWalletPassword: Boolean = false,
    biometricEnabled: Boolean = true,
    onUnlockWalletWithPassword: (password: String) -> Boolean = { false },
    onUnlockWalletWithBiometric: () -> Unit = {},
    onLockWallet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var activeModal by remember { mutableStateOf<WalletModalType>(WalletModalType.NONE) }
    var copyToast by remember { mutableStateOf<String?>(null) }
    var showExposedSeed by remember { mutableStateOf(false) }

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signOutPasswordInput by remember { mutableStateOf("") }
    var signOutError by remember { mutableStateOf<String?>(null) }
    var isSignOutPasswordVisible by remember { mutableStateOf(false) }

    var pullOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(walletState.isLoading) {
        if (!walletState.isLoading) {
            isPullRefreshing = false
        }
    }

    LaunchedEffect(activeAccount?.kaspaAddress) {
        onRefresh()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (scrollState.value == 0 && (dragAmount > 0f || pullOffsetY > 0f)) {
                            change.consume()
                            pullOffsetY = (pullOffsetY + dragAmount * 0.45f).coerceIn(0f, 90f)
                        }
                    },
                    onDragEnd = {
                        if (pullOffsetY >= 40f) {
                            isPullRefreshing = true
                            onRefresh()
                        }
                        pullOffsetY = 0f
                    },
                    onDragCancel = {
                        pullOffsetY = 0f
                    }
                )
            }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sticky Top Pull To Refresh (Frameless at top)
        AnimatedVisibility(
            visible = pullOffsetY > 0f || isPullRefreshing || walletState.isLoading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isPullRefreshing || walletState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Updating Kaspa balance & UTXOs...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                } else {
                    val rotation = (pullOffsetY * 4f) % 360f
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Pull to refresh",
                        tint = ElectricCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(rotationZ = rotation)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pullOffsetY >= 40f) "Release to refresh balance" else "Pull down to refresh",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (pullOffsetY >= 40f) ElectricCyan else TextMuted
                    )
                }
            }
        }
        if (activeAccount == null) {
            // Un-initialized Wallet Onboarding Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(
                        text = "No Active Kaspa Wallet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Create a new self-sovereign Kaspa BlockDAG wallet or import an existing 12-word recovery seed phrase.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onNavigateToCreateOrImport,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create or Import Kaspa Wallet", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else if (isWalletLocked) {
            val context = androidx.compose.ui.platform.LocalContext.current
            var unlockPasswordInput by remember { mutableStateOf("") }
            var unlockError by remember { mutableStateOf<String?>(null) }
            var passwordVisible by remember { mutableStateOf(false) }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricCyan),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "Kaspa Wallet Locked",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Enter your password or use biometric verification to access non-custodial keys, balances, and transfer funds.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )

                    // Password Field
                    OutlinedTextField(
                        value = unlockPasswordInput,
                        onValueChange = {
                            unlockPasswordInput = it
                            unlockError = null
                        },
                        label = { Text("Enter Wallet Password / PIN", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_wallet_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = SurfaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (unlockError != null) {
                        Text(
                            text = unlockError ?: "",
                            fontSize = 11.sp,
                            color = RedTamper,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Unlock Button
                    Button(
                        onClick = {
                            val success = onUnlockWalletWithPassword(unlockPasswordInput.trim())
                            if (!success) {
                                unlockError = "Incorrect wallet password. Try again."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("submit_unlock_wallet_button")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Wallet with Password", color = ObsidianBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (biometricEnabled) {
                        OutlinedButton(
                            onClick = {
                                com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                    context = context,
                                    title = "Unlock Kaspa Wallet",
                                    subtitle = "Use fingerprint, face, or device PIN",
                                    onSuccess = {
                                        onUnlockWalletWithBiometric()
                                    },
                                    onError = { err ->
                                        unlockError = err
                                    }
                                )
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("unlock_biometric_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock with Biometric / Device PIN", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Copy notification toast
            if (copyToast != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "$copyToast copied to clipboard", fontSize = 11.sp, color = ElectricCyan)
                    }
                }
            }

        // Active Account Banner & Network Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = activeAccount?.handle ?: "Unassigned Wallet",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Built-in Real Kaspa L1 Wallet",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hasWalletPassword) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                        modifier = Modifier.clickable { onLockWallet() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock Wallet", fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("BlockDAG L1", fontSize = 9.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Primary Balance Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "TOTAL BALANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.4f".format(walletState.balanceKas),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "KAS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = "≈ $%.2f USD".format(walletState.balanceUsd),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("refresh_kaspa_wallet_button")
                    ) {
                        if (walletState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Balance",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SurfaceCardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Kaspa Address Preview Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val addr = activeAccount?.kaspaAddress ?: walletState.kaspaAddress
                            if (addr.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(addr))
                                copyToast = "Kaspa Address"
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Address", fontSize = 9.sp, color = TextMuted)
                            Text(
                                text = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Address",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Market & Protocol Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1 KAS = ", fontSize = 10.sp, color = TextMuted)
                        Text("$%.4f USD".format(walletState.priceUsd), fontSize = 10.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                    Text("UTXOs: ${walletState.utxosCount}", fontSize = 10.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { 
                        if (hasWalletPassword) {
                            showSignOutConfirm = true 
                        } else {
                            onSignOut()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("wallet_sign_out_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x1AFF5555), // Subtle transparent red background
                        contentColor = Color(0xFFFF5555)   // Solid red text
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF5555)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Sign Out & Disconnect",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Quick Actions Row (Send, Receive, .k Domains, Explorer)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { activeModal = if (activeModal == WalletModalType.SEND) WalletModalType.NONE else WalletModalType.SEND },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("wallet_send_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeModal == WalletModalType.SEND) ElectricCyan else SurfaceCard
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (activeModal == WalletModalType.SEND) ObsidianBg else ElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "Send",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeModal == WalletModalType.SEND) ObsidianBg else TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Button(
                onClick = { activeModal = if (activeModal == WalletModalType.RECEIVE) WalletModalType.NONE else WalletModalType.RECEIVE },
                modifier = Modifier
                    .weight(1.1f)
                    .height(42.dp)
                    .testTag("wallet_receive_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeModal == WalletModalType.RECEIVE) ElectricCyan else SurfaceCard
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (activeModal == WalletModalType.RECEIVE) ObsidianBg else ElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "Receive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeModal == WalletModalType.RECEIVE) ObsidianBg else TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Button(
                onClick = onNavigateToDomains,
                modifier = Modifier
                    .weight(1.1f)
                    .height(42.dp)
                    .testTag("wallet_kab_domains_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceCard
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        ".k",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Button(
                onClick = {
                    val addr = activeAccount?.kaspaAddress ?: walletState.kaspaAddress
                    if (addr.isNotEmpty()) {
                        onOpenUrl("https://explorer.kaspa.org/addresses/$addr")
                    }
                },
                modifier = Modifier
                    .height(42.dp)
                    .testTag("wallet_explorer_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceCard
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = "Kaspa Explorer",
                    tint = ElectricCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Modal Content Body
        when (activeModal) {
            WalletModalType.SEND -> {
                SendKaspaSection(
                    senderAddress = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                    balanceKas = walletState.balanceKas,
                    priceUsd = walletState.priceUsd,
                    isSending = walletState.isSending,
                    lastBroadcastTxId = walletState.lastBroadcastTxId,
                    statusNotice = walletState.statusNotice,
                    onSend = { recipient, amount ->
                        onSendKaspa(recipient, amount)
                    },
                    onCopyTx = { txId ->
                        clipboardManager.setText(AnnotatedString(txId))
                        copyToast = "Transaction ID"
                    },
                    onOpenExplorer = { txId ->
                        onOpenUrl("https://explorer.kaspa.org/txs/$txId")
                    },
                    onClose = { activeModal = WalletModalType.NONE }
                )
            }
            WalletModalType.RECEIVE -> {
                ReceiveKaspaSection(
                    address = activeAccount?.kaspaAddress ?: walletState.kaspaAddress,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(it))
                        copyToast = "Kaspa Address"
                    },
                    onClose = { activeModal = WalletModalType.NONE }
                )
            }
            WalletModalType.NONE -> {
                // Nothing rendered here
            }
        }

        // Recent BlockDAG Transactions Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent BlockDAG Activity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${walletState.recentTransactions.size} records", fontSize = 10.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (walletState.recentTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No recent transactions on this address yet.", fontSize = 11.sp, color = TextMuted)
                    }
                } else {
                    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                    walletState.recentTransactions.take(5).forEachIndexed { index, tx ->
                        if (index > 0) {
                            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = SurfaceDark,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (tx.type == "RECEIVED") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = if (tx.type == "RECEIVED") EmeraldMesh else ElectricCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (tx.type == "RECEIVED") "Received KAS" else if (tx.type == "DAG_MINT") "L1 Genesis Mint" else "Sent KAS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Tx: ${tx.txId.take(12)}...",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (tx.amountKas > 0) "${if (tx.type == "RECEIVED") "+" else "-"}%.2f KAS".format(tx.amountKas) else "Genesis",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "RECEIVED") EmeraldMesh else TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = dateFormat.format(Date(tx.blockTime)),
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSignOutConfirm) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Dialog(onDismissRequest = { 
                showSignOutConfirm = false
                signOutPasswordInput = ""
                signOutError = null
            }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x1AFF5555),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5555),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Confirm Sign Out",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "For your security, please verify your identity before signing out and disconnecting your decentralized keys.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (signOutError != null) {
                            Text(
                                text = signOutError!!,
                                color = Color(0xFFFF5555),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedTextField(
                            value = signOutPasswordInput,
                            onValueChange = {
                                signOutPasswordInput = it
                                signOutError = null
                            },
                            label = { Text("Wallet Password", fontSize = 11.sp) },
                            singleLine = true,
                            visualTransformation = if (isSignOutPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isSignOutPasswordVisible = !isSignOutPasswordVisible }) {
                                    Icon(
                                        if (isSignOutPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = SurfaceCardBorder,
                                focusedLabelColor = ElectricCyan,
                                unfocusedLabelColor = TextMuted,
                                cursorColor = ElectricCyan
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    showSignOutConfirm = false
                                    signOutPasswordInput = ""
                                    signOutError = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                            ) {
                                Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (onUnlockWalletWithPassword(signOutPasswordInput)) {
                                        showSignOutConfirm = false
                                        onSignOut()
                                    } else {
                                        signOutError = "Incorrect wallet password"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5555))
                            ) {
                                Text("Confirm", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        if (biometricEnabled) {
                            HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    com.example.utils.BiometricAuthHelper.authenticateWithBiometricOrDeviceLock(
                                        context = context,
                                        title = "Confirm Sign Out",
                                        subtitle = "Verify identity to disconnect wallet",
                                        onSuccess = {
                                            showSignOutConfirm = false
                                            onSignOut()
                                        },
                                        onError = { err ->
                                            signOutError = err
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElectricCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock with Biometric", color = ElectricCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SendKaspaSection(
    senderAddress: String,
    balanceKas: Double,
    priceUsd: Double,
    isSending: Boolean,
    lastBroadcastTxId: String? = null,
    statusNotice: String? = null,
    onSend: (recipient: String, amount: Double) -> Unit,
    onCopyTx: (String) -> Unit,
    onOpenExplorer: (String) -> Unit,
    onClose: () -> Unit
) {
    var recipientInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf<String?>(null) }

    val isValidAddress = remember(recipientInput) {
        recipientInput.isBlank() || CryptoUtils.isValidKaspaAddress(recipientInput.trim())
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Send Kaspa (L1 BlockDAG)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recipient Input
            OutlinedTextField(
                value = recipientInput,
                onValueChange = {
                    recipientInput = it
                    sendError = null
                },
                label = { Text("Recipient Kaspa Address", fontSize = 11.sp) },
                placeholder = { Text("kaspa:q...", fontSize = 11.sp) },
                singleLine = true,
                isError = recipientInput.isNotBlank() && !isValidAddress,
                trailingIcon = {
                    if (recipientInput.isNotBlank()) {
                        if (isValidAddress) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Valid Address", tint = EmeraldMesh, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = "Invalid Address", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("send_kaspa_recipient_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0
            val estMass = remember(recipientInput, parsedAmount) {
                KaspaTransactionEngine.estimateTransactionMass(inputsCount = 1, outputsCount = 2, payloadByteCount = 0)
            }
            val estFeeSompis = remember(estMass) {
                KaspaTransactionEngine.calculateFeeForMass(estMass)
            }
            val estFeeKas = remember(estFeeSompis) {
                estFeeSompis / 100_000_000.0
            }

            // Amount Input
            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it
                    sendError = null
                },
                label = { Text("Amount (KAS)", fontSize = 11.sp) },
                placeholder = { Text("0.0", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    OutlinedButton(
                        onClick = {
                            val max = (balanceKas - estFeeKas).coerceAtLeast(0.0)
                            amountInput = "%.8f".format(max).trimEnd('0').let { if (it.endsWith('.')) "${it}0" else it }
                        },
                        modifier = Modifier.height(28.dp).padding(end = 4.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("MAX", fontSize = 10.sp, color = ElectricCyan)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("send_kaspa_amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(10.dp)
            )

            val amountUsd = parsedAmount * priceUsd
            if (parsedAmount > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                val estFeeFormatted = com.example.network.kaspa.KaspaTransactionEngine.formatKas(estFeeKas)
                Text("≈ $%.2f USD (Network Fee: $estFeeFormatted KAS)".format(amountUsd), fontSize = 10.sp, color = TextSecondary)
            }

            val activeError = sendError ?: if (statusNotice?.startsWith("Error", ignoreCase = true) == true) {
                statusNotice.removePrefix("Error:").trim()
            } else null

            if (activeError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(activeError, fontSize = 11.sp, color = Color(0xFFEF4444))
                    }
                }
            }

            if (!lastBroadcastTxId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transaction Broadcasted to Kaspa L1 DAG!", fontSize = 11.sp, color = EmeraldMesh, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TxID: $lastBroadcastTxId",
                            fontSize = 9.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onCopyTx(lastBroadcastTxId) },
                                modifier = Modifier.height(26.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy TxID", fontSize = 9.sp, color = ElectricCyan)
                            }
                            OutlinedButton(
                                onClick = { onOpenExplorer(lastBroadcastTxId) },
                                modifier = Modifier.height(26.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = EmeraldMesh, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kaspa Explorer", fontSize = 9.sp, color = EmeraldMesh)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val target = recipientInput.trim()
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    if (!CryptoUtils.isValidKaspaAddress(target)) {
                        sendError = "Please enter a valid Kaspa (kaspa:q...) address."
                        return@Button
                    }
                    if (amt <= 0.0) {
                        sendError = "Amount must be greater than 0 KAS."
                        return@Button
                    }
                    if (amt > balanceKas) {
                        sendError = "Insufficient balance. Available: %.4f KAS".format(balanceKas)
                        return@Button
                    }
                    onSend(target, amt)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("confirm_send_kaspa_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp),
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianBg, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Broadcast Transaction", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianBg)
                }
            }
        }
    }
}

@Composable
private fun ReceiveKaspaSection(
    address: String,
    onCopy: (address: String) -> Unit,
    onClose: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receive Kaspa (L1)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stylized QR Code Matrix Canvas
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            ) {
                KaspaAddressQrCanvas(address = address)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Your Authentic Kaspa Address", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = address,
                    fontSize = 11.sp,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onCopy(address) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("copy_receive_kaspa_address_button"),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Kaspa Address", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianBg)
            }
        }
    }
}

@Composable
private fun KaspaAddressQrCanvas(address: String) {
    val bitmap = remember(address) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(address, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Kaspa Address QR Code",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("QR Generation Error", fontSize = 10.sp, color = Color.Red)
        }
    }
}

private enum class WalletModalType {
    NONE,
    SEND,
    RECEIVE
}
