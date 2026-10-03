package com.nudge.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.nudge.app.ai.SavedModel
import com.nudge.app.bluetooth.ModelSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.bluetooth.DeviceStatus
import com.nudge.app.data.DataPoint
import com.nudge.app.ui.components.*
import com.nudge.app.ui.theme.NudgeTheme
import com.nudge.app.utils.CSVExporter

@Composable
fun TrainAiScreen(
    username: String,
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onTryIt: () -> Unit,
    onCalibrate: () -> Unit,
    viewModel: TrainingViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val saved by bluetooth.userModel.collectAsState()
    val sync by bluetooth.modelSync.collectAsState()
    val status by bluetooth.deviceStatus.collectAsState()
    val online = status == DeviceStatus.Connected || status == DeviceStatus.Demo
    val recording = phase == TrainingPhase.COUNTDOWN || phase == TrainingPhase.PREPARE ||
        phase == TrainingPhase.HOLD || phase == TrainingPhase.BUILDING
    val start = { viewModel.start(username, bluetooth.samples, bluetooth::setPromptedGesture) }

    DisposableEffect(Unit) {
        onDispose { bluetooth.setPromptedGesture(null) }
    }

    NudgeScreen(title = if (recording) "" else "Train AI", onBack = if (recording) null else onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (phase) {
                TrainingPhase.INTRO -> IntroView(
                    totalSeconds = viewModel.totalSeconds,
                    rounds = viewModel.rounds,
                    online = online,
                    saved = saved,
                    sync = sync,
                    onRetrySync = bluetooth::retryModelSync,
                    onConnect = onConnect,
                    onStart = start,
                    onCalibrate = onCalibrate
                )
                TrainingPhase.COUNTDOWN -> CountdownView(viewModel)
                TrainingPhase.PREPARE, TrainingPhase.HOLD -> RecordingView(viewModel, bluetooth)
                TrainingPhase.BUILDING -> BuildingView()
                TrainingPhase.RESULT -> ResultView(
                    viewModel = viewModel,
                    username = username,
                    sync = sync,
                    onRetrySync = bluetooth::retryModelSync,
                    onRecordAgain = start,
                    onTryIt = onTryIt,
                    onDone = onBack
                )
                TrainingPhase.FAILED -> FailedView(onRetry = start, onBack = { viewModel.reset() })
            }
        }
    }
}

