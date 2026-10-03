package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.ConnectionStatus
import com.nudge.app.data.PhysicianConnection
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme
import kotlinx.coroutines.launch

@Composable
fun ConnectPhysicianScreen(
    username: String,
    onBack: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    var clinician by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<PhysicianConnection?>(null) }
    val connections by viewModel.patientConnections.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) { viewModel.setPatientContext(username) }

    fun submit() {
        val name = clinician.trim()
        if (name.isEmpty()) {
            error = "Enter your clinician's username."
            return
        }
        scope.launch {
            error = viewModel.requestClinician(name, username)
            if (error == null) clinician = ""
        }
    }

    NudgeScreen(title = "Care team", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Clinicians you add can see the sessions you choose to share.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                NudgeCard(Modifier.fillMaxWidth()) {
                    NudgeTextField(
                        value = clinician,
                        onValueChange = { clinician = it; error = null },
                        label = "Clinician's username",
                        leadingIcon = Icons.Default.MedicalServices,
                        imeAction = ImeAction.Send,
                        onImeAction = ::submit
                    )
                    ErrorText(error)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Send request", icon = Icons.Default.PersonAdd, onClick = ::submit)
                }
                Spacer(Modifier.height(16.dp))
                if (connections.isNotEmpty()) SectionLabel("Your clinicians")
            }
            if (connections.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Groups,
                        title = "No clinicians yet",
                        message = "Once you send a request, it shows up here."
                    )
                }
            }
            items(connections, key = { it.physicianEmail }) { connection ->
                NudgeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.MedicalServices)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(connection.physicianEmail, style = MaterialTheme.typography.titleMedium)
                            StatusChip(connection.status)
                        }
                        IconButton(onClick = { pendingRemoval = connection }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { connection ->
        ConfirmDialog(
            title = "Remove ${connection.physicianEmail}?",
            message = "They will no longer see your sessions.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.removeConnection(connection.physicianEmail, username)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null }
        )
    }
}

@Composable
fun StatusChip(status: ConnectionStatus) {
    val colors = NudgeTheme.colors
    val (label, color) = when (status) {
        ConnectionStatus.ACCEPTED -> "Connected" to colors.success
        ConnectionStatus.PENDING -> "Waiting for approval" to colors.warning
        ConnectionStatus.REJECTED -> "Declined" to colors.danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(8.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
