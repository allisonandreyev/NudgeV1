package com.nudge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nudge.app.data.UserStats
import com.nudge.app.data.UserStatsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MinigameViewModel @Inject constructor(
    private val userStatsDao: UserStatsDao
) : ViewModel() {

    private val _currentUsername = MutableStateFlow<String?>(null)

    val highScore = _currentUsername.flatMapLatest { username ->
        if (username != null) {
            userStatsDao.getUserStats(username).map { it?.highScore ?: 0 }
        } else {
            flowOf(0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setUsername(username: String) {
        _currentUsername.value = username
    }

    fun updateHighScore(score: Int) {
        val username = _currentUsername.value ?: return
        if (score > highScore.value) {
            viewModelScope.launch {
                userStatsDao.insertOrUpdate(UserStats(username = username, highScore = score))
            }
        }
    }
}
