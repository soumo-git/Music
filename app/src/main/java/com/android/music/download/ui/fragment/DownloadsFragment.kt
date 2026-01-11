package com.android.music.download.ui.fragment

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.databinding.FragmentDownloadsBinding
import com.android.music.databinding.ItemExtractedContentBinding
import com.android.music.download.data.model.DownloadStatus
import com.android.music.download.data.model.ExtractedContent
import com.android.music.download.ui.adapter.DownloadsAdapter
import com.android.music.download.ui.viewmodel.DownloadsViewModel
import com.android.music.settings.SettingsActivity
import com.android.music.videoplayer.ui.VideoPlayerActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

/**
 * Fragment for managing downloads from various platforms.
 * Allows users to paste links, extract content, and download media.
 */
class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    
    private var _extractedBinding: ItemExtractedContentBinding? = null
    private var hasExtractedContent: Boolean = false
    private var currentExtractedContent: ExtractedContent? = null
    
    private val viewModel: DownloadsViewModel by activityViewModels()
    private lateinit var downloadsAdapter: DownloadsAdapter
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(requireContext(), "Storage permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Storage permissions required for downloads", Toast.LENGTH_LONG).show()
        }
    }
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(requireContext(), "Storage access granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Storage access required for downloads", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())
        checkAndRequestStoragePermissions()
        setupUI()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupUI() {
        // Setup folder header back button
        binding.folderHeader.visibility = View.GONE
        binding.btnBack.setOnClickListener {
            handleBackPress()
        }
        
        // Setup tabs: Extracts, Downloading, Downloaded
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Extracts"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Downloading"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Downloaded"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showExtracts()
                    1 -> showDownloadingItems()
                    2 -> showDownloadedItems()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to Extracts tab
        binding.tabLayout.getTabAt(0)?.select()
        showExtracts()

        // Extract button click
        binding.btnExtract.setOnClickListener {
            extractLink()
        }

        // Handle IME action on EditText
        binding.etLinkInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                extractLink()
                true
            } else {
                false
            }
        }

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.reloadFromStorage()
            when (binding.tabLayout.selectedTabPosition) {
                0 -> showExtracts()
                1 -> showDownloadingItems()
                2 -> showDownloadedItems()
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }
    
    private fun showExtracts() {
        if (hasExtractedContent) {
            binding.extractedContentContainer.visibility = View.VISIBLE
            binding.rvDownloads.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
        } else {
            binding.extractedContentContainer.visibility = View.GONE
            binding.rvDownloads.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        }
    }
    
    private fun showDownloadingItems() {
        val downloads = viewModel.downloads.value ?: emptyList()
        val downloading = downloads.filter { download ->
            download.status != DownloadStatus.COMPLETED
        }
        
        if (downloading.isEmpty()) {
            binding.rvDownloads.visibility = View.GONE
            binding.emptyState.visibility =
                if (binding.extractedContentContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.extractedContentContainer.visibility = View.GONE
            binding.rvDownloads.visibility = View.VISIBLE
            downloadsAdapter.submitList(downloading)
        }
    }
    
    private fun showDownloadedItems() {
        binding.extractedContentContainer.visibility = View.GONE

        val downloads = viewModel.downloads.value ?: emptyList()
        val downloaded = downloads.filter {
            it.status == DownloadStatus.COMPLETED
        }

        if (downloaded.isEmpty()) {
            binding.rvDownloads.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvDownloads.visibility = View.VISIBLE
            downloadsAdapter.submitList(downloaded)
        }
    }
    
    private fun showFolderContents(items: List<com.android.music.download.data.model.DownloadItem>) {
        binding.extractedContentContainer.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.rvDownloads.visibility = View.VISIBLE
        downloadsAdapter.submitList(items)
    }
    
    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs access to storage to save downloaded files to your Downloads folder.")
                    .setPositiveButton("Grant") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        manageStorageLauncher.launch(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(arrayOf(permission))
            }
        }
    }

    private fun setupRecyclerView() {
        downloadsAdapter = DownloadsAdapter(
            onPauseClick = { viewModel.pauseDownload(it.id) },
            onResumeClick = { viewModel.resumeDownload(it.id) },
            onCancelClick = { viewModel.cancelDownload(it.id) },
            onDeleteClick = { item -> showDeleteConfirmation(item) },
            onFolderClick = { folder ->
                // Navigate into the folder
                viewModel.openFolder(folder.filePath ?: folder.url)
                updateFolderHeader(folder.title)
            },
            onItemClick = { item ->
                // Play the file if it's a media file
                if (!item.isFolder && item.filePath != null) {
                    playMediaFile(item.filePath)
                }
            }
        )
        
        binding.rvDownloads.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = downloadsAdapter
        }
    }
    
    private fun showDeleteConfirmation(item: com.android.music.download.data.model.DownloadItem) {
        val itemType = if (item.isFolder) "folder and all its contents" else "file"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${if (item.isFolder) "Folder" else "File"}")
            .setMessage("Are you sure you want to delete \"${item.title}\"?\n\nThis will permanently delete the $itemType from your device.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteDownload(item.id)
                Toast.makeText(requireContext(), "Deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun playMediaFile(filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val extension = file.extension.lowercase()
        when (extension) {
            in listOf("mp4", "mkv", "avi", "webm", "mov") -> {
                // Play video
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                intent.setDataAndType(uri, "video/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            }
            in listOf("mp3", "m4a", "aac", "flac", "wav", "ogg") -> {
                // Play audio - could integrate with MusicService
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                intent.setDataAndType(uri, "audio/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            }
            else -> {
                Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateFolderHeader(folderName: String) {
        binding.folderHeader.visibility = View.VISIBLE
        binding.tvFolderName.text = folderName
    }
    
    private fun hideFolderHeader() {
        binding.folderHeader.visibility = View.GONE
    }
    
    /**
     * Handle back press - returns true if handled (was in subfolder)
     */
    fun handleBackPress(): Boolean {
        return if (viewModel.navigateBack()) {
            hideFolderHeader()
            true
        } else {
            false
        }
    }

    private fun extractLink() {
        val link = binding.etLinkInput.text.toString().trim()
        
        if (link.isEmpty()) {
            Toast.makeText(requireContext(), "Please paste a link", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidUrl(link)) {
            Toast.makeText(requireContext(), "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide keyboard without clearing text
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etLinkInput.windowToken, 0)
        binding.etLinkInput.clearFocus()
        
        // Extract content
        viewModel.extractContent(link)
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.downloads.observe(viewLifecycleOwner) { downloads ->
            // If in subfolder, always show the folder contents
            if (viewModel.isInSubfolder()) {
                showFolderContents(downloads)
            } else {
                when (binding.tabLayout.selectedTabPosition) {
                    0 -> showExtracts()
                    1 -> showDownloadingItems()
                    2 -> showDownloadedItems()
                }
            }
        }
        
        // Observe current folder for header updates
        viewModel.currentFolder.observe(viewLifecycleOwner) { folderPath ->
            if (folderPath == null) {
                hideFolderHeader()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.extractionSuccess.observe(viewLifecycleOwner) { content ->
            content?.let {
                showExtractedContent(it)
                viewModel.clearExtractionSuccess()
                binding.tabLayout.getTabAt(0)?.select()
                showExtracts()
            }
        }

        viewModel.showEngineSetup.observe(viewLifecycleOwner) { show ->
            if (show) {
                showEngineSetupDialog()
            }
        }

        viewModel.engineInfo.observe(viewLifecycleOwner) { info ->
            if (info?.isUpdateAvailable == true) {
                binding.engineWarningBanner.visibility = View.VISIBLE
                binding.tvEngineWarning.text = "Engine update available"
            } else if (info?.isInstalled == false) {
                binding.engineWarningBanner.visibility = View.VISIBLE
                binding.tvEngineWarning.text = "Download engine not installed"
            } else {
                binding.engineWarningBanner.visibility = View.GONE
            }
        }
        
        // Observe emoji error event
        viewModel.emojiError.observe(viewLifecycleOwner) { playlistName ->
            playlistName?.let {
                showEmojiErrorDialog(it)
                viewModel.clearEmojiError()
            }
        }
        
        // Observe preview ready event
        viewModel.previewReady.observe(viewLifecycleOwner) { previewData ->
            previewData?.let {
                // Clear immediately to prevent re-triggering
                viewModel.clearPreviewReady()
                
                // Launch video player
                VideoPlayerActivity.start(
                    context = requireContext(),
                    videoUrl = it.videoUrl,
                    audioUrl = it.audioUrl,
                    title = it.title,
                    startPositionMs = it.startPositionMs
                )
            }
        }
    }
    
    private fun showEmojiErrorDialog(playlistName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Emoji in Playlist Name")
            .setMessage("The playlist \"$playlistName\" contains emoji characters which can cause issues with folder naming.\n\nPlease rename the playlist on YouTube to remove emojis, then try downloading again.")
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun showExtractedContent(content: ExtractedContent) {
        binding.emptyState.visibility = View.GONE
        binding.rvDownloads.visibility = View.GONE
        binding.extractedContentContainer.visibility = View.VISIBLE
        hasExtractedContent = true
        currentExtractedContent = content
        
        if (content.isPlaylist && content.playlistItems.isNotEmpty()) {
            // Show playlist with items
            _extractedBinding = ItemExtractedContentBinding.bind(binding.extractedContentView.root)
            _extractedBinding?.apply {
                tvTitle.text = content.title
                tvAuthor.text = "Playlist"
                tvPlatform.text = content.platform
                tvDuration.visibility = View.GONE
                
                if (content.thumbnailUrl != null) {
                    Glide.with(requireContext())
                        .load(content.thumbnailUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .centerCrop()
                        .into(ivThumbnail)
                } else {
                    ivThumbnail.setImageResource(R.drawable.ic_music_note)
                }
                
                // Preview button - preview first item
                btnPreview.setOnClickListener {
                    val firstItem = content.playlistItems.firstOrNull()
                    if (firstItem != null) {
                        viewModel.startPreview(firstItem)
                    } else {
                        Toast.makeText(requireContext(), "No items to preview", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Download all button
                btnDownload.text = "Download All"
                btnDownload.setOnClickListener {
                    showPlaylistDownloadOptionsMenu(it, content)
                }
            }
            
            showPlaylistItemsList(content.playlistItems)
        } else {
            // Show single video
            _extractedBinding = ItemExtractedContentBinding.bind(binding.extractedContentView.root)
            _extractedBinding?.apply {
                tvTitle.text = content.title
                tvAuthor.text = content.author ?: "Unknown"
                tvPlatform.text = content.platform
                
                if (content.duration != null) {
                    tvDuration.text = content.duration
                    tvDuration.visibility = View.VISIBLE
                } else {
                    tvDuration.visibility = View.GONE
                }
                
                if (content.thumbnailUrl != null) {
                    Glide.with(requireContext())
                        .load(content.thumbnailUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .centerCrop()
                        .into(ivThumbnail)
                } else {
                    ivThumbnail.setImageResource(R.drawable.ic_music_note)
                }
                
                // Preview button - use new preview system
                btnPreview.setOnClickListener {
                    viewModel.startPreview(content)
                }
                
                // Download button
                btnDownload.text = "Download"
                btnDownload.setOnClickListener {
                    showDownloadOptionsMenu(it, content)
                }
            }
        }
    }
    
    private fun showPlaylistItemsList(items: List<ExtractedContent>) {
        val scrollView = binding.extractedContentContainer
        val container = scrollView.getChildAt(0) as? LinearLayout
        
        if (container != null) {
            val existingRecycler = container.findViewWithTag<RecyclerView>("playlistItemsRecycler")
            if (existingRecycler != null) {
                container.removeView(existingRecycler)
            }
            
            val recyclerView = RecyclerView(requireContext()).apply {
                tag = "playlistItemsRecycler"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutManager = LinearLayoutManager(requireContext())
                adapter = PlaylistItemsAdapter(items)
            }
            
            container.addView(recyclerView)
        }
    }
    
    private fun showPlaylistDownloadOptionsMenu(anchor: View, content: ExtractedContent) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Video + Audio")
        popup.menu.add("Audio only")
        
        popup.setOnMenuItemClickListener { menuItem ->
            val formatId = when (menuItem.title) {
                "Video + Audio" -> "best"
                "Audio only" -> "bestaudio"
                else -> "best"
            }
            
            viewModel.startDownload(content, formatId)
            Toast.makeText(
                requireContext(),
                "Queued playlist for download",
                Toast.LENGTH_SHORT
            ).show()
            hasExtractedContent = false
            binding.extractedContentContainer.visibility = View.GONE
            binding.tabLayout.getTabAt(1)?.select()
            showDownloadingItems()
            true
        }
        
        popup.show()
    }

    private fun showDownloadOptionsMenu(anchor: View, content: ExtractedContent) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add("Video + Audio")
        popup.menu.add("Video only")
        popup.menu.add("Audio only")
        
        popup.setOnMenuItemClickListener { menuItem ->
            val formatId = when (menuItem.title) {
                "Video + Audio" -> "best"
                "Video only" -> "bestvideo"
                "Audio only" -> "bestaudio"
                else -> "best"
            }
            viewModel.startDownload(content, formatId)
            hasExtractedContent = false
            binding.extractedContentContainer.visibility = View.GONE
            binding.tabLayout.getTabAt(1)?.select()
            showDownloadingItems()
            true
        }
        
        popup.show()
    }

    private fun showEngineSetupDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Download Engine Required")
            .setMessage("The download engine is not installed. Would you like to install it now?")
            .setPositiveButton("Install") { _, _ ->
                openSettings()
            }
            .setNegativeButton("Later") { _, _ ->
                viewModel.dismissEngineSetup()
            }
            .show()
    }

    private fun openSettings() {
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _extractedBinding = null
        _binding = null
    }

    /**
     * Extract content from URL
     */
    fun extractFromUrl(url: String) {
        if (!isValidUrl(url)) {
            Toast.makeText(requireContext(), "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.extractContent(url)
    }

    companion object {
        const val TAG = "DownloadsFragment"
        
        fun newInstance() = DownloadsFragment()
    }
}

/**
 * Adapter for displaying playlist items
 */
class PlaylistItemsAdapter(
    private val items: List<ExtractedContent>
) : RecyclerView.Adapter<PlaylistItemsAdapter.PlaylistItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_playlist_content,
            parent,
            false
        )
        return PlaylistItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position + 1)
    }

    override fun getItemCount() = items.size

    class PlaylistItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        
        fun bind(item: ExtractedContent, index: Int) {
            tvIndex.text = index.toString()
            tvTitle.text = item.title
            tvDuration.text = item.duration ?: "Duration unknown"
        }
    }
}
