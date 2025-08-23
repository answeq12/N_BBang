package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var nicknameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var verifyPhoneButton: Button
    private lateinit var editProfileButton: Button
    private lateinit var logoutButton: Button
    private lateinit var deleteAccountButton: Button

    private val profileUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadUserProfile()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_profile, container, false)
        db = FirebaseFirestore.getInstance()

        nicknameTextView = view.findViewById(R.id.textViewProfileNickname)
        emailTextView = view.findViewById(R.id.textViewProfileEmail)
        phoneTextView = view.findViewById(R.id.textViewProfilePhone)
        verifyPhoneButton = view.findViewById(R.id.buttonVerifyPhone)
        editProfileButton = view.findViewById(R.id.buttonEditProfile)
        logoutButton = view.findViewById(R.id.buttonLogout)
        deleteAccountButton = view.findViewById(R.id.buttonDeleteAccount)

        setupListeners()
        return view
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = Firebase.auth.currentUser
        if (user == null) {
            activity?.finish()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    nicknameTextView.text = document.getString("nickname") ?: "닉네임 정보 없음"
                    emailTextView.text = user.email ?: "이메일 정보 없음"

                    val phoneNumber = document.getString("phoneNumber")
                    if (phoneNumber.isNullOrEmpty()) {
                        phoneTextView.text = "인증되지 않음"
                        verifyPhoneButton.visibility = View.VISIBLE
                    } else {
                        phoneTextView.text = phoneNumber
                        verifyPhoneButton.visibility = View.GONE
                    }
                } else {
                    Log.w("ProfileFragment", "User document not found for UID: ${user.uid}")
                    nicknameTextView.text = user.displayName ?: "닉네임 정보 없음"
                    emailTextView.text = user.email ?: "이메일 정보 없음"
                    phoneTextView.text = "인증되지 않음"
                    verifyPhoneButton.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileFragment", "Error getting user document", exception)
                nicknameTextView.text = "오류 발생"
                emailTextView.text = "데이터를 불러오지 못했습니다."
            }
    }

    private fun setupListeners() {
        editProfileButton.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            profileUpdateLauncher.launch(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        verifyPhoneButton.setOnClickListener {
            val intent = Intent(requireContext(), VerifyPhoneActivity::class.java)
            profileUpdateLauncher.launch(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        logoutButton.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }

        deleteAccountButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("계정 탈퇴")
            .setMessage("정말로 계정을 탈퇴하시겠습니까?\n모든 정보가 영구적으로 삭제되며, 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("탈퇴") { _, _ ->
                // --- 비밀번호 확인 절차를 추가합니다 ---
                promptForPassword()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // --- 비밀번호 입력을 받는 다이얼로그 함수 ---
    private fun promptForPassword() {
        val user = Firebase.auth.currentUser ?: return
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("본인 확인")
        builder.setMessage("계정을 삭제하려면 비밀번호를 다시 입력해주세요.")

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("확인") { _, _ ->
            val password = input.text.toString()
            if (password.isEmpty()) {
                Toast.makeText(context, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            // 비밀번호로 재인증 시도
            val credential = EmailAuthProvider.getCredential(user.email!!, password)
            user.reauthenticate(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // 재인증 성공 시, 계정 삭제 절차 진행
                        deleteUserAccount()
                    } else {
                        Toast.makeText(context, "비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    private fun deleteUserAccount() {
        val user = Firebase.auth.currentUser ?: return

        db.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                Log.d("ProfileFragment", "Firestore user data deleted successfully.")
                user.delete()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(context, "계정이 성공적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            activity?.finish()
                        } else {
                            Toast.makeText(context, "계정 삭제에 실패했습니다.", Toast.LENGTH_LONG).show()
                            Log.w("ProfileFragment", "Failed to delete user account.", task.exception)
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "사용자 정보 삭제에 실패했습니다.", Toast.LENGTH_LONG).show()
                Log.w("ProfileFragment", "Failed to delete user data from Firestore.", e)
            }
    }
}
