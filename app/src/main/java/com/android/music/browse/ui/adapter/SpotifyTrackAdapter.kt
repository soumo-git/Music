package com.android.music.browse.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.browse.data.model.SpotifyTrack
import com.android.music.databinding.ItemSpotifyTrackBinding
import com.bumptech.glide.Glide

class SpotifyTrackAdapter(
    private val onTrackClick: (SpotifyTrack) -> Unit,
    private val onArtistClick: ((SpotifyTrack) -> Unit)? = null,
    private val onMoreClick: (SpotifyTrack, View) -> Unit = { _, _ -> }
) : ListAdapter<SpotifyTrack, SpotifyTrackAdapter.TrackViewHolder>(TrackDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemSpotifyTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackViewHolder(
        private val binding: ItemSpotifyTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: SpotifyTrack) {
            binding.apply {
                tvTrackName.text = track.name
                tvArtistAlbum.text = "${track.artistName} · ${track.albumName}"
                tvDuration.text = track.duration

                // Show explicit badge if needed
                ivExplicit.visibility = if (track.explicit) View.VISIBLE else View.GONE

                // Load album art
                Glide.with(ivAlbumArt)
                    .load(track.albumArtUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .centerCrop()
                    .into(ivAlbumArt)

                // Click listeners
                root.setOnClickListener { onTrackClick(track) }
                btnMore.setOnClickListener { onMoreClick(track, it) }
                
                // Artist click listener
                tvArtistAlbum.setOnClickListener {
                    onArtistClick?.invoke(track)
                }
            }
        }
    }

    class TrackDiffCallback : DiffUtil.ItemCallback<SpotifyTrack>() {
        override fun areItemsTheSame(oldItem: SpotifyTrack, newItem: SpotifyTrack): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SpotifyTrack, newItem: SpotifyTrack): Boolean {
            return oldItem == newItem
        }
    }
}
