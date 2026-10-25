package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SwapScreenOverlay(onClose: () -> Unit) {
    var isKasToKB by remember { mutableStateOf(true) }
    var amountFrom by remember { mutableStateOf("") }
    var unavailableAction by remember { mutableStateOf<String?>(null) }
    
    val KB = "\$KB"
    
    val fromAsset = if (isKasToKB) "KAS" else KB
    val toAsset = if (isKasToKB) KB else "KAS"

    Surface(modifier = Modifier.fillMaxSize(), color = ObsidianBg) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text("Swap Assets", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Swap Interface
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountFrom,
                            onValueChange = { amountFrom = it },
                            label = { Text("From ($fromAsset)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary)
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            IconButton(
                                onClick = { isKasToKB = !isKasToKB },
                                modifier = Modifier.background(SurfaceDark, CircleShape)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Switch", tint = KaspaTea)
                            }
                        }

                        OutlinedTextField(
                            value = "", // TODO: Calculate estimated output
                            onValueChange = {},
                            label = { Text("To ($toAsset) (Est.)") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary)
                        )
                    }
                }

                Button(
                    onClick = { unavailableAction = "Swap" },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Swap $fromAsset to $toAsset", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = SurfaceCardBorder)
                Spacer(modifier = Modifier.height(24.dp))

                // Professional Action Buttons
                Text("Manage $KB", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                    Button(
                        onClick = { unavailableAction = "Buy $KB" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Buy $KB")
                    }
                    Button(
                        onClick = { unavailableAction = "Send $KB" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RedTamper)
                    ) {
                        Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send $KB")
                    }


                }
            }
        }
    }

    unavailableAction?.let { action ->
        AlertDialog(
            onDismissRequest = { unavailableAction = null },
            title = { Text("$action unavailable") },
            text = {
                Text(
                    "No transaction was sent. KaspaRocket's live pool state, KCC20 covenant execution, " +
                        "and wallet signing are not connected to this screen yet."
                )
            },
            confirmButton = {
                TextButton(onClick = { unavailableAction = null }) {
                    Text("OK")
                }
            },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }
}
