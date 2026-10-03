package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.nudge.app.Routes
import com.nudge.app.ai.SavedModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.ModelSync
import com.nudge.app.data.TherapySessionDao
import com.nudge.app.data.UserStatsDao
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionDao: TherapySessionDao,
    private val statsDao: UserStatsDao
) : ViewModel() {
    fun sessionsThisWeek(username: String): Flow<Int> {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return sessionDao.getSessionsForUser(username).map { list -> list.count { it.startTime >= weekAgo } }
    }

    fun bestScore(username: String): Flow<Int> = statsDao.getUserStats(username).map { it?.highScore ?: 0 }
}

@Composable
fun HomeScreen(
    username: String,
    bluetooth: BluetoothViewModel,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val status by bluetooth.deviceStatus.collectAsState()
    val model by bluetooth.userModel.collectAsState()
    val sync by bluetooth.modelSync.collectAsState()
    val sessions by remember(username) { viewModel.sessionsThisWeek(username) }.collectAsState(initial = 0)
    val best by remember(username) { viewModel.bestScore(username) }.collectAsState(initial = 0)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionPill(status, onClick = { onNavigate(Routes.CONNECT) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(greeting(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(username.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            // Main action
            NudgeCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate(Routes.THERAPY) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(20.dp)
            ) {
                val onPrimary = MaterialTheme.colorScheme.onPrimary
                Text("Today's therapy", style = MaterialTheme.typography.labelLarge, color = onPrimary.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text("Open and close exercise", style = MaterialTheme.typography.titleLarge, color = onPrimary)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.small, color = onPrimary) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (sessions == 1) "1 session this week" else "$sessions sessions this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onPrimary.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("More")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionRow(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    title = "Live signals",
                    subtitle = "See your muscle activity",
                    tint = NudgeTheme.colors.channels[1],
                    onClick = { onNavigate(Routes.LIVE) }
                )
                ActionRow(
                    icon = Icons.Default.SportsEsports,
                    title = "Play",
                    subtitle = if (best > 0) "Best score $best" else "Fly the bird with your hand",
                    tint = NudgeTheme.colors.channels[2],
                    onClick = { onNavigate(Routes.GAME) }
                )
                ActionRow(
                    icon = Icons.Default.Psychology,
                    title = "Train AI",
                    subtitle = when {
                        model == null -> "Teach Nudge your gestures"
                        sync == ModelSync.OnDevice -> modelSummary(model!!) + " · on wearable"
                        else -> modelSummary(model!!)
                    },
                    tint = NudgeTheme.colors.channels[0],
                    onClick = { onNavigate(Routes.TRAIN) }
                )
                ActionRow(
                    icon = Icons.Default.Groups,
                    title = "Care team",
                    subtitle = "Share sessions with your clinician",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = { onNavigate(Routes.CARE_TEAM) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun modelSummary(saved: SavedModel): String =
    saved.accuracy?.let { "${(it * 100).toInt()}% accurate" } ?: "Trained"

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
