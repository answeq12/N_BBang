package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.ktx.messaging
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var signUpButton: Button
    private lateinit var googleSignInButton: SignInButton

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var signUpLauncher: ActivityResultLauncher<Intent>

    private val TAG = "LoginActivity"
    private val WEB_CLIENT_ID = "979223132862-8aiv5g67eklhs2luetcuj9lniiti4kfc.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        signUpButton = findViewById(R.id.buttonSignUp)
        googleSignInButton = findViewById(R.id.buttonGoogleSignIn)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Log.w(TAG, "Google sign in failed", e)
                    Toast.makeText(this, "Google 로그인 실패: ${e.message} (코드: ${e.statusCode})", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w(TAG, "Google sign in flow cancelled or failed. Result code: ${result.resultCode}")
                if (result.resultCode != Activity.RESULT_CANCELED) {
                    Toast.makeText(this, "Google 로그인에 실패했습니다. (결과 코드: ${result.resultCode})", Toast.LENGTH_SHORT).show()
                }
            }
        }

        signUpLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "회원가입이 완료되었습니다. 로그인해주세요.", Toast.LENGTH_LONG).show()
            } else {
                Log.d(TAG, "SignUpActivity cancelled or failed. Result code: ${result.resultCode}")
            }
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        signUpButton.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            signUpLauncher.launch(intent)
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            signInWithEmailPassword(email, password)
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val authResult = task.result
                    val isNewUser = authResult?.additionalUserInfo?.isNewUser ?: false

                    // 새로운 사용자인 경우에만 Firestore에 정보를 생성합니다.
                    if (isNewUser && user != null) {
                        val db = FirebaseFirestore.getInstance()
                        val nickname = getNicknameForWelcome(user)
                        val userMap = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "nickname" to nickname,
                            "phoneNumber" to "", // 초기값
                            "location" to ""  // 초기값
                        )
                        db.collection("users").document(user.uid).set(userMap)
                            .addOnSuccessListener {
                                Log.d(TAG, "New Google user's info saved to Firestore.")
                                saveFCMTokenToFirestore(user) // 토큰 저장 로직 추가
                                navigateToWelcomeActivity(user)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error saving new Google user to Firestore", e)
                                saveFCMTokenToFirestore(user) // 실패해도 토큰은 저장 시도
                                navigateToWelcomeActivity(user)
                            }
                    } else {
                        Log.d(TAG, "Google signIn with Firebase: success. User: ${user?.displayName}")
                        saveFCMTokenToFirestore(user) // 기존 사용자는 로그인 후 바로 토큰 저장
                        navigateToWelcomeActivity(user)
                    }
                } else {
                    Log.w(TAG, "Google signIn with Firebase: failure", task.exception)
                    Toast.makeText(this, "Google 로그인 연동 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun signInWithEmailPassword(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    Toast.makeText(baseContext, "로그인 성공!", Toast.LENGTH_SHORT).show()
                    saveFCMTokenToFirestore(user) // 이메일 로그인 성공 시 토큰 저장 로직 추가
                    navigateToWelcomeActivity(user)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    var errorMessage = "로그인 실패: ${task.exception?.message}"
                    if (task.exception?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
                        task.exception?.message?.contains("INVALID_PASSWORD") == true ||
                        task.exception?.message?.contains("USER_NOT_FOUND") == true) {
                        errorMessage = "이메일 또는 비밀번호가 올바르지 않습니다."
                    } else if (task.exception?.message?.contains("USER_DISABLED") == true) {
                        errorMessage = "사용 중지된 계정입니다."
                    }
                    Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 앱 시작 시 자동 로그인 되면 WelcomeActivity로 바로 이동
            navigateToWelcomeActivity(currentUser)
        }
    }

    // 새로운 FCM 토큰 저장 함수 추가
    private fun saveFCMTokenToFirestore(user: FirebaseUser?) {
        if (user == null) {
            Log.w(TAG, "User is null, cannot save FCM token.")
            return
        }

        Firebase.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val token = task.result
                val userDocRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)
                userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM 토큰이 로그인 후 성공적으로 업데이트되었습니다.")
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "FCM 토큰 업데이트 중 오류 발생.", e)
                    }
            } else {
                Log.w(TAG, "FCM 토큰을 가져오는 데 실패했습니다.", task.exception)
            }
        }
    }

    private fun navigateToWelcomeActivity(user: FirebaseUser?) {
        val nickname = getNicknameForWelcome(user)
        val intent = Intent(this, WelcomeActivity::class.java).apply {
            putExtra("NICKNAME", nickname)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun getNicknameForWelcome(user: FirebaseUser?): String {
        return user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.split('@')?.firstOrNull()
            ?: "사용자"
    }
}