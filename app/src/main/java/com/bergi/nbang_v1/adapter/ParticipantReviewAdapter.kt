package com.bergi.nbang_v1.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Participant
import com.google.android.material.button.MaterialButton

class ParticipantReviewAdapter(
    private val participants: List<Participant>,
    private val alreadyReviewedUids: List<String>,
    private val onParticipantClick: (Participant) -> Unit
) : RecyclerView.Adapter<ParticipantReviewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants[position]
        holder.bind(participant)
    }

    override fun getItemCount(): Int = participants.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nicknameTextView: TextView = itemView.findViewById(R.id.textViewParticipantNickname)
        // XML에서 Button 타입이 com.google.android.material.button.MaterialButton 이므로 캐스팅 타입 변경
        private val reviewButton: MaterialButton = itemView.findViewById(R.id.buttonWriteReviewForParticipant)

        fun bind(participant: Participant) {
            nicknameTextView.text = participant.nickname
            if (participant.isCreator) {
                nicknameTextView.append(" (방장)")
            }

            if (alreadyReviewedUids.contains(participant.uid)) {
                reviewButton.text = "리뷰 완료"
                reviewButton.isEnabled = false
                reviewButton.alpha = 0.5f
            } else {
                reviewButton.text = "후기 남기기" // XML에 맞춰 버튼 텍스트 변경
                reviewButton.isEnabled = true
                reviewButton.alpha = 1.0f
            }

            reviewButton.setOnClickListener {
                if (reviewButton.isEnabled) {
                    onParticipantClick(participant)
                }
            }
        }
    }
}

