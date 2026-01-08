package com.android.music.download.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.databinding.ItemDownloadBinding
import com.android.music.download.data.model.DownloadItem
import com.android.music.download.data.model.DownloadStatus
import com.bumptech.glide.Glide

/**
 * Adapter for displaying download items in a RecyclerView
 */
class DownloadsAdapter(
    private val onPauseClick: (DownloadItem) -> Unit,
    private val onResumeClick: (DownloadItem) -> Unit,
    private val onCancelClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit,
    private val onFolderClick: (DownloadItem) -> Unit = {},
    private val onItemClick: (DownloadItem) -> Unit = {}
) : ListAdapter<DownloadItem, DownloadsAdapter.DownloadViewHolder>(DownloadDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(
        private val binding: ItemDownloadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            binding.tvTitle.text = item.title
            binding.tvAuthor.text = item.author ?: item.platform
            binding.tvPlatform.text = item.platform
            
            // Handle folder vs file display
            if (item.isFolder) {
                // Show folder icon
                binding.ivThumbnail.setImageResource(R.drawable.ic_folder)
                binding.tvDuration.visibility = View.GONE
                binding.tvStatus.text = "${item.itemCount} items"
                binding.tvStatus.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.playlistProgressContainer.visibility = View.GONE
                binding.regularProgressContainer.visibility = View.VISIBLE
                
                // Hide all action buttons for folders
                hideAllButtons()
                
                // Set click listener for folder
                binding.root.setOnClickListener { onFolderClick(item) }
            } else {
                // Load thumbnail for files
                if (item.thumbnailUrl != null) {
                    Glide.with(binding.ivThumbnail)
                        .load(item.thumbnailUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .centerCrop()
                        .into(binding.ivThumbnail)
                } else {
                    binding.ivThumbnail.setImageResource(R.drawable.ic_music_note)
                }
                
                // Duration
                if (item.duration != null) {
                    binding.tvDuration.text = item.duration
                    binding.tvDuration.visibility = View.VISIBLE
                } else {
                    binding.tvDuration.visibility = View.GONE
                }
                
                // Handle playlist progress display
                val showPlaylistProgress = item.isPlaylist && item.totalItems > 0 && 
                    item.status == DownloadStatus.DOWNLOADING
                
                if (showPlaylistProgress) {
                    binding.playlistProgressContainer.visibility = View.VISIBLE
                    binding.regularProgressContainer.visibility = View.GONE
                    
                    // Update playlist progress UI
                    val completed = item.completedItems
                    val total = item.totalItems
                    val progressPercent = if (total > 0) (completed * 100) / total else 0
                    
                    binding.tvPlaylistProgress.text = "$completed of $total songs"
                    binding.tvPlaylistPercent.text = "${item.progress}%"
                    binding.playlistProgressBar.progress = item.progress
                    
                    // Current item being downloaded
                    if (item.currentItemTitle != null) {
                        binding.tvCurrentItem.text = "♪ ${item.currentItemTitle}"
                        binding.tvCurrentItem.visibility = View.VISIBLE
                    } else {
                        binding.tvCurrentItem.visibility = View.GONE
                    }
                    
                    showDownloadingButtons()
                } else {
                    binding.playlistProgressContainer.visibility = View.GONE
                    binding.regularProgressContainer.visibility = View.VISIBLE
                    
                    // Progress and status
                    when (item.status) {
                        DownloadStatus.PENDING -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = "Pending"
                            binding.tvStatus.visibility = View.VISIBLE
                            showPendingButtons()
                        }
                        DownloadStatus.EXTRACTING -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.progressBar.isIndeterminate = true
                            binding.tvStatus.text = "Extracting..."
                            binding.tvStatus.visibility = View.VISIBLE
                            hideAllButtons()
                        }
                        DownloadStatus.DOWNLOADING -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.progressBar.isIndeterminate = false
                            binding.progressBar.progress = item.progress
                            binding.tvStatus.text = "${item.progress}%"
                            binding.tvStatus.visibility = View.VISIBLE
                            showDownloadingButtons()
                        }
                        DownloadStatus.PAUSED -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.progressBar.isIndeterminate = false
                            binding.progressBar.progress = item.progress
                            binding.tvStatus.text = "Paused - ${item.progress}%"
                            binding.tvStatus.visibility = View.VISIBLE
                            showPausedButtons()
                        }
                        DownloadStatus.COMPLETED -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = "Completed"
                            binding.tvStatus.visibility = View.VISIBLE
                            showCompletedButtons()
                        }
                        DownloadStatus.FAILED -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = item.errorMessage ?: "Failed"
                            binding.tvStatus.visibility = View.VISIBLE
                            showFailedButtons()
                        }
                        DownloadStatus.CANCELLED -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvStatus.text = "Cancelled"
                            binding.tvStatus.visibility = View.VISIBLE
                            showCancelledButtons()
                        }
                    }
                }
                
                // Set click listener for file
                binding.root.setOnClickListener { onItemClick(item) }
            }
            
            // File size
            if (item.fileSize != null && item.fileSize > 0) {
                binding.tvFileSize.text = formatFileSize(item.fileSize)
                binding.tvFileSize.visibility = View.VISIBLE
            } else {
                binding.tvFileSize.visibility = View.GONE
            }
            
            // Button click listeners
            binding.btnPause.setOnClickListener { onPauseClick(item) }
            binding.btnResume.setOnClickListener { onResumeClick(item) }
            binding.btnCancel.setOnClickListener { onCancelClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
        
        private fun hideAllButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnDelete.visibility = View.GONE
        }
        
        private fun showPendingButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.GONE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.GONE
        }
        
        private fun showDownloadingButtons() {
            binding.btnPause.visibility = View.VISIBLE
            binding.btnResume.visibility = View.GONE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.GONE
        }
        
        private fun showPausedButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.VISIBLE
            binding.btnDelete.visibility = View.GONE
        }
        
        private fun showCompletedButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.btnDelete.visibility = View.VISIBLE
        }
        
        private fun showFailedButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.GONE
            binding.btnDelete.visibility = View.VISIBLE
        }
        
        private fun showCancelledButtons() {
            binding.btnPause.visibility = View.GONE
            binding.btnResume.visibility = View.VISIBLE
            binding.btnCancel.visibility = View.GONE
            binding.btnDelete.visibility = View.VISIBLE
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
    }

    class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem == newItem
        }
    }
}
