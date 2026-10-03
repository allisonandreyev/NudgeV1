package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.*
import com.nudge.app.utils.SecurityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userDao: UserDao,
    private val dataPointDao: DataPointDao,
    private val userStatsDao: UserStatsDao,
    private val physicianConnectionDao: PhysicianConnectionDao,
    private val therapySessionDao: TherapySessionDao,
    private val models: com.nudge.app.ai.ModelRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState = _authState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val user = userDao.getUserByUsername(username)
            // BCrypt is deliberately slow, keep it off the main thread
            val ok = user != null && withContext(Dispatchers.Default) {
                SecurityUtils.checkPassword(password, user.passwordHash)
            }
            _authState.value = if (user != null && ok) {
                AuthResult.Success(user.username, user.role)
            } else {
                AuthResult.Error("That username and password don't match.")
            }
        }
    }

    fun signUp(username: String, password: String, role: UserRole) {
        viewModelScope.launch {
            if (username.equals(com.nudge.app.DEMO_USERNAME, ignoreCase = true) || userDao.getUserByUsername(username) != null) {
                _authState.value = AuthResult.Error("That username is taken.")
                return@launch
            }
            try {
                val hashed = withContext(Dispatchers.Default) { SecurityUtils.hashPassword(password) }
                userDao.registerUser(User(username, hashed, role))
                _authState.value = AuthResult.Success(username, role)
            } catch (e: Exception) {
                _authState.value = AuthResult.Error("Couldn't create the account. Try again.")
            }
        }
    }

    fun deleteAccount(username: String) {
        viewModelScope.launch {
            try {
                dataPointDao.deleteDataForUser(username)
                userStatsDao.deleteStatsForUser(username)
                physicianConnectionDao.deleteAllConnectionsForUser(username)
                therapySessionDao.deleteSessionsForUser(username)
                userDao.deleteUser(username)
                models.delete(username)
                _authState.value = AuthResult.Deleted
            } catch (e: Exception) {
                _authState.value = AuthResult.Error("Couldn't delete the account.")
            }
        }
    }

    fun resetAuthState() {
        _authState.value = null
    }
}

sealed class AuthResult {
    data class Success(val username: String, val role: UserRole) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Deleted : AuthResult()
}
