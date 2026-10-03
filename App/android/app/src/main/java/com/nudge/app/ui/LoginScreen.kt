package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.UserRole
import com.nudge.app.ui.components.ErrorText
import com.nudge.app.ui.components.NudgeScreen
import com.nudge.app.ui.components.NudgeTextField
import com.nudge.app.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (String, UserRole) -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (val result = authState) {
            is AuthResult.Success -> onLoggedIn(result.username, result.role)
            is AuthResult.Error -> error = result.message
            else -> {}
        }
        if (authState != null) viewModel.resetAuthState()
    }

    fun submit() {
        if (username.isBlank() || password.isBlank()) {
            error = "Enter your username and password."
        } else {
            viewModel.login(username.trim(), password)
        }
    }

    NudgeScreen(title = "", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in to continue your therapy.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            NudgeTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = "Username",
                leadingIcon = Icons.Default.Person
            )
            Spacer(Modifier.height(12.dp))
            NudgeTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = ::submit
            )
            ErrorText(error)

            Spacer(Modifier.height(28.dp))
            PrimaryButton("Sign in", onClick = ::submit)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("New to Nudge?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onCreateAccount) { Text("Create an account") }
            }
        }
    }
}
