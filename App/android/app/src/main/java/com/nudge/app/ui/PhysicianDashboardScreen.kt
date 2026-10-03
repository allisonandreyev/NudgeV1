package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.ConnectionStatus
import com.nudge.app.ui.components.*

@Composable
fun PhysicianDashboardScreen(
    physicianUsername: String,
    onOpenPatient: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    val connections by viewModel.physicianConnections.collectAsState()
    LaunchedEffect(physicianUsername) { viewModel.setPhysicianContext(physicianUsername) }

    val pending = connections.filter { it.status == ConnectionStatus.PENDING }
    val patients = connections.filter { it.status == ConnectionStatus.ACCEPTED }

    NudgeScreen(
        title = "Patients",
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pending.isNotEmpty()) {
                item { SectionLabel("Requests") }
                items(pending, key = { "p-" + it.patientUsername }) { request ->
                    NudgeCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Default.Person)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(request.patientUsername, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Wants to share their sessions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Decline", onClick = { viewModel.rejectConnection(request) }, modifier = Modifier.weight(1f))
                            PrimaryButton("Accept", onClick = { viewModel.acceptConnection(request) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            item { SectionLabel("Your patients") }
            if (patients.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.PersonSearch,
                        title = "No patients yet",
                        message = "Patients add you from their Care team screen using your username: $physicianUsername"
                    )
                }
            }
            items(patients, key = { it.patientUsername }) { patient ->
                ActionRow(
                    icon = Icons.Default.Person,
                    title = patient.patientUsername,
                    onClick = { onOpenPatient(patient.patientUsername) }
                )
            }
        }
    }
}
