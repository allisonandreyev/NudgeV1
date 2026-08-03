package com.nudge.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.ConnectionStatus
import com.nudge.app.data.PhysicianConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicianDashboardScreen(
    physicianEmail: String,
    onNavigateToPatientDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    val connections by viewModel.connections.collectAsState()
    
    LaunchedEffect(physicianEmail) {
        viewModel.setUsername(physicianEmail)
    }

    val pendingRequests = connections.filter { it.status == ConnectionStatus.PENDING }
    val myPatients = connections.filter { it.status == ConnectionStatus.ACCEPTED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physician Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Pending Requests Section
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        "PENDING REQUESTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(pendingRequests) { request ->
                    ConnectionRequestItem(
                        request = request,
                        onAccept = { viewModel.acceptConnection(request) },
                        onReject = { viewModel.rejectConnection(request) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // My Patients Section
            item {
                Text(
                    "MY PATIENTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (myPatients.isEmpty()) {
                item {
                    Text(
                        "No connected patients yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(myPatients) { patient ->
                    PatientItem(
                        patient = patient,
                        onClick = { onNavigateToPatientDetail(patient.patientUsername) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionRequestItem(
    request: PhysicianConnection,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(request.patientUsername, fontWeight = FontWeight.Bold)
                Text("Requested connection", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Green)
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
            }
        }
    }
}

@Composable
fun PatientItem(
    patient: PhysicianConnection,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Text(
                patient.patientUsername,
                modifier = Modifier.padding(start = 16.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("View Details", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}
