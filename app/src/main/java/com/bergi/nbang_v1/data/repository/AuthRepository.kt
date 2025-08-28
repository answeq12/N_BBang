package com.bergi.nbang_v1.data.repository

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    fun sendVerificationCode(
        phoneNumber: String,
        activity: Activity, // Activity should be passed from the UI layer (Fragment/Activity)
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): Flow<PhoneAuthResult>

    suspend fun linkPhoneNumberToCurrentUser(credential: AuthCredential): Result<Unit>
    suspend fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential): Result<FirebaseUser>
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser>
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override fun sendVerificationCode(
        phoneNumber: String,
        activity: Activity,
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): Flow<PhoneAuthResult> = callbackFlow {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                trySend(PhoneAuthResult.VerificationCompleted(credential))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                trySend(PhoneAuthResult.VerificationFailed(e))
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                trySend(PhoneAuthResult.CodeSent(verificationId, token))
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        resendToken?.let {
            optionsBuilder.setForceResendingToken(it)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())

        awaitClose { /* Cleanup if needed */ }
    }

    override suspend fun linkPhoneNumberToCurrentUser(credential: AuthCredential): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw IllegalStateException("User not logged in")
            currentUser.linkWithCredential(credential).await()
            currentUser.reload().await() // Reload user to get updated phone number
            val updatedPhoneNumber = auth.currentUser?.phoneNumber ?: ""
            firestore.collection("users").document(currentUser.uid)
                .update("phoneNumber", updatedPhoneNumber)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { Result.success(it) } ?: Result.failure(Exception("Sign-in failed: User is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user!!
            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

            if (isNewUser) {
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "nickname" to (user.displayName ?: user.email?.split("@")?.get(0)),
                    "phoneNumber" to "",
                    "location" to ""
                )
                firestore.collection("users").document(user.uid).set(userMap).await()
            }
            saveFCMTokenToFirestore(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user!!
            saveFCMTokenToFirestore(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveFCMTokenToFirestore(user: FirebaseUser) {
        try {
            val token = Firebase.messaging.token.await()
            val userDocRef = firestore.collection("users").document(user.uid)
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge()).await()
        } catch (e: Exception) {
            // Log the exception, but don't block the sign-in flow
        }
    }
}

sealed class PhoneAuthResult {
    data class VerificationCompleted(val credential: PhoneAuthCredential) : PhoneAuthResult()
    data class VerificationFailed(val exception: FirebaseException) : PhoneAuthResult()
    data class CodeSent(val verificationId: String, val resendToken: PhoneAuthProvider.ForceResendingToken) : PhoneAuthResult()
}
