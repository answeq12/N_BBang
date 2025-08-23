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
    private lateinit var nicknameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var verifyPhoneButton: Button
    private lateinit var editProfileButton: Button
    private lateinit var logoutButton: Button

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

        setupListeners()
        return view
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 프로필 정보를 새로고침
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = Firebase.auth.currentUser ?: return

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
                    Log.w("ProfileFragment", "User document not found.")
                    nicknameTextView.text = user.displayName ?: "닉네임 정보 없음"
                    emailTextView.text = user.email ?: "이메일 정보 없음"
                    phoneTextView.text = "인증되지 않음"
                    verifyPhoneButton.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ProfileFragment", "Error getting user document", exception)
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
    }
}
