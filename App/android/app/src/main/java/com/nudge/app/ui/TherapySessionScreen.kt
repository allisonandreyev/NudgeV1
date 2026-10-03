package com.nudge.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.data.RestPosition
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme

@Composable
fun TherapySessionScreen(
    username: String,
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onCalibrate: () -> Unit,
    viewModel: TherapyViewModel = hiltViewModel()
) {
    val saved by bluetooth.userModel.collectAsState()
    // Suggest a quick check once per day of use, since the wearable is usually re-fitted daily
    val needsCheck = saved?.let { !isToday(it.calibratedAt ?: it.trainedAt) } == true
    val state by viewModel.uiState.collectAsState()
    val sessionId by viewModel.currentSessionId.collectAsState()
    val status by bluetooth.deviceStatus.collectAsState()
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo
    val active = state == TherapyState.ACTIVE_REST || state == TherapyState.ACTIVE_CONTRACT

    // Tag incoming readings with this session while it runs
    LaunchedEffect(sessionId, state) {
        bluetooth.setActiveSession(if (active) sessionId else null)
    }
    // AI assists the hand only while the session is running
    LaunchedEffect(active) {
        bluetooth.sendCommand(if (active) "ai_start" else "ai_stop")
    }
    DisposableEffect(Unit) {
        onDispose {
            bluetooth.setActiveSession(null)
            bluetooth.sendCommand("ai_stop")
        }
    }

    NudgeScreen(title = if (state == TherapyState.FINISHED) "" else "Therapy", onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TherapyState.SETUP -> SetupView(
                    online = online,
                    needsCheck = needsCheck,
                    onCalibrate = onCalibrate,
                    onConnect = onConnect,
                    onStart = { position, reps -> viewModel.startSession(username, position, reps) }
                )
                TherapyState.ACTIVE_REST, TherapyState.ACTIVE_CONTRACT -> ActiveView(viewModel, bluetooth)
                TherapyState.FINISHED -> FinishedView(viewModel, onDone = onBack)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupView(
    online: Boolean,
    needsCheck: Boolean,
    onCalibrate: () -> Unit,
    onConnect: () -> Unit,
    onStart: (RestPosition, Int) -> Unit
) {
    var position by remember { mutableStateOf(RestPosition.OPENED) }
    var reps by remember { mutableIntStateOf(10) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!online) {
                NotConnectedBanner(onConnect)
                Spacer(Modifier.height(20.dp))
            } else if (needsCheck) {
                ActionRow(
                    icon = Icons.Default.Tune,
                    title = "Quick check first",
                    subtitle = "15 seconds to tune the AI to today's fit",
                    onClick = onCalibrate
                )
                Spacer(Modifier.height(20.dp))
            }
            Text("How does your hand rest?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick the position your hand naturally falls into. You'll practise moving away from it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(Icons.Default.BackHand, "Open", position == RestPosition.OPENED, Modifier.weight(1f)) {
                    position = RestPosition.OPENED
                }
                ChoiceCard(Icons.Default.FrontHand, "Closed", position == RestPosition.CLOSED, Modifier.weight(1f)) {
                    position = RestPosition.CLOSED
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Repetitions", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(5, 10, 20)
                options.forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = reps == n,
                        onClick = { reps = n },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)
                    ) { Text("$n") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "About ${reps * 10 / 60} min ${reps * 10 % 60} s. Each rep is 5 s relaxed, then 5 s moving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PrimaryButton("Start", icon = Icons.Default.PlayArrow, enabled = online, onClick = { onStart(position, reps) })
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceCard(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border)
    ) {
        Column(Modifier.padding(vertical = 24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ActiveView(viewModel: TherapyViewModel, bluetooth: BluetoothViewModel) {
    val state by viewModel.uiState.collectAsState()
    val remaining by viewModel.phaseRemaining.collectAsState()
    val rep by viewModel.rep.collectAsState()
    val total by viewModel.totalReps.collectAsState()
    val channels by bluetooth.channels.collectAsState()
    val restPosition by viewModel.restPosition.collectAsState()
    val moving = state == TherapyState.ACTIVE_CONTRACT
    val ringColor by animateColorAsState(
        if (moving) MaterialTheme.colorScheme.primary else NudgeTheme.colors.gestureRest,
        label = "ring"
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressCaption(
            progress = (rep - 1 + if (moving) 0.5f else 0f) / total,
            caption = "Rep $rep of $total"
        )
        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            CircularProgressIndicator(
                progress = { remaining },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = ringColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            AnimatedContent(moving, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "instruction") { isMoving ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isMoving) "Move" else "Relax",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (isMoving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${(remaining * TherapyViewModel.PHASE_MS / 1000f).toInt() + 1} s",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                !moving -> "Let your hand rest"
                restPosition == RestPosition.OPENED -> "Close your fist"
                else -> "Open your hand"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))
        NudgeCard(Modifier.fillMaxWidth().height(120.dp)) {
            EmgChart(channels, Modifier.fillMaxSize(), showLegend = false)
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("End session", onClick = { viewModel.stopSession() })
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FinishedView(viewModel: TherapyViewModel, onDone: () -> Unit) {
    val rep by viewModel.rep.collectAsState()
    val seconds by viewModel.durationSeconds.collectAsState()
    val shared by viewModel.shared.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.CheckCircle, null, tint = NudgeTheme.colors.success, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Nice work", style = MaterialTheme.typography.headlineMedium)
        Text("Session saved on this phone.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("$rep", "Reps", Modifier.weight(1f))
            StatTile("${seconds / 60}:${"%02d".format(seconds % 60)}", "Duration", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        NudgeCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Share with care team", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your clinician can review this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = shared, onCheckedChange = viewModel::setShared)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Done", onClick = onDone)
        Spacer(Modifier.height(16.dp))
    }
}

private fun isToday(time: Long): Boolean {
    val then = java.util.Calendar.getInstance().apply { timeInMillis = time }
    val now = java.util.Calendar.getInstance()
    return then.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        then.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
}
