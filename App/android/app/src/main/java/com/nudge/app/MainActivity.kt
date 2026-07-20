package com.nudge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nudge.app.data.UserRole
import com.nudge.app.ui.ConnectPhysicianScreen
import com.nudge.app.ui.DeviceSelectScreen
import com.nudge.app.ui.LoginScreen
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
    var userRole by remember { mutableStateOf<UserRole?>(null) }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onLoginSuccess = { role ->
                userRole = role
                navController.navigate("device_select")
            })
        }
        composable("device_select") {
            DeviceSelectScreen(onDeviceSelected = { deviceName ->
                // TODO: Store selected device info
                navController.navigate("summary")
            })
        }
        composable("summary") {
            SummaryScreen(onConnectWithPhysician = {
                navController.navigate("connect_physician")
            })
        }
        composable("connect_physician") {
            ConnectPhysicianScreen(onConnectionSuccess = {
                navController.popBackStack()
            })
        }
    }
}
