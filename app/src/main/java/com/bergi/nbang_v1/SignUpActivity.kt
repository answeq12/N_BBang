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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
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

    // ▼▼▼ [수정] 인증하기 버튼 추가 ▼▼▼
    private lateinit var verifyCodeButton: Button

    // 전화번호 인증 관련 변수
    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    // ▼▼▼ [수정] PhoneAuthCredential 객체를 저장할 변수 추가 ▼▼▼
    private var phoneAuthCredential: PhoneAuthCredential? = null

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

        // ▼▼▼ [수정] 새로 추가한 버튼 초기화 ▼▼▼
        verifyCodeButton = findViewById(R.id.buttonVerifyCode)
    }

    private fun setupListeners() {
        phoneCheckBox.setOnCheckedChangeListener { _, isChecked ->
            phoneVerificationGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        sendCodeButton.setOnClickListener {
            val phoneNumberInput = phoneEditText.text.toString()
            if (phoneNumberInput.startsWith("0")) {
                val phoneNumber = "+82" + phoneNumberInput.substring(1)
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber).setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this).setCallbacks(callbacks).build()
                PhoneAuthProvider.verifyPhoneNumber(options)
                Toast.makeText(this, "인증번호를 보냈습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "올바른 휴대폰 번호를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // ▼▼▼ [신규] "인증하기" 버튼 리스너 ▼▼▼
        verifyCodeButton.setOnClickListener {
            val code = codeEditText.text.toString().trim()
            if (code.isEmpty() || storedVerificationId == null) {
                Toast.makeText(this, "인증번호를 먼저 받으신 후 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // PhoneAuthCredential 객체를 미리 생성해 둡니다.
            // 실제 서버 검증은 "가입 완료" 시점에 이루어집니다.
            phoneAuthCredential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)

            // 사용자에게 인증이 확인되었음을 UI로 피드백
            Toast.makeText(this, "인증이 확인되었습니다. 가입 완료를 진행해주세요.", Toast.LENGTH_SHORT).show()
            phoneEditText.isEnabled = false
            codeEditText.isEnabled = false
            sendCodeButton.isEnabled = false
            verifyCodeButton.isEnabled = false
            phoneVerificationGroup.alpha = 0.5f // 비활성화된 것처럼 보이게 처리
        }


        confirmSignUpButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || nickname.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일, 닉네임, 비밀번호는 필수 항목입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phoneCheckBox.isChecked) {
                // ▼▼▼ [수정] EditText의 코드를 직접 쓰는 대신, 생성된 credential 객체가 있는지 확인 ▼▼▼
                if (phoneAuthCredential == null) {
                    Toast.makeText(this, "휴대폰 인증을 먼저 완료해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                createUserAndLinkPhone(email, password, nickname, phoneAuthCredential!!)
            } else {
                createUserOnlyWithEmail(email, password, nickname)
            }
        }
    }

    private fun createUserOnlyWithEmail(email: String, pass: String, nickname: String) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                updateUserProfile(task.result.user!!, nickname)
            } else {
                handleSignUpFailure(task.exception)
            }
        }
    }

    // ▼▼▼ [수정] 마지막 파라미터를 String code에서 PhoneAuthCredential credential로 변경 ▼▼▼
    private fun createUserAndLinkPhone(email: String, pass: String, nickname: String, credential: PhoneAuthCredential) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = task.result.user!!
                linkPhoneNumber(user, credential, nickname)
            } else {
                // 계정 생성 실패 시, 인증 UI 초기화
                resetPhoneVerificationUI()
                handleSignUpFailure(task.exception)
            }
        }
    }

    private fun linkPhoneNumber(user: FirebaseUser, credential: AuthCredential, nickname: String) {
        user.linkWithCredential(credential).addOnCompleteListener(this) { linkTask ->
            if (linkTask.isSuccessful) {
                user.reload().addOnCompleteListener { reloadTask ->
                    if (reloadTask.isSuccessful) {
                        updateUserProfile(user, nickname)
                    } else {
                        Toast.makeText(this, "사용자 정보 갱신에 실패했습니다.", Toast.LENGTH_LONG).show()
                        user.delete()
                        resetPhoneVerificationUI()
                    }
                }
            } else {
                // ▼▼▼ [수정] 실제 인증 실패는 여기서 발생! UI 초기화 로직 추가 ▼▼▼
                Toast.makeText(this, "인증에 실패했습니다. 번호를 확인하고 다시 시도해주세요.", Toast.LENGTH_LONG).show()
                user.delete()
                resetPhoneVerificationUI()
            }
        }
    }

    // ▼▼▼ [신규] 휴대폰 인증 관련 UI를 초기 상태로 되돌리는 함수 ▼▼▼
    private fun resetPhoneVerificationUI() {
        phoneAuthCredential = null
        phoneEditText.isEnabled = true
        codeEditText.isEnabled = true
        sendCodeButton.isEnabled = true
        verifyCodeButton.isEnabled = true
        phoneVerificationGroup.alpha = 1.0f
        codeEditText.text.clear()
        Toast.makeText(this, "휴대폰 인증 정보를 초기화합니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
    }


    private fun updateUserProfile(user: FirebaseUser, nickname: String) {
        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(nickname).build()
        user.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val db = FirebaseFirestore.getInstance()
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "nickname" to nickname,
                    "phoneNumber" to (user.phoneNumber ?: ""),
                    "location" to ""
                )

                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "회원가입 성공! ($nickname 님 환영합니다)", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        finishSignUp(Activity.RESULT_OK)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "DB 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                        user.delete()
                        resetPhoneVerificationUI()
                    }

            } else {
                Toast.makeText(this, "닉네임 설정에 실패했습니다.", Toast.LENGTH_LONG).show()
                auth.signOut()
                finishSignUp(Activity.RESULT_CANCELED)
            }
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            codeEditText.setText(credential.smsCode)
            // ▼▼▼ [수정] 자동 인증 완료 시 바로 credential 저장 및 UI 피드백 ▼▼▼
            phoneAuthCredential = credential
            Toast.makeText(applicationContext, "인증번호가 자동으로 확인되었습니다.", Toast.LENGTH_SHORT).show()
            phoneEditText.isEnabled = false
            codeEditText.isEnabled = false
            sendCodeButton.isEnabled = false
            verifyCodeButton.isEnabled = false
            phoneVerificationGroup.alpha = 0.5f
        }
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