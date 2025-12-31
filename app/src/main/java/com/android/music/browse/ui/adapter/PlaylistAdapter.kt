package com.android.music.browse.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.browse.data.model.YouTubePlaylist
import com.android.music.databinding.ItemPlaylistBinding
import com.bumptech.glide.Glide

class PlaylistAdapter(
    private val onPlaylistClick: (YouTubePlaylist) -> Unit
) : ListAdapter<YouTubePlaylist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(
        private val binding: ItemPlaylistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlaylistClick(getItem(position))
                }
            }
        }

        fun bind(playlist: YouTubePlaylist) {
            binding.apply {
                tvPlaylistTitle.text = playlist.title
                tvVideoCount.text = playlist.videoCount.toString()
                tvPlaylistInfo.text = "${playlist.videoCount} videos"

                Glide.with(ivPlaylistThumbnail)
                    .load(playlist.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivPlaylistThumbnail)
            }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<YouTubePlaylist>() {
        override fun areItemsTheSame(oldItem: YouTubePlaylist, newItem: YouTubePlaylist): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: YouTubePlaylist, newItem: YouTubePlaylist): Boolean {
            return oldItem == newItem
        }
    }
}
