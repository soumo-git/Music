package com.android.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.data.model.Album
import com.android.music.databinding.ItemAlbumBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit,
    private val onAlbumOptionClick: (Album, AlbumOption) -> Unit = { _, _ -> },
    private val onSelectionChanged: (Set<Album>) -> Unit = {}
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    enum class AlbumOption {
        SHARE
    }

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun isInSelectionMode() = isSelectionMode

    fun getSelectedAlbums(): List<Album> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(emptySet())
    }

    private fun toggleSelection(album: Album) {
        if (selectedItems.contains(album.id)) {
            selectedItems.remove(album.id)
        } else {
            selectedItems.add(album.id)
        }
        
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        
        notifyDataSetChanged()
        onSelectionChanged(getSelectedAlbums().toSet())
    }

    inner class AlbumViewHolder(
        private val binding: ItemAlbumBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(album: Album) {
            binding.apply {
                tvAlbumTitle.text = album.title
                tvAlbumArtist.text = "${album.artist} • ${album.songCount} songs"

                Glide.with(ivAlbumArt)
                    .load(album.albumArtUri)
                    .signature(com.bumptech.glide.signature.ObjectKey(album.id.toString()))
                    .placeholder(R.drawable.ic_album)
                    .error(R.drawable.ic_album)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(ivAlbumArt)

                val isSelected = selectedItems.contains(album.id)
                root.isActivated = isSelected
                root.setBackgroundColor(
                    if (isSelected) 0x1A8B5CF6 else 0x00000000
                )

                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(album)
                    } else {
                        onAlbumClick(album)
                    }
                }
                
                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                    }
                    toggleSelection(album)
                    true
                }
                
                btnOptions.setOnClickListener { view ->
                    if (isSelectionMode) {
                        toggleSelection(album)
                    } else {
                        showPopupMenu(view, album)
                    }
                }
            }
        }

        private fun showPopupMenu(view: View, album: Album) {
            PopupMenu(view.context, view).apply {
                menu.add(0, R.id.action_share, 0, R.string.share)
                    .setIcon(R.drawable.ic_share)
                
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_share -> {
                            onAlbumOptionClick(album, AlbumOption.SHARE)
                            true
                        }
                        else -> false
                    }
                }
                
                show()
            }
        }
    }

    private class AlbumDiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem == newItem
        }
    }
}
