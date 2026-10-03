package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.ai.SensorState
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.bluetooth.ModelSync
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme
import kotlin.math.roundToInt

private fun stepInstruction(gesture: String) = when (gesture) {
    "REST" -> "Let your hand go loose"
    "CLOSE" -> "Squeeze a firm fist"
    "OPEN" -> "Spread your fingers wide"
    else -> ""
}

@Composable
fun CalibrateScreen(
    username: String,
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onRetrain: () -> Unit,
    viewModel: CalibrateViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val saved by bluetooth.userModel.collectAsState()
    val status by bluetooth.deviceStatus.collectAsState()
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo
    val running = phase == CalibratePhase.PREPARE || phase == CalibratePhase.HOLD

    DisposableEffect(Unit) { onDispose { bluetooth.setPromptedGesture(null) } }

    NudgeScreen(title = if (running) "" else "Quick check", onBack = if (running) null else onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (phase) {
                CalibratePhase.INTRO -> {
                    val model = saved
                    if (model?.reference == null) {
                        // Nothing to compare against: no model, or one trained before recalibration existed
                        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                            Spacer(Modifier.weight(1f))
                            EmptyState(
                                icon = Icons.Default.WarningAmber,
                                title = if (model == null) "Train the AI first" else "Retrain once to enable quick checks",
                                message = "The quick check compares today's signals with the ones from training."
                            )
                            Spacer(Modifier.weight(1f))
                            PrimaryButton("Train AI", onClick = onRetrain)
                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        IntroView(viewModel.totalSeconds, online, onConnect) {
                            viewModel.start(username, model, bluetooth.samples, bluetooth::setPromptedGesture)
                        }
                    }
                }
                CalibratePhase.PREPARE, CalibratePhase.HOLD -> RunningView(viewModel, bluetooth)
                CalibratePhase.RESULT -> ResultView(viewModel, bluetooth, onDone = onBack, onRetrain = onRetrain)
                CalibratePhase.FAILED -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.weight(1f))
                    EmptyState(
                        icon = Icons.Default.WarningAmber,
                        title = "Not enough signal",
                        message = "Make sure the wearable stays connected for the whole check."
                    )
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Try again", onClick = { viewModel.cancel(bluetooth::setPromptedGesture) })
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun IntroView(seconds: Int, online: Boolean, onConnect: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!online) {
                NotConnectedBanner(onConnect)
                Spacer(Modifier.height(20.dp))
            }
            Text("Tune Nudge to today's fit", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Each time you put the wearable on, the sensors sit a little differently. This $seconds-second check adjusts for that, so you don't need to retrain.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            SectionLabel("You'll do")
            NudgeCard(Modifier.fillMaxWidth()) {
                listOf("REST" to "4 s", "CLOSE" to "3 s", "OPEN" to "3 s").forEachIndexed { i, (g, time) ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(10.dp), shape = CircleShape, color = gestureColor(g)) {}
                        Spacer(Modifier.width(14.dp))
                        Text(gestureLabel(g), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        PrimaryButton("Start", icon = Icons.Default.PlayArrow, enabled = online, onClick = onStart)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RunningView(viewModel: CalibrateViewModel, bluetooth: BluetoothViewModel) {
    val phase by viewModel.phase.collectAsState()
    val index by viewModel.step.collectAsState()
    val remaining by viewModel.holdRemaining.collectAsState()
    val channels by bluetooth.channels.collectAsState()
    val step = viewModel.steps[index]
    val holding = phase == CalibratePhase.HOLD
    val color = gestureColor(step.gesture)

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressCaption((index + if (holding) 1f - remaining else 0f) / viewModel.steps.size, "Step ${index + 1} of ${viewModel.steps.size}")
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (holding) remaining else 1f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = if (holding) color else MaterialTheme.colorScheme.surfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (holding) "Hold" else "Next", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(gestureLabel(step.gesture), style = MaterialTheme.typography.headlineMedium, color = color, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stepInstruction(step.gesture), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        NudgeCard(Modifier.fillMaxWidth().height(110.dp)) {
            EmgChart(channels, Modifier.fillMaxSize(), showLegend = false)
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Cancel", onClick = { viewModel.cancel(bluetooth::setPromptedGesture) })
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ResultView(viewModel: CalibrateViewModel, bluetooth: BluetoothViewModel, onDone: () -> Unit, onRetrain: () -> Unit) {
    val result by viewModel.result.collectAsState()
    val applied by viewModel.applied.collectAsState()
    val sync by bluetooth.modelSync.collectAsState()
    val r = result ?: return
    val colors = NudgeTheme.colors

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Icon(
                if (r.allRecognised) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                null,
                tint = if (r.allRecognised) colors.success else colors.warning,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(if (r.allRecognised) "You're all set" else "Some gestures weren't recognised", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                when {
                    !r.allRecognised -> "Adjusting wasn't enough this time. A sensor may have moved onto a different muscle."
                    sync == ModelSync.OnDevice -> "Nudge is tuned to today's fit and the wearable has it."
                    else -> "Nudge is tuned to today's fit. Sending it to the wearable…"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            SectionLabel("Gestures", Modifier.align(Alignment.Start))
            NudgeCard(Modifier.fillMaxWidth()) {
                listOf("REST", "CLOSE", "OPEN").forEachIndexed { i, g ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    val ok = r.recognised[g] == true
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(gestureLabel(g), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(
                            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = if (ok) "Recognised" else "Not recognised",
                            tint = if (ok) colors.success else colors.danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Sensors compared with training", Modifier.align(Alignment.Start))
            NudgeCard(Modifier.fillMaxWidth()) {
                r.sensors.forEachIndexed { i, state ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    val pct = (r.strength[i] * 100).roundToInt()
                    val (text, color) = when (state) {
                        SensorState.Normal -> "About the same" to colors.success
                        SensorState.Weaker -> "Weaker ($pct%), adjusted" to colors.warning
                        SensorState.Stronger -> "Stronger ($pct%), adjusted" to colors.warning
                        SensorState.NotResponding -> "Barely responding. Check it touches the skin" to colors.danger
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(10.dp), shape = CircleShape, color = NudgeTheme.colors.channels[i]) {}
                        Spacer(Modifier.width(12.dp))
                        Text("Sensor ${i + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(84.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (applied) {
            PrimaryButton("Done", onClick = onDone)
        } else {
            PrimaryButton("Retrain from scratch", onClick = onRetrain)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::apply, modifier = Modifier.fillMaxWidth()) { Text("Use the adjustment anyway") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
