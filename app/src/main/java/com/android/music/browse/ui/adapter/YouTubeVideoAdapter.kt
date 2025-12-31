package com.android.music.browse.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.browse.data.model.YouTubeVideo
import com.android.music.databinding.ItemYoutubeVideoBinding
import com.android.music.download.manager.DownloadStateManager
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class YouTubeVideoAdapter(
    private val onVideoClick: (YouTubeVideo) -> Unit,
    private val onChannelClick: ((YouTubeVideo) -> Unit)? = null,
    private val onMoreClick: (YouTubeVideo, View) -> Unit = { _, _ -> },
    private val onDownloadClick: (YouTubeVideo, View) -> Unit = { _, _ -> }
) : ListAdapter<YouTubeVideo, YouTubeVideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemYoutubeVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelStateObserver()
    }

    inner class VideoViewHolder(
        private val binding: ItemYoutubeVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var stateJob: Job? = null

        fun bind(video: YouTubeVideo) {
            binding.apply {
                tvTitle.text = video.title
                tvDuration.text = video.duration
                tvChannelInfo.text = "${video.channelName} · ${video.viewCount} · ${video.publishedAt}"

                // Load thumbnail
                Glide.with(ivThumbnail)
                    .load(video.thumbnailUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivThumbnail)

                // Load channel avatar
                video.channelThumbnail?.let { url ->
                    Glide.with(ivChannelAvatar)
                        .load(url)
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(ivChannelAvatar)
                }

                // Click listeners
                root.setOnClickListener { onVideoClick(video) }
                btnMore.setOnClickListener { onMoreClick(video, it) }
                btnDownload.setOnClickListener { onDownloadClick(video, it) }
                
                // Channel click listener
                ivChannelAvatar.setOnClickListener { 
                    onChannelClick?.invoke(video) 
                }
                tvChannelInfo.setOnClickListener {
                    onChannelClick?.invoke(video)
                }
                
                // Observe download state for this video
                observeDownloadState(video.id)
            }
        }
        
        private fun observeDownloadState(videoId: String) {
            stateJob?.cancel()
            stateJob = scope.launch {
                DownloadStateManager.downloadStates.collectLatest { states ->
                    val info = states[videoId] ?: DownloadStateManager.DownloadInfo()
                    updateDownloadUI(info)
                }
            }
        }
        
        private fun updateDownloadUI(info: DownloadStateManager.DownloadInfo) {
            binding.apply {
                when (info.state) {
                    DownloadStateManager.DownloadState.EXTRACTING -> {
                        btnDownload.visibility = View.INVISIBLE
                        progressExtracting.visibility = View.VISIBLE
                        progressDownloading.visibility = View.GONE
                    }
                    DownloadStateManager.DownloadState.DOWNLOADING -> {
                        btnDownload.visibility = View.INVISIBLE
                        progressExtracting.visibility = View.GONE
                        progressDownloading.visibility = View.VISIBLE
                        progressDownloading.progress = info.progress
                    }
                    DownloadStateManager.DownloadState.COMPLETED -> {
                        btnDownload.visibility = View.VISIBLE
                        progressExtracting.visibility = View.GONE
                        progressDownloading.visibility = View.GONE
                        // Could change icon to checkmark here
                    }
                    else -> {
                        btnDownload.visibility = View.VISIBLE
                        progressExtracting.visibility = View.GONE
                        progressDownloading.visibility = View.GONE
                    }
                }
            }
        }
        
        fun cancelStateObserver() {
            stateJob?.cancel()
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<YouTubeVideo>() {
        override fun areItemsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem == newItem
        }
    }
}
