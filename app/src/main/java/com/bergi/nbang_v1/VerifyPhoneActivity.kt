package com.bergi.nbang_v1

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore // Firestore import 추가
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class VerifyPhoneActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // DB 변수 추가

    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private val TAG = "VerifyPhoneActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_phone)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance() // DB 초기화 추가

        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }
        val phoneEditText = findViewById<EditText>(R.id.editTextPhoneNumber)
        val codeEditText = findViewById<EditText>(R.id.editTextVerificationCode)
        val sendCodeButton = findViewById<Button>(R.id.buttonSendCode)
        val verifyButton = findViewById<Button>(R.id.buttonVerify)
        sendCodeButton.setOnClickListener {
            var phoneNumberInput = phoneEditText.text.toString()
            if (phoneNumberInput.startsWith("0")) {
                phoneNumberInput = phoneNumberInput.substring(1)
            }
            val phoneNumber = "+82$phoneNumberInput"
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
            Toast.makeText(this, "인증번호를 보냈습니다.", Toast.LENGTH_SHORT).show()
        }
        verifyButton.setOnClickListener {
            val code = codeEditText.text.toString().trim()
            storedVerificationId?.let { verificationId ->
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                linkPhoneNumberToCurrentUser(currentUser, credential)
            }
        }
    }
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                linkPhoneNumberToCurrentUser(currentUser, credential)
            }
        }
        override fun onVerificationFailed(e: FirebaseException) {
            Toast.makeText(applicationContext, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            storedVerificationId = verificationId
            resendToken = token
        }
    }

    // ▼▼▼ Firestore 업데이트 로직이 추가된 함수 ▼▼▼
    private fun linkPhoneNumberToCurrentUser(user: FirebaseUser, credential: AuthCredential) {
        user.linkWithCredential(credential).addOnCompleteListener(this) { linkTask ->
            if (linkTask.isSuccessful) {
                // ▼▼▼ 핵심 해결책: 사용자 정보를 서버에서 강제로 다시 불러옵니다. ▼▼▼
                user.reload().addOnCompleteListener { reloadTask ->
                    if (reloadTask.isSuccessful) {
                        // 새로고침된 최신 사용자 정보에서 전화번호를 가져옵니다.
                        val updatedPhoneNumber = Firebase.auth.currentUser?.phoneNumber ?: ""

                        // 최신 전화번호를 Firestore DB에 업데이트합니다.
                        db.collection("users").document(user.uid)
                            .update("phoneNumber", updatedPhoneNumber)
                            .addOnSuccessListener {
                                Toast.makeText(this, "휴대폰 번호가 성공적으로 인증되었습니다.", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "DB 업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK) // 인증은 성공했으므로 일단 성공으로 처리
                                finish()
                            }
                    } else {
                        // 새로고침 실패 시
                        Toast.makeText(this, "사용자 정보 갱신에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                // ▲▲▲ 여기까지가 수정된 부분입니다. ▲▲▲
            } else {
                Toast.makeText(this, "인증에 실패했습니다: ${linkTask.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}