package com.bergi.nbang_v1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.R
import com.bumptech.glide.Glide

class PhotoAdapter(private val photoUrls: List<String>) :
    RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        // [수정] 이미지 비율은 유지하고, 틀을 가득 채운 뒤 남는 부분을 잘라냅니다.
        holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        Glide.with(holder.itemView.context)
            .load(photoUrls[position])
            .into(holder.imageView)
    }

    override fun getItemCount(): Int {
        return photoUrls.size
    }
}