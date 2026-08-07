package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.ConnectionStatus
import com.nudge.app.data.PhysicianConnection
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun ConnectPhysicianScreen(
    username: String,
    onConnectionSuccess: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    var identifier by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val connections by viewModel.patientConnections.collectAsState()

    LaunchedEffect(username) {
        viewModel.setPatientContext(username)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Physician Connections",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Request Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connect with a New Physician", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Physician Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (identifier.isBlank()) {
                            errorMessage = "Please enter an email"
                        } else {
                            viewModel.connectPhysician(identifier, username, "Physician")
                            identifier = ""
                            errorMessage = null
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                ) {
                    Text("Send Request")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "YOUR PHYSICIANS",
            modifier = Modifier.align(Alignment.Start),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Note: Currently PhysicianViewModel.connections returns connections for a physician.
            // I need to filter or update the ViewModel.
            // For now, I'll just show the list and assume the data layer will be fixed.
            items(connections) { connection ->
                PhysicianListItem(
                    connection = connection,
                    onDelete = { viewModel.removeConnection(connection.physicianEmail, username) }
                )
            }
        }
        
        Button(onClick = onConnectionSuccess, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
fun PhysicianListItem(connection: PhysicianConnection, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(connection.physicianEmail, fontWeight = FontWeight.Bold)
                Text(
                    text = connection.status.name,
                    fontSize = 10.sp,
                    color = when(connection.status) {
                        ConnectionStatus.ACCEPTED -> Color(0xFF4CAF50)
                        ConnectionStatus.PENDING -> Color(0xFFFF9800)
                        ConnectionStatus.REJECTED -> Color.Red
                    }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectPhysicianScreenPreview() {
    NudgeTheme {
        ConnectPhysicianScreen(username = "test", onConnectionSuccess = {})
    }
}
