file_path = 'app/src/main/java/com/example/ui/KaspaTestnetWalletScreen.kt'
with open(file_path, 'r') as f:
    code = f.read()

# Check if WalletSetupStep already exists
if 'enum class WalletSetupStep' not in code:
    # Append WalletSetupStep, WalletSetupFlow, and ManagementOptionCard at the end of the file
    helpers = '''
private enum class WalletSetupStep {
    CREATE_DETAILS,
    IMPORT_PHRASE
}

@Composable
private fun WalletSetupFlow(
    initialStep: WalletSetupStep,
    viewModel: DecentralViewModel,
    onClose: () -> Unit
) {
    var currentStep by remember { mutableStateOf(initialStep) }
    var passwordInput by remember { mutableStateOf("") }
    var importPhraseInput by remember { mutableStateOf("") }
    var accountHandleInput by remember { mutableStateOf("Kaspa Wallet") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .clickable(enabled = false) {}
            .testTag("wallet_setup_flow_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (currentStep == WalletSetupStep.CREATE_DETAILS) "Create New Wallet" else "Import Existing Wallet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (currentStep == WalletSetupStep.CREATE_DETAILS) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Set up a secure password to encrypt your newly generated BIP-39 mnemonic seed phrase on-device.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )

                            OutlinedTextField(
                                value = accountHandleInput,
                                onValueChange = { accountHandleInput = it },
                                label = { Text("Wallet Account Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { 
                                    passwordInput = it
                                    errorText = null
                                },
                                label = { Text("Security Password (8+ characters)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("setup_password_input"),
                                isError = errorText != null,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    errorBorderColor = RedTamper
                                )
                            )

                            if (errorText != null) {
                                Text(errorText!!, fontSize = 12.sp, color = RedTamper)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (passwordInput.length < 8) {
                                        errorText = "Password must be at least 8 characters."
                                        return@Button
                                    }
                                    scope.launch {
                                        viewModel.createNewWalletWithPassword(accountHandleInput.ifBlank { "Kaspa Wallet" }, passwordInput)
                                        onClose()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_create_submit_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = SurfaceDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate & Secure Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Enter your 12 or 24-word recovery phrase separated by spaces, along with an optional wallet password.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )

                            OutlinedTextField(
                                value = importPhraseInput,
                                onValueChange = { 
                                    importPhraseInput = it
                                    errorText = null
                                },
                                label = { Text("12 or 24-Word Recovery Phrase") },
                                modifier = Modifier.fillMaxWidth().height(120.dp).testTag("setup_import_phrase_input"),
                                isError = errorText != null,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    errorBorderColor = RedTamper
                                )
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Wallet Password (Optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = KaspaTea,
                                    unfocusedBorderColor = SurfaceCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            if (errorText != null) {
                                Text(errorText!!, fontSize = 12.sp, color = RedTamper)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val trimmed = importPhraseInput.trim()
                                    if (trimmed.split(Regex("\\\\s+")).size < 12) {
                                        errorText = "Please enter a valid 12 or 24-word mnemonic phrase."
                                        return@Button
                                    }
                                    scope.launch {
                                        viewModel.importWalletWithPhrase(trimmed, passwordInput)
                                        onClose()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_import_submit_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = KaspaTea),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = SurfaceDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Wallet", color = SurfaceDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = KaspaTea,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
'''
    code = code + helpers
    with open(file_path, 'w') as f:
        f.write(code)
    print('APPENDED HELPERS SUCCESSFULLY')
else:
    print('HELPERS ALREADY PRESENT')
"
