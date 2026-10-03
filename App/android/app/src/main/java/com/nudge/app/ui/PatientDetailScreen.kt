package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.TherapySession
import com.nudge.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PatientDetailScreen(
    patientUsername: String,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    val stats by remember(patientUsername) { viewModel.getPatientStats(patientUsername) }.collectAsState(initial = null)
    val allSessions by remember(patientUsername) { viewModel.getPatientSessions(patientUsername) }.collectAsState(initial = emptyList())
    // Clinicians only see what the patient chose to share
    val sessions = allSessions.filter { it.isUploaded }
    val totalMinutes = sessions.sumOf { s -> s.endTime?.let { (it - s.startTime) / 60_000 } ?: 0L }

    NudgeScreen(title = patientUsername, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${sessions.size}", "Shared sessions", Modifier.weight(1f))
                    StatTile("$totalMinutes", "Minutes", Modifier.weight(1f))
                    StatTile("${stats?.highScore ?: 0}", "Game best", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel("Sessions")
            }
            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.FolderOff,
                        title = "Nothing shared yet",
                        message = "Sessions appear here when $patientUsername shares them after a session."
                    )
                }
            }
            items(sessions, key = { it.sessionId }) { session ->
                ActionRow(
                    icon = Icons.Default.EventNote,
                    title = formatSessionDate(session.startTime),
                    subtitle = sessionSubtitle(session),
                    onClick = { onOpenSession(session.sessionId) }
                )
            }
        }
    }
}

fun formatSessionDate(time: Long): String = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(time))

fun sessionSubtitle(session: TherapySession): String {
    val minutes = session.endTime?.let { ((it - session.startTime) / 1000 + 30) / 60 }
    val rest = if (session.restPosition.name == "OPENED") "Rests open" else "Rests closed"
    return listOfNotNull(minutes?.let { "$it min" }, rest, if (session.notes.isNullOrBlank()) null else "Has notes").joinToString(" · ")
}
