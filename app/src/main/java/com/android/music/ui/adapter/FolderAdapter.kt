package com.android.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.data.model.Folder
import com.android.music.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onFolderOptionClick: (Folder, FolderOption) -> Unit = { _, _ -> },
    private val onSelectionChanged: (Set<Folder>) -> Unit = {}
) : ListAdapter<Folder, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    enum class FolderOption {
        SHARE
    }

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun isInSelectionMode() = isSelectionMode

    fun getSelectedFolders(): List<Folder> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(emptySet())
    }

    private fun toggleSelection(folder: Folder) {
        if (selectedItems.contains(folder.id)) {
            selectedItems.remove(folder.id)
        } else {
            selectedItems.add(folder.id)
        }
        
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
        }
        
        notifyDataSetChanged()
        onSelectionChanged(getSelectedFolders().toSet())
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: Folder) {
            binding.apply {
                tvFolderName.text = folder.name
                val itemCount = buildString {
                    if (folder.songCount > 0) append("${folder.songCount} songs")
                    if (folder.videoCount > 0) {
                        if (folder.songCount > 0) append(" • ")
                        append("${folder.videoCount} videos")
                    }
                }
                tvItemCount.text = itemCount.ifEmpty { "Empty" }
                
                val isSelected = selectedItems.contains(folder.id)
                root.isActivated = isSelected
                root.setBackgroundColor(
                    if (isSelected) 0x1A8B5CF6 else 0x00000000
                )
                
                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(folder)
                    } else {
                        onFolderClick(folder)
                    }
                }
                
                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                    }
                    toggleSelection(folder)
                    true
                }
                
                btnOptions.setOnClickListener { view ->
                    if (isSelectionMode) {
                        toggleSelection(folder)
                    } else {
                        showPopupMenu(view, folder)
                    }
                }
            }
        }

        private fun showPopupMenu(view: View, folder: Folder) {
            PopupMenu(view.context, view).apply {
                menu.add(0, R.id.action_share, 0, R.string.share)
                    .setIcon(R.drawable.ic_share)
                
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_share -> {
                            onFolderOptionClick(folder, FolderOption.SHARE)
                            true
                        }
                        else -> false
                    }
                }
                
                show()
            }
        }
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Folder, newItem: Folder): Boolean {
            return oldItem == newItem
        }
    }
}
