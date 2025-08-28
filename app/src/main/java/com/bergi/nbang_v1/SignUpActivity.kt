package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
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
    private lateinit var progressBar: ProgressBar

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
        progressBar = findViewById(R.id.signUpProgressBar) // ProgressBar 초기화
    }

    private fun setupListeners() {
        phoneCheckBox.setOnCheckedChangeListener { _, isChecked ->
            phoneVerificationGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        sendCodeButton.setOnClickListener {
            val phoneNumberInput = phoneEditText.text.toString().trim()
            if (phoneNumberInput.length > 10 && phoneNumberInput.startsWith("0")) {
                val phoneNumber = "+82" + phoneNumberInput.substring(1)
                sendCodeButton.isEnabled = false // 스팸 방지를 위해 버튼 비활성화
                Toast.makeText(this, "인증번호를 전송 중입니다...", Toast.LENGTH_SHORT).show()
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks)
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
            } else {
                Toast.makeText(this, "올바른 휴대폰 번호를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
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

            showInProgress(true)

            if (phoneCheckBox.isChecked) {
                val code = codeEditText.text.toString().trim()
                if (code.isEmpty() || storedVerificationId == null) {
                    Toast.makeText(this, "휴대폰 인증을 먼저 완료해주세요.", Toast.LENGTH_SHORT).show()
                    showInProgress(false)
                    return@setOnClickListener
                }
                val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
                createUserAndLinkPhone(email, password, nickname, credential)
            } else {
                createUserOnlyWithEmail(email, password, nickname)
            }
        }
    }

    private fun showInProgress(inProgress: Boolean) {
        emailEditText.isEnabled = !inProgress
        nicknameEditText.isEnabled = !inProgress
        passwordEditText.isEnabled = !inProgress
        phoneCheckBox.isEnabled = !inProgress
        phoneEditText.isEnabled = !inProgress
        codeEditText.isEnabled = !inProgress
        sendCodeButton.isEnabled = !inProgress
        confirmSignUpButton.visibility = if (inProgress) View.INVISIBLE else View.VISIBLE
        progressBar.visibility = if (inProgress) View.VISIBLE else View.GONE
    }

    private fun createUserOnlyWithEmail(email: String, pass: String, nickname: String) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                updateUserProfile(task.result.user!!, nickname)
            } else {
                showInProgress(false)
                handleSignUpFailure(task.exception)
            }
        }
    }

    private fun createUserAndLinkPhone(email: String, pass: String, nickname: String, credential: PhoneAuthCredential) {
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = task.result.user!!
                linkPhoneNumber(user, credential, nickname)
            } else {
                showInProgress(false)
                handleSignUpFailure(task.exception)
            }
        }
    }

    private fun linkPhoneNumber(user: FirebaseUser, credential: AuthCredential, nickname: String) {
        user.linkWithCredential(credential).addOnCompleteListener(this) { linkTask ->
            if (linkTask.isSuccessful) {
                updateUserProfile(user, nickname)
            } else {
                Toast.makeText(this, "인증에 실패했습니다. 번호를 확인하고 다시 시도해주세요.", Toast.LENGTH_LONG).show()
                user.delete().addOnCompleteListener {
                    showInProgress(false)
                    if (it.isSuccessful) {
                        Log.d(TAG, "User deleted successfully after failed phone link.")
                    } else {
                        Log.w(TAG, "Failed to delete user after failed phone link.", it.exception)
                    }
                }
            }
        }
    }

    private fun updateUserProfile(user: FirebaseUser, nickname: String) {
        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(nickname).build()
        user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
            if (profileTask.isSuccessful) {
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
                        Toast.makeText(this, "회원가입 성공! $nickname 님 환영합니다.", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        finishSignUp(Activity.RESULT_OK)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "DB 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                        user.delete().addOnCompleteListener {
                           showInProgress(false)
                        }
                    }
            } else {
                Toast.makeText(this, "닉네임 설정에 실패했습니다.", Toast.LENGTH_LONG).show()
                user.delete().addOnCompleteListener {
                    showInProgress(false)
                }
            }
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(TAG, "onVerificationCompleted:$credential")
            codeEditText.setText(credential.smsCode)
            Toast.makeText(applicationContext, "인증번호가 자동으로 확인되었습니다.", Toast.LENGTH_SHORT).show()
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "onVerificationFailed", e)
            Toast.makeText(applicationContext, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
            sendCodeButton.isEnabled = true
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d(TAG, "onCodeSent:$verificationId")
            storedVerificationId = verificationId
            resendToken = token
            sendCodeButton.isEnabled = true
            Toast.makeText(this@SignUpActivity, "인증번호를 보냈습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignUpFailure(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthUserCollisionException -> "이미 사용 중인 이메일입니다."
            is FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약합니다. 6자 이상으로 설정해주세요."
            is FirebaseAuthInvalidCredentialsException -> "이메일 형식이 올바르지 않습니다."
            else -> "회원가입 실패: ${exception?.message}"
        }
        Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun finishSignUp(resultCode: Int) {
        setResult(resultCode, Intent())
        finish()
    }
}