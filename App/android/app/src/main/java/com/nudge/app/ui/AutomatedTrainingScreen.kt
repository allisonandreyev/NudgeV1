package com.nudge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.ui.theme.MedicalGradient
import com.nudge.app.utils.CSVExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomatedTrainingScreen(
    username: String,
    onBack: () -> Unit,
    trainingViewModel: TrainingViewModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    val uiState by trainingViewModel.uiState.collectAsState()
    val currentGesture by trainingViewModel.currentGesture.collectAsState()
    val currentRepetition by trainingViewModel.currentRepetition.collectAsState()
    val timerSeconds by trainingViewModel.timerSeconds.collectAsState()
    
    val emgD0 by bluetoothViewModel.emgDataD0.collectAsState()
    val emgD1 by bluetoothViewModel.emgDataD1.collectAsState()
    val emgD2 by bluetoothViewModel.emgDataD2.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MedicalGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                title = { Text("Model Training", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            when (uiState) {
                TrainingState.IDLE -> {
                    TrainingIdleView(
                        onStart = { trainingViewModel.startTraining(username) { bluetoothViewModel.setActiveLabel(it) } },
                        onClear = { trainingViewModel.clearData(username) }
                    )
                }
                TrainingState.COUNTDOWN -> {
                    TrainingCountdownView(timerSeconds.toInt())
                }
                TrainingState.PREPARING_GESTURE -> {
                    TrainingPreparationView(
                        gesture = currentGesture,
                        rep = currentRepetition
                    )
                }
                TrainingState.RECORDING -> {
                    TrainingRecordingView(
                        gesture = currentGesture,
                        rep = currentRepetition,
                        seconds = timerSeconds,
                        emgDataD0 = emgD0,
                        emgDataD1 = emgD1,
                        emgDataD2 = emgD2,
                        onStop = { trainingViewModel.stopTraining { bluetoothViewModel.setActiveLabel(it) } },
                        onSaveEarly = {
                            scope.launch {
                                val data = trainingViewModel.getLabeledData(username)
                                CSVExporter.exportTrainingData(context, username, data)
                            }
                        }
                    )
                }
                TrainingState.FINISHED -> {
                    TrainingFinishedView(
                        onExport = {
                            scope.launch {
                                val labeledData = trainingViewModel.getLabeledData(username)
                                CSVExporter.exportTrainingData(context, username, labeledData)
                            }
                        },
                        onRestart = { trainingViewModel.startTraining(username) { bluetoothViewModel.setActiveLabel(it) } },
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnScope.TrainingIdleView(onStart: () -> Unit, onClear: () -> Unit) {
    Icon(
        imageVector = Icons.Default.Psychology,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    Text(
        "Automated Calibration",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    
    Text(
        "The app will guide you through 10 cycles of Rest, Open, Pinch, and Close gestures. 1.25s is given to switch, then 4s is recorded.",
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 16.dp)
    )

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text("Start Full Calibration (10 Sets)", fontWeight = FontWeight.Bold)
    }
    
    TextButton(onClick = onClear) {
        Text("Clear Previous Training Data", color = Color.Red.copy(alpha = 0.7f))
    }
}

@Composable
fun ColumnScope.TrainingCountdownView(seconds: Int) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GET READY", fontSize = 24.sp, color = Color.White.copy(alpha = 0.6f))
            Text(
                seconds.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ColumnScope.TrainingPreparationView(gesture: String, rep: Int) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "SWITCH TO",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            gesture,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Stabilizing signals...", color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun ColumnScope.TrainingRecordingView(
    gesture: String, 
    rep: Int, 
    seconds: Float, 
    emgDataD0: List<Float>,
    emgDataD1: List<Float>,
    emgDataD2: List<Float>,
    onStop: () -> Unit,
    onSaveEarly: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "CYCLE $rep / 10",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.5f)
        )
        
        CircularProgressIndicator(
            progress = { seconds / 4f },
            modifier = Modifier.size(40.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        gesture,
        fontSize = 48.sp,
        fontWeight = FontWeight.Black,
        color = when(gesture) {
            "REST" -> Color.Gray
            "OPEN" -> Color(0xFF00E676)
            "PINCH" -> Color(0xFFFF9100)
            "CLOSE" -> Color(0xFFFF5252)
            else -> Color.White
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SensorGraphCard(label = "Channel D0", data = emgDataD0, color = MaterialTheme.colorScheme.primary)
        SensorGraphCard(label = "Channel D1", data = emgDataD1, color = Color(0xFF00E676))
        SensorGraphCard(label = "Channel D2", data = emgDataD2, color = Color(0xFFFF9100))
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f))
    ) {
        Text("Stop Calibration")
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onSaveEarly,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save Current Progress Early")
    }
}

@Composable
fun ColumnScope.TrainingFinishedView(onExport: () -> Unit, onRestart: () -> Unit, onBack: () -> Unit) {
    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(120.dp),
        tint = Color(0xFF00E676)
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text("Calibration Complete!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Text("10 sets of data have been recorded and labeled.", color = Color.White.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Default.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Export Dataset (CSV)", fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text("Restart Calibration")
    }
    
    TextButton(onClick = onBack) {
        Text("Return to Dashboard", color = Color.White.copy(alpha = 0.7f))
    }
}
