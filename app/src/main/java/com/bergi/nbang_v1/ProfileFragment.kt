package com.bergi.nbang_v1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    // UI 요소
    private lateinit var nicknameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var verifyPhoneButton: Button
    private lateinit var editProfileButton: Button
    private lateinit var verifyLocationButton: Button // 동네 인증 버튼 추가
    private lateinit var logoutButton: Button

    // Activity 결과를 받기 위한 Launcher
    private val profileUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 정보 수정 또는 인증이 완료되면 프로필 정보를 새로고침
            loadUserProfile()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_profile, container, false)
        db = FirebaseFirestore.getInstance()

        // UI 요소 초기화
        nicknameTextView = view.findViewById(R.id.textViewProfileNickname)
        emailTextView = view.findViewById(R.id.textViewProfileEmail)
        phoneTextView = view.findViewById(R.id.textViewProfilePhone)
        locationTextView = view.findViewById(R.id.textViewProfileLocation) // 새로 추가된 UI
        verifyPhoneButton = view.findViewById(R.id.buttonVerifyPhone)
        editProfileButton = view.findViewById(R.id.buttonEditProfile)
        verifyLocationButton = view.findViewById(R.id.buttonVerifyLocation) // 새로 추가된 UI
        logoutButton = view.findViewById(R.id.buttonLogout)

        setupListeners()
        loadUserProfile()

        return view
    }

    private fun loadUserProfile() {
        val user = Firebase.auth.currentUser
        if (user == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 데이터가 성공적으로 로드된 경우 (기존 코드)
                    nicknameTextView.text = document.getString("nickname") ?: "닉네임 정보 없음"
                    emailTextView.text = document.getString("email") ?: "이메일 정보 없음"

                    val phoneNumber = document.getString("phoneNumber")
                    if (phoneNumber.isNullOrEmpty()) {
                        phoneTextView.text = "인증되지 않음"
                        verifyPhoneButton.visibility = View.VISIBLE
                    } else {
                        phoneTextView.text = phoneNumber
                        verifyPhoneButton.visibility = View.GONE
                    }

                    val location = document.getString("location")
                    if (location.isNullOrEmpty()) {
                        locationTextView.text = "인증되지 않음"
                        verifyLocationButton.visibility = View.VISIBLE
                    } else {
                        locationTextView.text = location
                        verifyLocationButton.visibility = View.GONE
                    }
                } else {
                    // ▼▼▼ 데이터 로드 실패 시 처리 (이 부분 추가) ▼▼▼
                    Log.w("ProfileFragment", "User document not found in Firestore for UID: ${user.uid}")
                    // Auth 정보로 최소한의 UI를 설정
                    nicknameTextView.text = user.displayName ?: "닉네임 정보 없음"
                    emailTextView.text = user.email ?: "이메일 정보 없음"
                    phoneTextView.text = "정보를 불러올 수 없습니다."
                    locationTextView.text = "정보를 불러올 수 없습니다."
                    // 인증 버튼들은 보이도록 처리
                    verifyPhoneButton.visibility = View.VISIBLE
                    verifyLocationButton.visibility = View.VISIBLE
                    // ▲▲▲ 여기까지 추가 ▲▲▲
                }
            }
            .addOnFailureListener { exception ->
                // DB 접근 자체를 실패한 경우
                Log.e("ProfileFragment", "Error getting user document", exception)
                nicknameTextView.text = "오류 발생"
                emailTextView.text = "데이터를 불러오지 못했습니다."
            }
    }
    private fun setupListeners() {
        // 정보 수정 버튼
        editProfileButton.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            profileUpdateLauncher.launch(intent)
        }

        // 휴대폰 인증 버튼
        verifyPhoneButton.setOnClickListener {
            val intent = Intent(requireContext(), VerifyPhoneActivity::class.java)
            profileUpdateLauncher.launch(intent)
        }

        // 동네 인증 버튼
        verifyLocationButton.setOnClickListener {
            // TODO: 5단계에서 생성할 VerifyLocationActivity를 실행합니다. -> 이 부분의 주석을 푸세요!
            val intent = Intent(requireContext(), VerifyLocationActivity::class.java)
            profileUpdateLauncher.launch(intent)
        }

        // 로그아웃 버튼
        logoutButton.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }
    }
}