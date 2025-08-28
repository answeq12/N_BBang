package com.bergi.nbang_v1.presentation.auth

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.repository.AuthRepository
import com.bergi.nbang_v1.data.repository.PhoneAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _eventFlow = MutableSharedFlow<AuthEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private var storedVerificationId: String?
        get() = savedStateHandle.get<String>(KEY_VERIFICATION_ID)
        set(value) = savedStateHandle.set(KEY_VERIFICATION_ID, value)

    private var resendToken: PhoneAuthProvider.ForceResendingToken?
        get() = savedStateHandle.get<PhoneAuthProvider.ForceResendingToken>(KEY_RESEND_TOKEN)
        set(value) = savedStateHandle.set(KEY_RESEND_TOKEN, value)

    fun sendVerificationCode(phoneNumber: String, activity: Activity) = viewModelScope.launch {
        _isLoading.value = true
        try {
            authRepository.sendVerificationCode(phoneNumber, activity, resendToken).collectLatest { result ->
                when (result) {
                    is PhoneAuthResult.CodeSent -> {
                        storedVerificationId = result.verificationId
                        resendToken = result.resendToken
                        _eventFlow.emit(AuthEvent.CodeSent)
                    }
                    is PhoneAuthResult.VerificationCompleted -> {
                        signInWithPhoneAuthCredential(result.credential)
                    }
                    is PhoneAuthResult.VerificationFailed -> {
                        _eventFlow.emit(AuthEvent.Error(R.string.auth_verification_failed))
                    }
                }
            }
        } catch (e: Exception) {
            _eventFlow.emit(AuthEvent.Error(R.string.auth_error_generic))
        } finally {
            _isLoading.value = false
        }
    }

    fun verifyPhoneNumber(code: String) = viewModelScope.launch {
        _isLoading.value = true
        try {
            val verificationId = storedVerificationId
            if (verificationId != null) {
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                signInWithPhoneAuthCredential(credential)
            } else {
                _eventFlow.emit(AuthEvent.Error(R.string.auth_resend_code))
            }
        } catch (e: Exception) {
            _eventFlow.emit(AuthEvent.Error(R.string.auth_error_generic))
        } finally {
            _isLoading.value = false
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) = viewModelScope.launch {
        _isLoading.value = true
        try {
            if (firebaseAuth.currentUser != null) {
                authRepository.linkPhoneNumberToCurrentUser(credential)
                    .onSuccess {
                        _eventFlow.emit(AuthEvent.AuthSuccess(firebaseAuth.currentUser))
                    }
                    .onFailure {
                        _eventFlow.emit(AuthEvent.Error(R.string.auth_link_failed))
                    }
            } else {
                authRepository.signInWithPhoneAuthCredential(credential)
                    .onSuccess { user ->
                        _eventFlow.emit(AuthEvent.AuthSuccess(user))
                    }
                    .onFailure {
                        _eventFlow.emit(AuthEvent.Error(R.string.auth_signin_failed))
                    }
            }
        } catch (e: Exception) {
            _eventFlow.emit(AuthEvent.Error(R.string.auth_error_generic))
        } finally {
            _isLoading.value = false
        }
    }

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        _isLoading.value = true
        try {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user -> _eventFlow.emit(AuthEvent.AuthSuccess(user)) }
                .onFailure { _eventFlow.emit(AuthEvent.Error(R.string.google_sign_in_failed_generic)) }
        } catch (e: Exception) {
            _eventFlow.emit(AuthEvent.Error(R.string.auth_error_generic))
        } finally {
            _isLoading.value = false
        }
    }

    fun signInWithEmail(email: String, pass: String) = viewModelScope.launch {
        if (email.isEmpty() || pass.isEmpty()) {
            _eventFlow.emit(AuthEvent.Error(R.string.empty_email_or_password))
            return@launch
        }
        _isLoading.value = true
        try {
            authRepository.signInWithEmail(email, pass)
                .onSuccess { user -> _eventFlow.emit(AuthEvent.AuthSuccess(user)) }
                .onFailure { _eventFlow.emit(AuthEvent.Error(R.string.auth_signin_failed)) }
        } catch (e: Exception) {
            _eventFlow.emit(AuthEvent.Error(R.string.auth_error_generic))
        } finally {
            _isLoading.value = false
        }
    }

    fun checkCurrentUser() = viewModelScope.launch {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _eventFlow.emit(AuthEvent.AuthSuccess(currentUser))
        }
    }

    sealed class AuthEvent {
        object CodeSent : AuthEvent()
        data class AuthSuccess(val user: FirebaseUser?) : AuthEvent()
        data class Error(val messageResId: Int) : AuthEvent()
    }

    companion object {
        private const val KEY_VERIFICATION_ID = "key_verification_id"
        private const val KEY_RESEND_TOKEN = "key_resend_token"
    }
}
