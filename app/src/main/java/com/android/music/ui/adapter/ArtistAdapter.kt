package com.android.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.data.model.Artist
import com.android.music.databinding.ItemArtistBinding

class ArtistAdapter(
    private val onArtistClick: (Artist) -> Unit,
    private val onArtistOptionClick: (Artist, ArtistOption) -> Unit = { _, _ -> },
    private val onSelectionChanged: (Set<Artist>) -> Unit = {}
) : ListAdapter<Artist, ArtistAdapter.ArtistViewHolder>(ArtistDiffCallback()) {

    enum class ArtistOption {
        SHARE
    }

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun isInSelectionMode() = isSelectionMode

    fun getSelectedArtists(): List<Artist> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(emptySet())
    }

    private fun toggleSelection(artist: Artist) {
        if (selectedItems.contains(artist.id)) {
            selectedItems.remove(artist.id)
        } else {
            selectedItems.add(artist.id)
        }
        
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        
        notifyDataSetChanged()
        onSelectionChanged(getSelectedArtists().toSet())
    }

    inner class ArtistViewHolder(
        private val binding: ItemArtistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(artist: Artist) {
            binding.apply {
                tvArtistName.text = artist.name
                tvSongCount.text = "${artist.songCount} songs"
                
                val isSelected = selectedItems.contains(artist.id)
                root.isActivated = isSelected
                root.setBackgroundColor(
                    if (isSelected) 0x1A8B5CF6 else 0x00000000
                )
                
                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(artist)
                    } else {
                        onArtistClick(artist)
                    }
                }
                
                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                    }
                    toggleSelection(artist)
                    true
                }
                
                btnOptions.setOnClickListener { view ->
                    if (isSelectionMode) {
                        toggleSelection(artist)
                    } else {
                        showPopupMenu(view, artist)
                    }
                }
            }
        }

        private fun showPopupMenu(view: View, artist: Artist) {
            PopupMenu(view.context, view).apply {
                menu.add(0, R.id.action_share, 0, R.string.share)
                    .setIcon(R.drawable.ic_share)
                
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_share -> {
                            onArtistOptionClick(artist, ArtistOption.SHARE)
                            true
                        }
                        else -> false
                    }
                }
                
                show()
            }
        }
    }

    private class ArtistDiffCallback : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean {
            return oldItem == newItem
        }
    }
}
