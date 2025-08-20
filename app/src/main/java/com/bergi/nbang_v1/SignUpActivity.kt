package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // UI 요소
    private lateinit var emailEditText: EditText
    private lateinit var nicknameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var codeEditText: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var confirmSignUpButton: Button

    // 전화번호 인증 관련 변수
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val TAG = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = Firebase.auth

        // UI 요소 초기화
        emailEditText = findViewById(R.id.editTextSignUpEmail)
        nicknameEditText = findViewById(R.id.editTextSignUpNickname)
        passwordEditText = findViewById(R.id.editTextSignUpPassword)
        phoneEditText = findViewById(R.id.editTextPhoneNumber)
        codeEditText = findViewById(R.id.editTextVerificationCode)
        sendCodeButton = findViewById(R.id.buttonSendCode)
        confirmSignUpButton = findViewById(R.id.buttonConfirmSignUp)

        // "인증번호 받기" 버튼 리스너
        sendCodeButton.setOnClickListener {
            val phoneNumber = phoneEditText.text.toString().trim()
            if (phoneNumber.isEmpty() || phoneNumber.length < 10) {
                Toast.makeText(this, "올바른 전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fullPhoneNumber = "+82" + phoneNumber.substring(1)

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(fullPhoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
            Toast.makeText(this, "인증번호를 보냈습니다.", Toast.LENGTH_SHORT).show()
        }

        // "가입 완료" 버튼 리스너
        confirmSignUpButton.setOnClickListener {
            // 모든 정보가 입력되었는지 최종 확인
            val email = emailEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val code = codeEditText.text.toString().trim()

            if (email.isEmpty() || nickname.isEmpty() || password.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (storedVerificationId == null) {
                Toast.makeText(this, "휴대폰 인증을 먼저 진행해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. 먼저 이메일/비밀번호로 계정을 생성합니다.
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.d(TAG, "Email account created successfully.")
                        // 2. 계정 생성이 성공하면, 이어서 휴대폰 번호를 이 계정에 연결(인증)합니다.
                        user?.let {
                            val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
                            linkPhoneNumber(it, credential, nickname)
                        }
                    } else {
                        // 이메일 계정 생성 실패 처리
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        var errorMessage = "회원가입 실패: ${task.exception?.message}"
                        if (task.exception?.message?.contains("email address is already in use") == true) {
                            errorMessage = "이미 사용 중인 이메일입니다."
                        }
                        Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    // 휴대폰 인증 콜백
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // 자동 인증 성공 시 (드문 경우)
            Log.d(TAG, "onVerificationCompleted:$credential")
            codeEditText.setText(credential.smsCode) // 받은 코드를 자동으로 입력
        }
        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "onVerificationFailed", e)
            Toast.makeText(applicationContext, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            storedVerificationId = verificationId
            resendToken = token
        }
    }

    // 생성된 이메일 계정에 휴대폰 번호를 연결하는 함수
    private fun linkPhoneNumber(user: FirebaseUser, credential: AuthCredential, nickname: String) {
        user.linkWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Phone number linked successfully.")
                    // 3. 휴대폰 번호 연결까지 성공하면, 마지막으로 닉네임을 설정합니다.
                    updateUserProfile(user, nickname)
                } else {
                    Log.w(TAG, "linkPhoneNumber:failure", task.exception)
                    Toast.makeText(this, "휴대폰 인증에 실패했습니다. 코드를 확인해주세요.", Toast.LENGTH_LONG).show()
                    // 휴대폰 인증에 실패했으므로, 방금 만든 이메일 계정을 삭제하여 다시 시도하도록 유도
                    user.delete()
                }
            }
    }

    // 닉네임 설정 함수
    private fun updateUserProfile(user: FirebaseUser, nickname: String) {
        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(nickname).build()
        user.updateProfile(profileUpdates)
            .addOnCompleteListener { profileTask ->
                if (profileTask.isSuccessful) {
                    Log.d(TAG, "User profile updated.")
                    Toast.makeText(this, "회원가입 성공! ($nickname 님 환영합니다)", Toast.LENGTH_SHORT).show()
                    auth.signOut() // 자동 로그인 해제
                    finishSignUp(Activity.RESULT_OK)
                } else {
                    // 닉네임 설정 실패 처리
                    Toast.makeText(this, "닉네임 설정에 실패했습니다.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    finishSignUp(Activity.RESULT_CANCELED)
                }
            }
    }

    private fun finishSignUp(resultCode: Int) {
        val resultIntent = Intent()
        setResult(resultCode, resultIntent)
        finish()
    }
}
