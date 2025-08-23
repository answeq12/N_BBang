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
        // fragment_my.xml 레이아웃을 화면에 표시합니다.
        return inflater.inflate(R.layout.fragment_my, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI 요소들을 찾습니다.
        val nicknameTextView = view.findViewById<TextView>(R.id.textViewMyNickname)
        val profileCard = view.findViewById<View>(R.id.cardViewProfile)
        val certifyLocationButton = view.findViewById<Button>(R.id.buttonCertifyLocation)

        // 닉네임은 Firestore가 아닌 Firebase Auth의 기본 정보만 간단히 표시합니다.
        val user = Firebase.auth.currentUser
        nicknameTextView.text = user?.displayName ?: "닉네임 정보 없음"

        // 프로필 카드를 클릭했을 때의 동작
        profileCard.setOnClickListener {
            (activity as? MainActivity)?.navigateToProfileFragment()
        }

        // 동네 인증 버튼을 클릭했을 때의 동작
        certifyLocationButton.setOnClickListener {
            val intent = Intent(requireContext(), VerifyLocationActivity::class.java)
            startActivity(intent)
            // 화면 전환 애니메이션 적용
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
