package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KeywordAdapter(
    private val keywords: List<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<KeywordAdapter.KeywordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keyword, parent, false)
        return KeywordViewHolder(view)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        val keyword = keywords[position]
        holder.bind(keyword)
    }

    override fun getItemCount(): Int = keywords.size

    inner class KeywordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewKeyword: TextView = itemView.findViewById(R.id.textViewKeyword)
        private val buttonDeleteKeyword: ImageButton = itemView.findViewById(R.id.buttonDeleteKeyword)

        fun bind(keyword: String) {
            textViewKeyword.text = keyword
            buttonDeleteKeyword.setOnClickListener {
                onDeleteClick(keyword)
            }
        }
    }
}
