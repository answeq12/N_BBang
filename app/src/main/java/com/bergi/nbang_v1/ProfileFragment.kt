package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_profile, container, false) // 기존 레이아웃 재사용
        val logoutButton = view.findViewById<Button>(R.id.buttonLogout)
        val nicknameTextView = view.findViewById<TextView>(R.id.textViewProfileNickname)
        val emailTextView = view.findViewById<TextView>(R.id.textViewProfileEmail)

        val user = Firebase.auth.currentUser
        nicknameTextView.text = user?.displayName ?: "닉네임 없음"
        emailTextView.text = user?.email ?: "이메일 없음"

        logoutButton.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }
        return view
    }
}