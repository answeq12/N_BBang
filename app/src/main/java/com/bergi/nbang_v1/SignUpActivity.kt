package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // UI 요소
    private lateinit var emailEditText: EditText
    private lateinit var nicknameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var phoneCheckBox: CheckBox
    private lateinit var phoneVerificationGroup: Group
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
        initViews()
        setupListeners()
    }

    private fun initViews() {
        emailEditText = findViewById(R.id.editTextSignUpEmail)
        nicknameEditText = findViewById(R.id.editTextSignUpNickname)
        passwordEditText = findViewById(R.id.editTextSignUpPassword)
        phoneCheckBox = findViewById(R.id.checkboxVerifyPhone)
        phoneVerificationGroup = findViewById(R.id.groupPhoneVerification)
        phoneEditText = findViewById(R.id.editTextPhoneNumber)
        codeEditText = findViewById(R.id.editTextVerificationCode)
        sendCodeButton = findViewById(R.id.buttonSendCode)
        confirmSignUpButton = findViewById(R.id.buttonConfirmSignUp)
    }

    private fun setupListeners() {
        // 체크박스를 누르면 휴대폰 인증 UI를 보여주거나 숨김
        phoneCheckBox.setOnCheckedChangeListener { _, isChecked ->
            phoneVerificationGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // "인증번호 받기" 버튼 리스너
        sendCodeButton.setOnClickListener {
            val phoneNumber = "+82" + phoneEditText.text.toString().substring(1)
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this).setCallbacks(callbacks).build()
            PhoneAuthProvider.verifyPhoneNumber(options)
            Toast.makeText(this, "인증번호를 보냈습니다.", Toast.LENGTH_SHORT).show()
        }

        // "가입 완료" 버튼 리스너
        confirmSignUpButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // 필수 정보 유효성 검사
            if (email.isEmpty() || nickname.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일, 닉네임, 비밀번호는 필수 항목입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 휴대폰 인증을 선택했는지 여부에 따라 로직 분기
            if (phoneCheckBox.isChecked) {
                // 휴대폰 인증 포함하여 가입
                val code = codeEditText.text.toString().trim()
                if (code.isEmpty() || storedVerificationId == null) {
                    Toast.makeText(this, "휴대폰 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // 이메일 계정 생성 후 휴대폰 번호 연결
                createUserAndLinkPhone(email, password, nickname, code)
            } else {
                // 휴대폰 인증 없이 이메일로만 가입
                createUserOnlyWithEmail(email, password, nickname)
            }
        }
    }

    // --- 휴대폰 인증 없이 가입하는 로직 ---
    private fun createUserOnlyWithEmail(email: String, pass: String, nickname: String) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                updateUserProfile(task.result.user!!, nickname)
            } else {
                handleSignUpFailure(task.exception)
            }
        }
    }

    // --- 휴대폰 인증 포함하여 가입하는 로직 ---
    private fun createUserAndLinkPhone(email: String, pass: String, nickname: String, code: String) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = task.result.user!!
                val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
                linkPhoneNumber(user, credential, nickname)
            } else {
                handleSignUpFailure(task.exception)
            }
        }
    }

    private fun linkPhoneNumber(user: FirebaseUser, credential: AuthCredential, nickname: String) {
        user.linkWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                updateUserProfile(user, nickname)
            } else {
                Toast.makeText(this, "휴대폰 인증에 실패했습니다. 코드를 확인해주세요.", Toast.LENGTH_LONG).show()
                user.delete() // 휴대폰 인증 실패 시 방금 만든 계정 삭제
            }
        }
    }

    // --- 공통 로직 ---
    private fun updateUserProfile(user: FirebaseUser, nickname: String) {
        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(nickname).build()
        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // --- Firestore에 사용자 정보 저장 (이 부분 추가!) ---
                val db = FirebaseFirestore.getInstance()
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "nickname" to nickname,
                    "phoneNumber" to (user.phoneNumber ?: ""), // 휴대폰 인증 시 번호 저장
                    "location" to "" // 위치 정보는 비워서 초기화
                )

                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "회원가입 성공! ($nickname 님 환영합니다)", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        finishSignUp(Activity.RESULT_OK)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "DB 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                        user.delete() // DB 저장 실패 시 계정 롤백
                    }
                // --- 여기까지 추가 ---

            } else {
                Toast.makeText(this, "닉네임 설정에 실패했습니다.", Toast.LENGTH_LONG).show()
                auth.signOut()
                finishSignUp(Activity.RESULT_CANCELED)
            }
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) { codeEditText.setText(credential.smsCode) }
        override fun onVerificationFailed(e: FirebaseException) { Toast.makeText(applicationContext, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show() }
        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) { storedVerificationId = verificationId; resendToken = token }
    }

    private fun handleSignUpFailure(exception: Exception?) {
        var errorMessage = "회원가입 실패: ${exception?.message}"
        if (exception?.message?.contains("email address is already in use") == true) {
            errorMessage = "이미 사용 중인 이메일입니다."
        }
        Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun finishSignUp(resultCode: Int) {
        setResult(resultCode, Intent())
        finish()
    }
}
