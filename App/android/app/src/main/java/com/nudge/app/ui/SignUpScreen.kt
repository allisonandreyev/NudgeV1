package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.UserRole
import com.nudge.app.ui.components.ErrorText
import com.nudge.app.ui.components.NudgeScreen
import com.nudge.app.ui.components.NudgeTextField
import com.nudge.app.ui.components.PrimaryButton
import com.nudge.app.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSignedUp: (String, UserRole) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.PATIENT) }
    var error by remember { mutableStateOf<String?>(null) }
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        when (val result = authState) {
            is AuthResult.Success -> onSignedUp(result.username, result.role)
            is AuthResult.Error -> error = result.message
            else -> {}
        }
        if (authState != null) viewModel.resetAuthState()
    }

    fun submit() {
        error = when {
            username.isBlank() || password.isBlank() -> "Choose a username and password."
            password.length < 6 -> "Use at least 6 characters for your password."
            password != confirmPassword -> "Passwords don't match."
            else -> null
        }
        if (error == null) viewModel.signUp(username.trim(), password, role)
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
            Text("Create your account", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your data stays encrypted on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            SectionLabel("I am a")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(UserRole.PATIENT to "Patient", UserRole.PHYSICIAN to "Clinician").forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = role == value,
                        onClick = { role = value },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(20.dp))

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
                isPassword = true
            )
            Spacer(Modifier.height(12.dp))
            NudgeTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = "Confirm password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = ::submit
            )
            ErrorText(error)

            Spacer(Modifier.height(28.dp))
            PrimaryButton("Create account", onClick = ::submit)
            Spacer(Modifier.height(24.dp))
        }
    }
}
