package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI 요소들을 찾습니다.
        val nicknameTextView = view.findViewById<TextView>(R.id.textViewMyNickname)
        val profileCard = view.findViewById<View>(R.id.cardViewProfile)
        val myActivitiesCard = view.findViewById<View>(R.id.cardMyActivities)
        val certifyLocationButton = view.findViewById<Button>(R.id.buttonCertifyLocation)

        // 닉네임은 Firebase Auth의 기본 정보만 간단히 표시합니다.
        val user = Firebase.auth.currentUser
        nicknameTextView.text = user?.displayName ?: "닉네임 정보 없음"

        // 프로필 카드를 클릭했을 때의 동작
        profileCard.setOnClickListener {
            (activity as? MainActivity)?.navigateToProfileFragment()
        }

        // '내 활동' 카드를 클릭했을 때의 동작
        myActivitiesCard.setOnClickListener {
            val intent = Intent(requireContext(), MyActivitiesActivity::class.java)
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 동네 인증 버튼을 클릭했을 때의 동작
        certifyLocationButton.setOnClickListener {
            val intent = Intent(requireContext(), VerifyLocationActivity::class.java)
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
