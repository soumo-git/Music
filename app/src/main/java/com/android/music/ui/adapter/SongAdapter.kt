package com.android.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.data.model.Song
import com.android.music.databinding.ItemSongBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class SongAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onSongOptionClick: (Song, SongOption) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingSongId: Long? = null

    enum class SongOption {
        PLAY_LATER, ADD_TO_QUEUE, DELETE, SHARE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setCurrentPlayingSong(songId: Long?) {
        val oldId = currentPlayingSongId
        currentPlayingSongId = songId
        
        currentList.forEachIndexed { index, song ->
            if (song.id == oldId || song.id == songId) {
                notifyItemChanged(index)
            }
        }
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.apply {
                tvTitle.text = song.title
                tvSubtitle.text = song.subtitle

                // Load actual album art with gradient fallback
                com.android.music.util.AlbumArtUtil.loadAlbumArtWithFallback(
                    Glide.with(ivThumbnail),
                    ivThumbnail,
                    song,
                    48
                )

                // Show playing indicator with animation
                val isPlaying = song.id == currentPlayingSongId
                if (isPlaying) {
                    ivPlayingIndicator.visibility = View.VISIBLE
                    // Highlight the playing song
                    root.setBackgroundColor(0x1A8B5CF6)
                } else {
                    ivPlayingIndicator.visibility = View.GONE
                    root.setBackgroundColor(0x00000000)
                }

                // Click listeners
                root.setOnClickListener { onSongClick(song) }
                
                btnOptions.setOnClickListener { view ->
                    showPopupMenu(view, song)
                }
            }
        }

        private fun showPopupMenu(view: View, song: Song) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.menu_song_options, menu)
                
                setOnMenuItemClickListener { menuItem ->
                    val option = when (menuItem.itemId) {
                        R.id.action_play_later -> SongOption.PLAY_LATER
                        R.id.action_add_to_queue -> SongOption.ADD_TO_QUEUE
                        R.id.action_delete -> SongOption.DELETE
                        R.id.action_share -> SongOption.SHARE
                        else -> return@setOnMenuItemClickListener false
                    }
                    onSongOptionClick(song, option)
                    true
                }
                
                show()
            }
        }
    }

    private class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}
