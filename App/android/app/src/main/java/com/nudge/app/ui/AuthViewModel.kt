package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.User
import com.nudge.app.data.UserDao
import com.nudge.app.data.UserRole
import com.nudge.app.utils.SecurityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState = _authState.asStateFlow()

    fun login(username: String, password: String, selectedRole: UserRole) {
        viewModelScope.launch {
            val user = userDao.getUserByUsername(username)
            if (user == null) {
                _authState.value = AuthResult.Error("User does not exist")
            } else if (!SecurityUtils.checkPassword(password, user.passwordHash)) {
                _authState.value = AuthResult.Error("Incorrect password")
            } else if (user.role != selectedRole) {
                _authState.value = AuthResult.Error("Incorrect role for this account")
            } else {
                _authState.value = AuthResult.Success(user.username, user.role)
            }
        }
    }

    fun signUp(username: String, password: String, role: UserRole) {
        viewModelScope.launch {
            val existingUser = userDao.getUserByUsername(username)
            if (existingUser != null) {
                _authState.value = AuthResult.Error("Username already taken")
            } else {
                try {
                    val hashedPassword = SecurityUtils.hashPassword(password)
                    userDao.registerUser(User(username, hashedPassword, role))
                    _authState.value = AuthResult.SignUpSuccess(username)
                } catch (e: Exception) {
                    _authState.value = AuthResult.Error("Registration failed")
                }
            }
        }
    }

    fun resetAuthState() {
        _authState.value = null
    }
}

sealed class AuthResult {
    data class Success(val username: String, val role: UserRole) : AuthResult()
    data class SignUpSuccess(val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
