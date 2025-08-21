// VerifyPhoneActivity.kt 파일 전체를 이 코드로 교체하세요.

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
        user.linkWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // 인증 성공 후, Firestore DB에 전화번호 업데이트
                val updatedPhoneNumber = user.phoneNumber ?: ""
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
                Toast.makeText(this, "인증에 실패했습니다: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}