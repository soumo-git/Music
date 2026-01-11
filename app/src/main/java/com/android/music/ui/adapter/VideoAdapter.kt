package com.android.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.data.model.Video
import com.android.music.databinding.ItemVideoBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class VideoAdapter(
    private val onVideoClick: (Video) -> Unit,
    private val onVideoOptionClick: (Video, VideoOption) -> Unit = { _, _ -> }
) : ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    enum class VideoOption {
        SHARE, DELETE, INFO
    }

    private var currentPlayingVideoId: Long = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setCurrentPlayingVideo(videoId: Long) {
        val oldId = currentPlayingVideoId
        currentPlayingVideoId = videoId
        
        // Refresh old and new playing items
        currentList.forEachIndexed { index, video ->
            if (video.id == oldId || video.id == videoId) {
                notifyItemChanged(index)
            }
        }
    }

    inner class VideoViewHolder(
        private val binding: ItemVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.apply {
                tvTitle.text = video.title
                tvDuration.text = video.formattedDuration

                // Highlight currently playing video
                val isPlaying = video.id == currentPlayingVideoId
                root.alpha = if (isPlaying) 1f else 0.9f
                
                Glide.with(ivThumbnail)
                    .load(video.thumbnailUri)
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(ivThumbnail)

                root.setOnClickListener { onVideoClick(video) }
                
                // Hide download button
                btnDownload.visibility = View.GONE
                
                // Setup options menu
                btnOptions.setOnClickListener { view ->
                    showPopupMenu(view, video)
                }
            }
        }

        private fun showPopupMenu(view: View, video: Video) {
            PopupMenu(view.context, view).apply {
                menu.add(0, R.id.action_share, 0, R.string.video_share)
                    .setIcon(R.drawable.ic_share)
                menu.add(0, R.id.action_delete, 1, R.string.video_delete)
                    .setIcon(R.drawable.ic_delete)
                menu.add(0, R.id.action_info, 2, R.string.video_info)
                    .setIcon(R.drawable.ic_error)
                
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_share -> {
                            onVideoOptionClick(video, VideoOption.SHARE)
                            true
                        }
                        R.id.action_delete -> {
                            onVideoOptionClick(video, VideoOption.DELETE)
                            true
                        }
                        R.id.action_info -> {
                            onVideoOptionClick(video, VideoOption.INFO)
                            true
                        }
                        else -> false
                    }
                }
                
                show()
            }
        }
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem == newItem
        }
    }
}
