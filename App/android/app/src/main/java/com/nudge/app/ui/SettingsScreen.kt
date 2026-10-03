package com.nudge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nudge.app.data.UserRole
import com.nudge.app.ui.components.*

@Composable
fun SettingsScreen(
    username: String,
    role: UserRole,
    isDemo: Boolean,
    onBack: () -> Unit,
    onHandControls: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthResult.Deleted) {
            viewModel.resetAuthState()
            onSignOut()
        }
    }

    NudgeScreen(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            NudgeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (role == UserRole.PHYSICIAN) Icons.Default.MedicalServices else Icons.Default.Person, size = 52)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(username, style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                isDemo -> "Demo account"
                                role == UserRole.PHYSICIAN -> "Clinician"
                                else -> "Patient"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (role == UserRole.PATIENT) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("Device")
                ActionRow(
                    icon = Icons.Default.Tune,
                    title = "Hand controls",
                    subtitle = "Move the motors yourself, for setup and testing",
                    onClick = onHandControls
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Account")
            SecondaryButton("Sign out", icon = Icons.AutoMirrored.Filled.Logout, onClick = onSignOut)

            if (!isDemo) {
                Spacer(Modifier.height(40.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete your account?",
            message = "This permanently erases your sessions, scores and clinician links from this phone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount(username)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
