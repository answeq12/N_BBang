package com.bergi.nbang_v1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ChatFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // 임시로 텍스트만 보여주는 화면
        val textView = TextView(requireContext()).apply {
            text = "채팅 기능은 준비 중입니다."
            textSize = 20f
            gravity = android.view.Gravity.CENTER
        }
        return textView
    }
}
