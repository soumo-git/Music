package com.android.music.browse.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.databinding.ItemShortBinding
import com.bumptech.glide.Glide

class ShortsAdapter(
    private val onShortClick: (YouTubeVideo) -> Unit
) : ListAdapter<YouTubeVideo, ShortsAdapter.ShortViewHolder>(ShortDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortViewHolder {
        val binding = ItemShortBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ShortViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShortViewHolder(
        private val binding: ItemShortBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: YouTubeVideo) {
            binding.apply {
                tvTitle.text = video.title
                tvDuration.text = video.duration
                tvChannel.text = video.channelName

                Glide.with(ivThumbnail)
                    .load(video.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivThumbnail)

                root.setOnClickListener { onShortClick(video) }
            }
        }
    }

    class ShortDiffCallback : DiffUtil.ItemCallback<YouTubeVideo>() {
        override fun areItemsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem == newItem
        }
    }
}
