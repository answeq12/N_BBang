package com.bergi.nbang_v1.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bergi.nbang_v1.data.Participant

class ParticipantAdapter(
    private val participants: List<Participant>,
    private val onItemClick: (Participant) -> Unit
) : RecyclerView.Adapter<ParticipantAdapter.ParticipantViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = participants[position]
        holder.bind(participant)
        holder.itemView.setOnClickListener { onItemClick(participant) }
    }

    override fun getItemCount(): Int = participants.size

    class ParticipantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val crownIcon: ImageView = itemView.findViewById(R.id.imageViewCrown)
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewParticipantName)

        fun bind(participant: Participant) {
            nameTextView.text = participant.nickname
            crownIcon.visibility = if (participant.isCreator) View.VISIBLE else View.GONE
        }
    }
}
