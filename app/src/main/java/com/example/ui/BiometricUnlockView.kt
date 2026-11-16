package com.example.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AmberCentral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldMesh
import com.example.ui.theme.KaspaTea
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RedTamper
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.BiometricAuthHelper.findFragmentActivity

/**
 * Full-screen app-level privacy lock gate rendered before showing the main browser interface.
 *
 * Security Invariants (RULES.md):
 * 1. Separate from wallet signing: invoking `onUnlockSucceeded` unlocks only the browser UI
 *    and MUST NEVER approve any payment or bypass password/biometric prompts when sending.
 * 2. Forgotten lock password recovery: NEVER wipes or resets the wallet; falls back directly
 *    to Android's OS-level `BiometricPrompt` with `DEVICE_CREDENTIAL` (fingerprint, face, or phone PIN).
 */
@Composable
fun BiometricUnlockView(
    hasCustomPassword: Boolean,
    onUnlockSucceeded: () -> Unit,
    modifier: Modifier = Modifier,
    onVerifyPassword: ((String) -> Boolean)? = null,
    onDeviceCredentialRecovered: (() -> Unit)? = null,
    autoPromptBiometric: Boolean = true,
    onLaunchBiometricOverride: ((isForgotPasswordRecovery: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isPromptActive by remember { mutableStateOf(false) }
    var showForgotNotice by remember { mutableStateOf(false) }

    val deviceCredentialReady = remember(context) {
        canAuthenticateWithDeviceOrBiometric(context)
    }

    fun launchSystemBiometricPrompt(isRecoveryMode: Boolean) {
        errorMessage = null
        if (onLaunchBiometricOverride != null) {
            onLaunchBiometricOverride(isRecoveryMode)
            return
        }

        val activity = context.findFragmentActivity()
        if (activity == null) {
            errorMessage = "Biometric prompt requires an active window. Tap Retry or use your lock password."
            return
        }

        if (!canAuthenticateWithDeviceOrBiometric(context)) {
            if (isRecoveryMode) {
                errorMessage = "No phone PIN, pattern, or biometric lock is set in Android Settings. Set a device lock first — your wallet and seeds remain intact."
            } else {
                errorMessage = "Device biometric or screen lock is not configured on this phone."
            }
            return
        }

        isPromptActive = true
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isPromptActive = false
                if (isRecoveryMode) {
                    onDeviceCredentialRecovered?.invoke()
                }
                // App-level unlock only; wallet signing still requires independent confirmation per tx.
                onUnlockSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isPromptActive = false
                errorMessage = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    "Unlock cancelled. Authenticate to open Kazium."
                } else {
                    errString.toString().ifBlank { "Authentication interrupted. Please try again." }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                errorMessage = "Biometric not recognized. Try again or use your phone PIN."
            }
        }

        val promptInfo = buildBrowserUnlockPromptInfo(
            title = if (isRecoveryMode) "Unlock with Phone Lock" else "Unlock Kazium Browser",
            subtitle = if (isRecoveryMode) {
                "Verify your phone PIN, pattern, or biometric. Your wallet will not be reset."
            } else {
                "Browser privacy lock only — wallet sends still authenticate separately"
            }
        )

        try {
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (t: Throwable) {
            isPromptActive = false
            errorMessage = t.message ?: "Unable to open system authentication prompt."
        }
    }

    LaunchedEffect(autoPromptBiometric) {
        if (autoPromptBiometric && deviceCredentialReady) {
            launchSystemBiometricPrompt(isRecoveryMode = false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .imePadding()
    ) {
        // Ambient radial depth backdrop (non-flat canvas polish)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricCyan.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.minDimension * 0.75f
                ),
                center = Offset(size.width * 0.5f, size.height * 0.28f),
                radius = size.minDimension * 0.75f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(KaspaTea.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.82f),
                    radius = size.minDimension * 0.55f
                ),
                center = Offset(size.width * 0.2f, size.height * 0.82f),
                radius = size.minDimension * 0.55f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f, fill = false))

            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 34.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceCard.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 22.dp, top = 50.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kazium Locked",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Verify your identity to access open tabs, bookmarks, and saved sessions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Explicit separation policy badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KaspaTea.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, KaspaTea.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("biometric_unlock_separation_notice")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Wallet signing stays separate",
                                    tint = KaspaTea,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Browser unlock never approves payments. Wallet transactions still ask for authentication every time.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        if (errorMessage != null || infoMessage != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                errorMessage?.let { err ->
                                    Surface(
                                        color = RedTamper.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, RedTamper.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("browser_lock_error_banner")
                                    ) {
                                        Text(
                                            text = err,
                                            color = RedTamper,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                infoMessage?.let { info ->
                                    Surface(
                                        color = AmberCentral.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, AmberCentral.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = info,
                                            color = TextPrimary,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (hasCustomPassword && onVerifyPassword != null) {
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    errorMessage = null
                                },
                                label = { Text("Browser lock password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (onVerifyPassword(passwordInput)) {
                                            errorMessage = null
                                            onUnlockSucceeded()
                                        } else {
                                            errorMessage = "Incorrect browser lock password."
                                        }
                                    }
                                ),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedLabelColor = ElectricCyan,
                                    unfocusedLabelColor = TextMuted
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("browser_lock_password_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (onVerifyPassword(passwordInput)) {
                                        errorMessage = null
                                        onUnlockSucceeded()
                                    } else {
                                        errorMessage = "Incorrect browser lock password."
                                    }
                                },
                                enabled = passwordInput.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBg
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("browser_lock_password_submit")
                            ) {
                                Text(
                                    text = "Unlock with Password",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Primary Biometric / Device Credential Unlock Button
                        val biometricModifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("browser_lock_biometric_button")

                        if (hasCustomPassword && onVerifyPassword != null) {
                            OutlinedButton(
                                onClick = { launchSystemBiometricPrompt(isRecoveryMode = false) },
                                enabled = !isPromptActive,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, EmeraldMesh.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldMesh),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                                modifier = biometricModifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPromptActive) "Waiting for Biometrics…" else "Use Biometrics or Phone PIN",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = { launchSystemBiometricPrompt(isRecoveryMode = false) },
                                enabled = !isPromptActive,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBg
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                                modifier = biometricModifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPromptActive) "Authenticating…" else "Unlock with Biometrics / Phone PIN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (hasCustomPassword) {
                            Spacer(modifier = Modifier.height(6.dp))

                            TextButton(
                                onClick = {
                                    showForgotNotice = true
                                    infoMessage = "Recovering via your phone's lock keeps your wallet and recovery phrase intact."
                                    launchSystemBiometricPrompt(isRecoveryMode = true)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("browser_lock_forgot_password_button")
                            ) {
                                Text(
                                    text = "Forgot lock password? Use phone lock",
                                    color = ElectricCyan,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Overlapping badge breaking top border edge for visual depth
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(ElectricCyan.copy(alpha = 0.25f), KaspaTea.copy(alpha = 0.25f))
                            ),
                            shape = CircleShape
                        )
                        .border(1.5.dp, ElectricCyan.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Kazium Privacy Lock",
                        tint = ElectricCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
        }
    }
}

internal fun canAuthenticateWithDeviceOrBiometric(context: Context): Boolean {
    return try {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) {
        false
    }
}

internal fun buildBrowserUnlockPromptInfo(
    title: String = "Unlock Kazium Browser",
    subtitle: String = "Browser privacy lock only — wallet sends still authenticate separately"
): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        // DEVICE_CREDENTIAL is included, so negative button text must NOT be set.
        .build()
}
