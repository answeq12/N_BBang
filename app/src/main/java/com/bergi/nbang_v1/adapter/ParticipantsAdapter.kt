package com.bergi.nbang_v1.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.UserProfileActivity // ✅ 추가: UserProfileActivity 임포트
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class ParticipantsAdapter(private val participantUids: List<String>) :
    RecyclerView.Adapter<ParticipantsAdapter.ParticipantViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()

    inner class ParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nicknameTextView: TextView = itemView.findViewById(R.id.textViewNickname)
        val profileImageView: ImageView = itemView.findViewById(R.id.imageViewProfilePic)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val uid = participantUids[position]
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "익명"
                holder.nicknameTextView.text = nickname
                // TODO: 프로필 이미지 로드 로직 추가 (Glide 등)
            }

        // ✅ 추가: 프로필 클릭 리스너
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, UserProfileActivity::class.java)
            intent.putExtra("USER_ID", uid)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return participantUids.size
    }
}
