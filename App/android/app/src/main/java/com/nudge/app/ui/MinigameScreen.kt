package com.nudge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class GameState {
    START, PLAYING, GAME_OVER
}

data class Pipe(
    val x: Float,
    val gapY: Float,
    val width: Float = 150f,
    val gapHeight: Float = 450f // Slightly larger gap for easier play
)

@Composable
fun MinigameScreen(
    username: String,
    onGameEnd: () -> Unit,
    viewModel: MinigameViewModel = hiltViewModel(),
    bluetoothViewModel: com.nudge.app.bluetooth.BluetoothViewModel = hiltViewModel()
) {
    var gameState by remember { mutableStateOf(GameState.START) }
    var birdY by remember { mutableStateOf(500f) }
    var birdVelocity by remember { mutableStateOf(0f) }
    var pipes by remember { mutableStateOf(listOf<Pipe>()) }
    var score by remember { mutableStateOf(0) }
    val highScore by viewModel.highScore.collectAsState()

    val emgD0 by bluetoothViewModel.emgDataD0.collectAsState()
    val lastGesture by bluetoothViewModel.lastGesture.collectAsState()

    var screenWidth by remember { mutableStateOf(1080f) }
    var screenHeight by remember { mutableStateOf(1920f) }

    // EMG Control Logic
    LaunchedEffect(lastGesture) {
        if (gameState == GameState.PLAYING && lastGesture == "OPEN") {
            birdVelocity = -9.5f // Trigger jump from flex
        }
    }

    // Physics Constants - Tuned to be slower and use more screen
    val gravity = 0.34f      // 75% of previous 0.45f
    val jumpImpulse = -9.5f  // Adjusted to match lighter gravity
    val pipeSpeed = 3.5f

    LaunchedEffect(username) {
        viewModel.setUsername(username)
    }

    fun resetGame() {
        birdY = screenHeight / 3f
        birdVelocity = 0f
        pipes = emptyList()
        score = 0
        gameState = GameState.PLAYING
    }

    // Game Loop
    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            while (gameState == GameState.PLAYING) {
                birdVelocity += gravity
                birdY += birdVelocity

                // Update Pipes
                pipes = pipes.map { it.copy(x = it.x - pipeSpeed) }
                    .filter { it.x + it.width > 0 }

                // Spawn Pipes - Dynamic based on screen width
                if (pipes.isEmpty() || pipes.last().x < screenWidth * 0.5f) {
                    pipes = pipes + Pipe(
                        x = screenWidth,
                        gapY = Random.nextFloat() * (screenHeight * 0.5f) + (screenHeight * 0.25f)
                    )
                }

                // Scoring
                pipes.forEach { pipe ->
                    if (gameState == GameState.PLAYING && pipe.x < 200f && pipe.x + pipeSpeed >= 200f) {
                        score++
                    }
                }

                // Collision Detection - Uses full screen height
                val birdRect = Offset(200f, birdY)
                val birdSize = 30f

                if (birdY < 0 || birdY > screenHeight) {
                    gameState = GameState.GAME_OVER
                }

                pipes.forEach { pipe ->
                    val hitPipeX = birdRect.x + birdSize > pipe.x && birdRect.x - birdSize < pipe.x + pipe.width
                    val hitUpperPipe = birdRect.y - birdSize < pipe.gapY - pipe.gapHeight / 2
                    val hitLowerPipe = birdRect.y + birdSize > pipe.gapY + pipe.gapHeight / 2
                    
                    if (hitPipeX && (hitUpperPipe || hitLowerPipe)) {
                        gameState = GameState.GAME_OVER
                    }
                }

                if (gameState == GameState.GAME_OVER) {
                    viewModel.updateHighScore(score)
                }

                delay(16) // ~60 FPS
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF70C5CE))
            .onSizeChanged { size ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
                if (gameState == GameState.START) {
                    birdY = screenHeight / 3f
                }
            }
            .clickable {
                if (gameState == GameState.START) {
                    gameState = GameState.PLAYING
                } else if (gameState == GameState.PLAYING) {
                    birdVelocity = jumpImpulse
                } else if (gameState == GameState.GAME_OVER) {
                    resetGame()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw Pipes
            pipes.forEach { pipe ->
                // Upper pipe
                drawRect(
                    color = Color(0xFF74BF2E),
                    topLeft = Offset(pipe.x, 0f),
                    size = Size(pipe.width, pipe.gapY - pipe.gapHeight / 2)
                )
                // Lower pipe
                drawRect(
                    color = Color(0xFF74BF2E),
                    topLeft = Offset(pipe.x, pipe.gapY + pipe.gapHeight / 2),
                    size = Size(pipe.width, size.height - (pipe.gapY + pipe.gapHeight / 2))
                )
            }

            // Draw Bird
            drawCircle(
                color = Color.Yellow,
                radius = 30f,
                center = Offset(200f, birdY)
            )
        }

        // UI Overlays
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Score: $score",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "High Score: $highScore",
                fontSize = 18.sp,
                color = Color.White
            )

            if (gameState == GameState.START) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "TAP TO START",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            if (gameState == GameState.GAME_OVER) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("GAME OVER", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Your Score: $score", color = Color.White.copy(alpha = 0.8f))
                            Text("Best: $highScore", color = Color.White.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Try Again", color = MaterialTheme.colorScheme.onPrimary)
                            }
                            TextButton(onClick = onGameEnd) {
                                Text("Back to Dashboard", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
