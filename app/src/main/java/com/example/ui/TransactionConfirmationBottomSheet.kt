package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionConfirmationBottomSheet(
    title: String,
    amount: String,
    asset: String,
    recipient: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            
            Text("Confirm the following transaction:", color = TextSecondary)
            
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Amount: $amount $asset", color = TextPrimary)
                    Text("To: $recipient", color = TextSecondary, fontSize = 12.sp)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTea)
                ) {
                    Text("Confirm & Sign", color = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
