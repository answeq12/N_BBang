// adapter/ParticipantReviewAdapter.kt
package com.bergi.nbang_v1.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Participant

class ParticipantReviewAdapter(
    private val participants: List<Participant>,
    private val onReviewClick: (Participant) -> Unit
) : RecyclerView.Adapter<ParticipantReviewAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nickname: TextView = view.findViewById(R.id.textViewParticipantNickname)
        val reviewButton: Button = view.findViewById(R.id.buttonWriteReviewForParticipant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_participant_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val participant = participants[position]
        holder.nickname.text = participant.nickname
        holder.reviewButton.setOnClickListener { onReviewClick(participant) }
    }

    override fun getItemCount() = participants.size
}