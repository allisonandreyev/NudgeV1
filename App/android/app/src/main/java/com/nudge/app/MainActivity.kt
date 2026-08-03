package com.nudge.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.data.UserRole
import com.nudge.app.ui.ConnectPhysicianScreen
import com.nudge.app.ui.DeviceSelectScreen
import com.nudge.app.ui.ForgotPasswordScreen
import com.nudge.app.ui.LoginScreen
import com.nudge.app.ui.MinigameScreen
import com.nudge.app.ui.PatientDetailScreen
import com.nudge.app.ui.PhysicianDashboardScreen
import com.nudge.app.ui.SignUpScreen
import com.nudge.app.ui.SummaryScreen
import com.nudge.app.ui.TherapySessionScreen
import com.nudge.app.ui.WelcomeScreen
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
    var currentUsername by remember { mutableStateOf<String?>(null) }
    val bluetoothViewModel: BluetoothViewModel = hiltViewModel()

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* Permissions handled */ }

    LaunchedEffect(currentUsername) {
        launcher.launch(permissionsToRequest)
        currentUsername?.let {
            bluetoothViewModel.setCurrentUser(it)
        }
    }

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToSignUp = { navController.navigate("signup") },
                onNavigateToTestData = {
                    currentUsername = "test_user"
                    userRole = UserRole.PATIENT
                    navController.navigate("device_select")
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { username, role ->
                    currentUsername = username
                    userRole = role
                    if (role == UserRole.PHYSICIAN) {
                        navController.navigate("summary")
                    } else {
                        navController.navigate("device_select")
                    }
                },
                onForgotPassword = { navController.navigate("forgot_password") }
            )
        }
        composable("signup") {
            SignUpScreen(onSignUpSuccess = { username ->
                currentUsername = username
                navController.navigate("login")
            })
        }
        composable("forgot_password") {
            ForgotPasswordScreen(onResetSuccess = { navController.navigate("login") })
        }
        composable("device_select") {
            DeviceSelectScreen(
                viewModel = bluetoothViewModel,
                onDeviceSelected = { device ->
                    bluetoothViewModel.connectToDevice(device)
                    navController.navigate("summary")
                },
                onSkipConnection = {
                    navController.navigate("summary")
                }
            )
        }
        composable("summary") {
            SummaryScreen(
                viewModel = bluetoothViewModel,
                userRole = userRole ?: UserRole.PATIENT,
                username = currentUsername ?: "guest",
                onConnectWithPhysician = {
                    navController.navigate("connect_physician")
                },
                onStartTherapy = {
                    navController.navigate("therapy")
                },
                onStartMinigame = {
                    navController.navigate("minigame")
                },
                onViewPhysicianDashboard = {
                    navController.navigate("physician_dashboard")
                }
            )
        }
        composable("therapy") {
            TherapySessionScreen(onSessionEnd = { navController.popBackStack() })
        }
        composable("minigame") {
            MinigameScreen(
                username = currentUsername ?: "guest",
                onGameEnd = { navController.popBackStack() }
            )
        }
        composable("connect_physician") {
            ConnectPhysicianScreen(
                username = currentUsername ?: "guest",
                onConnectionSuccess = {
                    navController.popBackStack()
                }
            )
        }
        composable("physician_dashboard") {
            PhysicianDashboardScreen(
                physicianEmail = currentUsername ?: "",
                onNavigateToPatientDetail = { patientUsername ->
                    navController.navigate("patient_detail/$patientUsername")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("patient_detail/{patientUsername}") { backStackEntry ->
            val patientUsername = backStackEntry.arguments?.getString("patientUsername") ?: ""
            PatientDetailScreen(
                patientUsername = patientUsername,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