@Composable
private fun IntroView(
    totalSeconds: Int,
    rounds: Int,
    online: Boolean,
    saved: SavedModel?,
    sync: ModelSync,
    onRetrySync: () -> Unit,
    onConnect: () -> Unit,
    onStart: () -> Unit,
    onCalibrate: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!online) {
                NotConnectedBanner(onConnect)
                Spacer(Modifier.height(20.dp))
            }
            if (saved != null) {
                SectionLabel("Your model")
                CurrentModelCard(saved, sync, onRetrySync)
                Spacer(Modifier.height(8.dp))
                Text(
                    "If gestures aren't recognised as well as before, start with a quick check. Retrain only if that doesn't help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IntroExplainer(totalSeconds, rounds)
            }
        }
        if (saved == null) {
            PrimaryButton("Start recording", icon = Icons.Default.PlayArrow, enabled = online, onClick = onStart)
        } else {
            PrimaryButton("Quick check (15 s)", enabled = online, onClick = onCalibrate)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Retrain from scratch", enabled = online, onClick = onStart)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IntroExplainer(totalSeconds: Int, rounds: Int) {
    Column {
        Text("Teach Nudge your hand", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "You'll make each gesture $rounds times while wearing the device. Nudge learns what your muscles look like for each one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        SectionLabel("Gestures")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "REST" to "Let your hand go loose",
                "OPEN" to "Spread your fingers wide",
                "CLOSE" to "Make a firm fist",
                "PINCH" to "Press thumb and index finger together"
            ).forEach { (g, hint) ->
                NudgeCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(10.dp), shape = MaterialTheme.shapes.extraLarge, color = gestureColor(g)) {}
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(gestureLabel(g), style = MaterialTheme.typography.titleMedium)
                            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Takes about ${totalSeconds / 60} min ${totalSeconds % 60} s. Sit comfortably with your arm supported.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CountdownView(viewModel: TrainingViewModel) {
    val count by viewModel.countdown.collectAsState()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Get ready", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(count, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "count") {
            Text("$it", fontSize = 120.sp, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecordingView(viewModel: TrainingViewModel, bluetooth: BluetoothViewModel) {
    val phase by viewModel.phase.collectAsState()
    val gesture by viewModel.gesture.collectAsState()
    val round by viewModel.round.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val remaining by viewModel.holdRemaining.collectAsState()
    val channels by bluetooth.channels.collectAsState()
    val holding = phase == TrainingPhase.HOLD
    val color = gestureColor(gesture)

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressCaption(progress, "Round $round of ${viewModel.rounds}")
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
                Text(
                    if (holding) "Hold" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(gestureLabel(gesture), style = MaterialTheme.typography.headlineMedium, color = color, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (holding) "Keep it steady" else "Switch to this gesture now",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
private fun BuildingView() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 5.dp)
        Spacer(Modifier.height(24.dp))
        Text("Learning your gestures", style = MaterialTheme.typography.titleLarge)
        Text(
            "This only takes a moment.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultView(
    viewModel: TrainingViewModel,
    username: String,
    sync: ModelSync,
    onRetrySync: () -> Unit,
    onRecordAgain: () -> Unit,
    onTryIt: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val result by viewModel.result.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val r = result ?: return
    val accuracy = r.accuracy

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (accuracy != null) "${(accuracy * 100).roundToInt()}%" else "Ready",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 64.sp),
                color = if (saved) MaterialTheme.colorScheme.primary else NudgeTheme.colors.warning
            )
            Text(
                if (accuracy != null) "estimated accuracy" else "model trained",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            NudgeCard(Modifier.fillMaxWidth()) {
                viewModel.gestures.forEachIndexed { i, g ->
                    val value = r.perGesture[g] ?: 0f
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(gestureLabel(g), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { value },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = gestureColor(g),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (saved) {
                SyncCard(sync, onRetrySync)
            } else {
                NudgeCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.WarningAmber, null, tint = NudgeTheme.colors.warning)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Accuracy is low", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Check the sensors sit firmly on the forearm, then record again. Your previous model is still in use.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = viewModel::keepModel, contentPadding = PaddingValues(0.dp)) {
                                Text("Use this model anyway")
                            }
                        }
                    }
                }
            }

            TextButton(onClick = {
                val points = viewModel.samples.flatMapIndexed { i, s ->
                    s.values.mapIndexed { sensor, v ->
                        DataPoint(username = username, timestamp = i * 20L, value = v, type = "EMG", sensorId = sensor, label = s.label)
                    }
                }
                CSVExporter.exportTrainingData(context, username, points)
            }) {
                Icon(Icons.Default.IosShare, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export recording (CSV)")
            }
        }
        if (saved) {
            PrimaryButton("Try it", onClick = onTryIt)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        } else {
            PrimaryButton("Record again", onClick = onRecordAgain)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Keep my previous model") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FailedView(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.weight(1f))
        EmptyState(
            icon = Icons.Default.WarningAmber,
            title = "Not enough data",
            message = "Some gestures didn't get recorded. Make sure the wearable stays connected for the whole recording."
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Record again", onClick = onRetry)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(8.dp))
    }
}

/** Summary of the user's saved model and whether the wearable has it. */
@Composable
fun CurrentModelCard(saved: SavedModel, sync: ModelSync, onRetrySync: () -> Unit) {
    NudgeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Psychology)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    saved.accuracy?.let { "${(it * 100).roundToInt()}% accurate" } ?: "Trained",
                    style = MaterialTheme.typography.titleMedium
                )
                val format = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                Text(
                    "Trained ${format.format(Date(saved.trainedAt))}" +
                        (saved.calibratedAt?.let { " · checked ${format.format(Date(it))}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SyncLine(sync, onRetrySync)
    }
}

@Composable
private fun SyncCard(sync: ModelSync, onRetrySync: () -> Unit) {
    NudgeCard(Modifier.fillMaxWidth()) { SyncLine(sync, onRetrySync) }
}

@Composable
private fun SyncLine(sync: ModelSync, onRetrySync: () -> Unit) {
    val colors = NudgeTheme.colors
    val (text, color) = when (sync) {
        ModelSync.OnDevice -> "On your wearable" to colors.success
        ModelSync.Sending -> "Sending to your wearable…" to colors.warning
        ModelSync.NotConnected -> "Saved. It'll be sent when your wearable connects." to colors.subtle
        ModelSync.Failed -> "Couldn't send to the wearable" to colors.danger
        ModelSync.FirmwareTooOld -> "Your wearable needs a firmware update to use it" to colors.danger
        ModelSync.NoModel -> "Not trained yet" to colors.subtle
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sync == ModelSync.Sending) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
        } else {
            Surface(Modifier.size(10.dp), shape = CircleShape, color = color) {}
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (sync == ModelSync.Failed) TextButton(onClick = onRetrySync) { Text("Retry") }
    }
}
