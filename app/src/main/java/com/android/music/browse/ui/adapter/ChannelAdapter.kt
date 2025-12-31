package com.android.music.browse.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.browse.data.model.YouTubeChannel
import com.android.music.databinding.ItemChannelBinding
import com.bumptech.glide.Glide

class ChannelAdapter(
    private val onChannelClick: (YouTubeChannel) -> Unit
) : ListAdapter<YouTubeChannel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onChannelClick(getItem(position))
                }
            }
        }

        fun bind(channel: YouTubeChannel) {
            binding.apply {
                tvChannelName.text = channel.name
                tvSubscriberCount.text = channel.subscriberCount?.let { "$it subscribers" } ?: ""

                Glide.with(ivChannelAvatar)
                    .load(channel.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .circleCrop()
                    .into(ivChannelAvatar)
            }
        }
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<YouTubeChannel>() {
        override fun areItemsTheSame(oldItem: YouTubeChannel, newItem: YouTubeChannel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: YouTubeChannel, newItem: YouTubeChannel): Boolean {
            return oldItem == newItem
        }
    }
}
