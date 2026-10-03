package com.nudge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.ui.components.PrimaryButton
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class GameState { START, PLAYING, GAME_OVER }

private data class Pipe(val x: Float, val gapY: Float, val scored: Boolean = false)

private const val PIPE_WIDTH = 150f
private const val GAP_HEIGHT = 470f
private const val BIRD_X = 200f
private const val BIRD_RADIUS = 30f
private const val GRAVITY = 0.34f
private const val JUMP = -9.5f
private const val PIPE_SPEED = 3.5f

private val Sky = Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFFE0F2FE)))
private val PipeColor = Color(0xFF22C55E)
private val PipeEdge = Color(0xFF15803D)
private val Bird = Color(0xFFFACC15)

@Composable
fun MinigameScreen(
    username: String,
    bluetooth: BluetoothViewModel,
    onBack: () -> Unit,
    viewModel: MinigameViewModel = hiltViewModel()
) {
    var state by remember { mutableStateOf(GameState.START) }
    var birdY by remember { mutableFloatStateOf(500f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var pipes by remember { mutableStateOf(listOf<Pipe>()) }
    var score by remember { mutableIntStateOf(0) }
    var width by remember { mutableFloatStateOf(1080f) }
    var height by remember { mutableFloatStateOf(1920f) }
    val highScore by viewModel.highScore.collectAsState()
    val gesture by bluetooth.lastGesture.collectAsState()

    LaunchedEffect(username) { viewModel.setUsername(username) }

    fun start() {
        birdY = height / 3f
        velocity = 0f
        pipes = emptyList()
        score = 0
        state = GameState.PLAYING
    }

    fun flap() {
        when (state) {
            GameState.START, GameState.GAME_OVER -> start()
            GameState.PLAYING -> velocity = JUMP
        }
    }

    // Opening the hand flaps, same as a tap
    LaunchedEffect(gesture) {
        if (state == GameState.PLAYING && gesture == "OPEN") velocity = JUMP
    }

    LaunchedEffect(state) {
        while (state == GameState.PLAYING) {
            velocity += GRAVITY
            birdY += velocity

            pipes = pipes.map { it.copy(x = it.x - PIPE_SPEED) }.filter { it.x + PIPE_WIDTH > 0 }
            if (pipes.isEmpty() || pipes.last().x < width * 0.5f) {
                pipes = pipes + Pipe(x = width, gapY = Random.nextFloat() * height * 0.5f + height * 0.25f)
            }
            pipes = pipes.map {
                if (!it.scored && it.x + PIPE_WIDTH < BIRD_X) { score++; it.copy(scored = true) } else it
            }

            val hit = birdY < 0 || birdY > height || pipes.any { p ->
                val inX = BIRD_X + BIRD_RADIUS > p.x && BIRD_X - BIRD_RADIUS < p.x + PIPE_WIDTH
                val inGap = birdY - BIRD_RADIUS > p.gapY - GAP_HEIGHT / 2 && birdY + BIRD_RADIUS < p.gapY + GAP_HEIGHT / 2
                inX && !inGap
            }
            if (hit) {
                state = GameState.GAME_OVER
                viewModel.updateHighScore(score)
            }
            delay(16)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sky)
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
                if (state == GameState.START) birdY = height / 3f
            }
            .pointerInput(Unit) { detectTapGestures { flap() } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            pipes.forEach { p ->
                val top = p.gapY - GAP_HEIGHT / 2
                val bottom = p.gapY + GAP_HEIGHT / 2
                drawRoundRect(PipeColor, Offset(p.x, -40f), Size(PIPE_WIDTH, top + 40f), CornerRadius(18f))
                drawRoundRect(PipeColor, Offset(p.x, bottom), Size(PIPE_WIDTH, size.height - bottom + 40f), CornerRadius(18f))
                drawRoundRect(PipeEdge, Offset(p.x - 8f, top - 36f), Size(PIPE_WIDTH + 16f, 36f), CornerRadius(10f))
                drawRoundRect(PipeEdge, Offset(p.x - 8f, bottom), Size(PIPE_WIDTH + 16f, 36f), CornerRadius(10f))
            }
            drawCircle(Bird, BIRD_RADIUS, Offset(BIRD_X, birdY))
            drawCircle(Color.Black, 5f, Offset(BIRD_X + 12f, birdY - 8f))
        }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                Text("Best $highScore", style = MaterialTheme.typography.labelLarge, color = Color(0xFF0C4A6E))
            }
            Text(
                "$score",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.weight(1f))
            when (state) {
                GameState.START -> HintCard(
                    title = "Tap or open your hand",
                    message = "Each flap lifts the bird. Fly through the gaps."
                )
                GameState.GAME_OVER -> Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(24.dp).width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Game over", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (score >= highScore && score > 0) "New best: $score" else "Score $score  ·  Best $highScore",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        PrimaryButton("Play again", onClick = ::start)
                        TextButton(onClick = onBack) { Text("Back to home") }
                    }
                }
                GameState.PLAYING -> {}
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HintCard(title: String, message: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Column(Modifier.padding(24.dp).width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
