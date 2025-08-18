package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var nicknameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmSignUpButton: Button

    private val TAG = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = Firebase.auth

        emailEditText = findViewById(R.id.editTextSignUpEmail)
        nicknameEditText = findViewById(R.id.editTextSignUpNickname)
        passwordEditText = findViewById(R.id.editTextSignUpPassword)
        confirmSignUpButton = findViewById(R.id.buttonConfirmSignUp)

        confirmSignUpButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val nickname = nicknameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || nickname.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createUserAccount(email, password, nickname)
        }
    }

    private fun createUserAccount(email: String, pass: String, nickname: String) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    val user = auth.currentUser
                    user?.let {
                        updateUserProfile(it, nickname)
                    } ?: run {
                        // 일반적으로 이 경우는 거의 없지만, 방어 코드
                        Log.w(TAG, "createUser:user is null, though successful")
                        Toast.makeText(baseContext, "회원가입에 성공했으나, 프로필 업데이트에 실패했습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                        finishSignUp(Activity.RESULT_CANCELED) // 실패로 간주하고 로그인 화면으로 돌아감
                    }
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    var errorMessage = "회원가입 실패: ${task.exception?.message}"
                    if (task.exception?.message?.contains("email address is already in use") == true) {
                        errorMessage = "이미 사용 중인 이메일입니다."
                    }
                    Toast.makeText(baseContext, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateUserProfile(user: FirebaseUser, nickname: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(nickname)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { profileTask ->
                if (profileTask.isSuccessful) {
                    Log.d(TAG, "User profile updated with nickname: $nickname")
                    Toast.makeText(this, "회원가입 성공! ($nickname 님 환영합니다)", Toast.LENGTH_SHORT).show()
                    auth.signOut() // 회원가입 후 자동 로그인 상태를 해제하여 로그인 창에서 다시 로그인하도록 유도
                    finishSignUp(Activity.RESULT_OK) // 성공 결과와 함께 SignUpActivity 종료
                } else {
                    Log.w(TAG, "updateUserProfile:failure", profileTask.exception)
                    // 프로필 업데이트는 실패했지만 계정은 생성되었을 수 있음.
                    // 사용자가 다시 로그인하여 프로필을 업데이트하도록 유도하거나,
                    // 혹은 여기서 계정 삭제 후 다시 시도하도록 할 수도 있음.
                    // 현재는 일단 가입은 된 것으로 처리하고 로그인 화면으로 유도.
                    Toast.makeText(this, "계정은 생성되었으나 닉네임 설정에 실패했습니다. 로그인 후 프로필을 다시 설정해주세요.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    finishSignUp(Activity.RESULT_CANCELED) // 실패로 간주하고 로그인 화면으로 돌아감
                }
            }
    }

    private fun finishSignUp(resultCode: Int) {
        // 회원가입 완료 후 LoginActivity로 돌아가기 위해 Activity를 종료.
        // 필요하다면 성공 여부를 LoginActivity에 전달할 수 있음.
        // 예를 들어, 성공 시 이메일을 전달하여 LoginActivity의 이메일 필드에 자동 채우기 등.
        // 현재는 resultCode로 성공/실패만 구분.
        val resultIntent = Intent()
        // 예: resultIntent.putExtra("USER_EMAIL", emailEditText.text.toString())
        setResult(resultCode, resultIntent)
        finish()
    }

    // 뒤로가기 버튼 처리 (선택 사항)
    override fun onBackPressed() {
        super.onBackPressed()
        // SignUpActivity에서 뒤로가기 시 LoginActivity로 돌아감
        // 특별한 결과 없이 종료
        finishSignUp(Activity.RESULT_CANCELED)
    }
}
