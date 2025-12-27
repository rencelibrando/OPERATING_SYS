package org.example.project.admin.presentation

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.admin.data.AdminUser
import org.example.project.admin.data.UserManagementRepository

class UserManagementViewModel : ViewModel() {
    private val userRepository = UserManagementRepository()

    private val _users = mutableStateOf<List<AdminUser>>(emptyList())
    val users: State<List<AdminUser>> = _users

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = userRepository.getAllUsers()
                result.fold(
                    onSuccess = { users ->
                        _users.value = users
                        _successMessage.value = "Loaded ${users.size} users"
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to load users: ${e.message}"
                        _users.value = emptyList()
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error loading users: ${e.message}"
                _users.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val result = userRepository.deleteUser(userId)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "User deleted successfully!"
                        loadUsers() // Reload list
                    },
                    onFailure = { e ->
                        _errorMessage.value = "Failed to delete user: ${e.message}"
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
