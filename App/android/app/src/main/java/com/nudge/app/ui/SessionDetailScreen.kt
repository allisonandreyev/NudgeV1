package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.TherapySession
import com.nudge.app.ui.components.*
import com.nudge.app.utils.CSVExporter

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    canEditNotes: Boolean,
    onBack: () -> Unit,
    viewModel: PhysicianViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<TherapySession?>(null) }
    var notes by remember { mutableStateOf("") }
    var savedNotes by remember { mutableStateOf("") }
    val points by remember(sessionId) { viewModel.getPointsForSession(sessionId) }.collectAsState(initial = emptyList())

    LaunchedEffect(sessionId) {
        session = viewModel.getSessionById(sessionId)
        notes = session?.notes ?: ""
        savedNotes = notes
    }

    // One line per sensor, thinned out so long sessions still draw quickly
    val channels = remember(points) {
        List(3) { sensor ->
            val values = points.filter { it.sensorId == sensor }.map { it.value }
            val step = (values.size / 300).coerceAtLeast(1)
            values.filterIndexed { i, _ -> i % step == 0 }
        }
    }
    val peak = points.maxOfOrNull { it.value }?.toInt() ?: 0
    val average = if (points.isEmpty()) 0 else points.map { it.value }.average().toInt()
    val seconds = session?.let { s -> s.endTime?.let { ((it - s.startTime) / 1000).toInt() } } ?: 0

    NudgeScreen(
        title = session?.let { formatSessionDate(it.startTime) } ?: "Session",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { CSVExporter.exportSession(context, sessionId, points) },
                enabled = points.isNotEmpty()
            ) { Icon(Icons.Default.IosShare, contentDescription = "Export CSV") }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${seconds / 60}:${"%02d".format(seconds % 60)}", "Duration", Modifier.weight(1f))
                StatTile("$peak", "Peak signal", Modifier.weight(1f))
                StatTile("$average", "Average", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Muscle activity")
            NudgeCard(Modifier.fillMaxWidth().height(240.dp)) {
                if (points.isEmpty()) {
                    Text(
                        "No signal was recorded in this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    EmgChart(channels, Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Clinical notes")
            if (canEditNotes) {
                NudgeTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Observations, progress, recommendations",
                    singleLine = false,
                    minLines = 4
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    if (notes == savedNotes) "Saved" else "Save notes",
                    enabled = notes != savedNotes,
                    onClick = {
                        viewModel.updateSessionNotes(sessionId, notes)
                        savedNotes = notes
                    }
                )
            } else {
                NudgeCard(Modifier.fillMaxWidth()) {
                    Text(
                        notes.ifBlank { "Your clinician hasn't added notes yet." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
