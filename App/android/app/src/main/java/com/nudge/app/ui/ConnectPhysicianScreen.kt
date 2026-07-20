package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectPhysicianScreen(onConnectionSuccess: () -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isSuccess) {
            Text(
                text = "Connect with your Physician",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Enter your physician's username or email to link your accounts.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("Physician Username or Email") },
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (identifier.isBlank()) {
                        errorMessage = "Please enter an identifier"
                    } else if (!identifier.contains("@") && identifier.length < 4) {
                        errorMessage = "Please enter a valid email or username"
                    } else {
                        // Demo Logic: In a real app, this would check if the account is a Physician type via API
                        // We simulate a check here
                        if (identifier.lowercase().contains("patient")) {
                            errorMessage = "Error: This account is not a Physician account."
                        } else {
                            isSuccess = true
                            errorMessage = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Connection Request")
            }
        } else {
            // Success State
            Text(
                text = "Connection Requested!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Once your physician accepts, they will be able to view your EMG data.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = onConnectionSuccess) {
                Text("Back to Dashboard")
            }
        }
    }
}
