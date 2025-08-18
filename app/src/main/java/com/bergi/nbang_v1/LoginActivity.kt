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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    // private lateinit var nicknameEditText: EditText // 삭제
    private lateinit var loginButton: Button
    private lateinit var signUpButton: Button
    private lateinit var googleSignInButton: SignInButton

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var signUpLauncher: ActivityResultLauncher<Intent> // SignUpActivity 결과 처리를 위함

    private val TAG = "LoginActivity"
    private val WEB_CLIENT_ID = "979223132862-8aiv5g67eklhs2luetcuj9lniiti4kfc.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        // nicknameEditText = findViewById(R.id.editTextNickname) // 삭제
        loginButton = findViewById(R.id.buttonLogin)
        signUpButton = findViewById(R.id.buttonSignUp)
        googleSignInButton = findViewById(R.id.buttonGoogleSignIn)

        // Google SignInOptions 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Google 로그인 결과 처리
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

        // SignUpActivity 결과 처리
        signUpLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 회원가입 성공 시 (SignUpActivity에서 auth.signOut() 했으므로 여기서 다시 로그인 필요)
                // val signedUpUserEmail = result.data?.getStringExtra("USER_EMAIL") // SignUpActivity에서 이메일 전달 시
                // emailEditText.setText(signedUpUserEmail) // 예: 이메일 필드 자동 채우기
                // passwordEditText.requestFocus() // 비밀번호 필드에 포커스
                Toast.makeText(this, "회원가입이 완료되었습니다. 로그인해주세요.", Toast.LENGTH_LONG).show()
            } else {
                // 회원가입 취소 또는 실패
                Log.d(TAG, "SignUpActivity cancelled or failed. Result code: ${result.resultCode}")
                // 필요시 실패 메시지 표시 (SignUpActivity에서 이미 Toast를 띄웠을 수 있음)
            }
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        signUpButton.setOnClickListener {
            // SignUpActivity를 시작한다.
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
                    Log.d(TAG, "Google signIn with Firebase: success. User: ${user?.displayName}")
                    Toast.makeText(this, "Google 로그인 성공! (${user?.displayName ?: user?.email})", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Log.w(TAG, "Google signIn with Firebase: failure", task.exception)
                    Toast.makeText(this, "Google 로그인 연동 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // createUserWithEmailPassword 함수 삭제 (SignUpActivity로 이동)
    // updateUserProfile 함수 삭제 (SignUpActivity로 이동)

    private fun signInWithEmailPassword(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    Toast.makeText(baseContext, "로그인 성공!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    var errorMessage = "로그인 실패: ${task.exception?.message}"
                    if (task.exception?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true) {
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
            navigateToMain()
        }
        // LoginActivity가 시작될 때는 항상 초기 상태로 시작
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
