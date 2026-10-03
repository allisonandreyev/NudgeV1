package com.nudge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nudge.app.bluetooth.BluetoothViewModel
import com.nudge.app.data.UserRole
import com.nudge.app.ui.*
import com.nudge.app.ui.theme.NudgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NudgeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NudgeApp()
                }
            }
        }
    }
}

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val CONNECT = "connect"
    const val LIVE = "live"
    const val THERAPY = "therapy"
    const val GAME = "game"
    const val TRAIN = "train"
    const val CALIBRATE = "calibrate"
    const val HAND_CONTROLS = "hand_controls"
    const val CARE_TEAM = "care_team"
    const val PHYSICIAN_HOME = "physician_home"
    const val PATIENT = "patient/{username}"
    const val SESSION = "session/{sessionId}"
    const val SETTINGS = "settings"

    fun patient(username: String) = "patient/$username"
    fun session(id: Long) = "session/$id"
}

/** Demo mode signs in as this user so nothing touches real accounts. */
const val DEMO_USERNAME = "demo"

@Composable
fun NudgeApp() {
    val navController = rememberNavController()
    var username by rememberSaveable { mutableStateOf<String?>(null) }
    var role by rememberSaveable { mutableStateOf<UserRole?>(null) }
    val bluetooth: BluetoothViewModel = hiltViewModel()

    LaunchedEffect(username) { bluetooth.setCurrentUser(username) }

    fun signIn(name: String, userRole: UserRole) {
        username = name
        role = userRole
        val home = if (userRole == UserRole.PHYSICIAN) Routes.PHYSICIAN_HOME else Routes.HOME
        navController.navigate(home) { popUpTo(0) }
    }

    fun signOut() {
        bluetooth.disconnect()
        username = null
        role = null
        navController.navigate(Routes.WELCOME) { popUpTo(0) }
    }

    val user = username ?: ""
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onSignIn = { navController.navigate(Routes.LOGIN) },
                onCreateAccount = { navController.navigate(Routes.SIGNUP) },
                onTryDemo = {
                    bluetooth.startDemo()
                    signIn(DEMO_USERNAME, UserRole.PATIENT)
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(onBack = back, onLoggedIn = ::signIn, onCreateAccount = {
                navController.navigate(Routes.SIGNUP) { popUpTo(Routes.WELCOME) }
            })
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(onBack = back, onSignedUp = ::signIn)
        }
        composable(Routes.HOME) {
            HomeScreen(
                username = user,
                bluetooth = bluetooth,
                onNavigate = { navController.navigate(it) }
            )
        }
        composable(Routes.CONNECT) {
            ConnectScreen(bluetooth = bluetooth, onBack = back, onConnected = back)
        }
        composable(Routes.LIVE) {
            LiveSignalsScreen(
                bluetooth = bluetooth,
                onBack = back,
                onConnect = { navController.navigate(Routes.CONNECT) },
                onTrain = { navController.navigate(Routes.TRAIN) }
            )
        }
        composable(Routes.THERAPY) {
            TherapySessionScreen(
                username = user,
                bluetooth = bluetooth,
                onBack = back,
                onConnect = { navController.navigate(Routes.CONNECT) },
                onCalibrate = { navController.navigate(Routes.CALIBRATE) }
            )
        }
        composable(Routes.GAME) {
            MinigameScreen(username = user, bluetooth = bluetooth, onBack = back)
        }
        composable(Routes.TRAIN) {
            TrainAiScreen(
                username = user,
                bluetooth = bluetooth,
                onBack = back,
                onConnect = { navController.navigate(Routes.CONNECT) },
                onTryIt = { navController.navigate(Routes.LIVE) { popUpTo(Routes.HOME) } },
                onCalibrate = { navController.navigate(Routes.CALIBRATE) { popUpTo(Routes.HOME) } }
            )
        }
        composable(Routes.CALIBRATE) {
            CalibrateScreen(
                username = user,
                bluetooth = bluetooth,
                onBack = back,
                onConnect = { navController.navigate(Routes.CONNECT) },
                onRetrain = { navController.navigate(Routes.TRAIN) { popUpTo(Routes.HOME) } }
            )
        }
        composable(Routes.HAND_CONTROLS) {
            ServoTestScreen(
                bluetooth = bluetooth,
                onBack = back,
                onConnect = { navController.navigate(Routes.CONNECT) }
            )
        }
        composable(Routes.CARE_TEAM) {
            ConnectPhysicianScreen(username = user, onBack = back)
        }
        composable(Routes.PHYSICIAN_HOME) {
            PhysicianDashboardScreen(
                physicianUsername = user,
                onOpenPatient = { navController.navigate(Routes.patient(it)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.PATIENT) { entry ->
            PatientDetailScreen(
                patientUsername = entry.arguments?.getString("username") ?: "",
                onBack = back,
                onOpenSession = { navController.navigate(Routes.session(it)) }
            )
        }
        composable(Routes.SESSION) { entry ->
            SessionDetailScreen(
                sessionId = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L,
                canEditNotes = role == UserRole.PHYSICIAN,
                onBack = back
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                username = user,
                role = role ?: UserRole.PATIENT,
                isDemo = user == DEMO_USERNAME,
                onBack = back,
                onHandControls = { navController.navigate(Routes.HAND_CONTROLS) },
                onSignOut = ::signOut
            )
        }
    }
}
