package com.nudge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nudge.app.ui.ConnectScreen
import com.nudge.app.ui.SummaryScreen
import com.nudge.app.ui.theme.NudgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NudgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NudgeApp()
                }
            }
        }
    }
}

@Composable
fun NudgeApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "connect") {
        composable("connect") {
            ConnectScreen(onNavigateToSummary = {
                navController.navigate("summary")
            })
        }
        composable("summary") {
            SummaryScreen()
        }
    }
}
