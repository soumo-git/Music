package com.android.music.videoplayer.engine.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.databinding.FragmentVideoEngineSettingsBinding
import com.android.music.videoplayer.engine.core.VideoEngineUpdateResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for managing video engine settings.
 * Shows engine version, update status, and allows updating/switching engines.
 */
class VideoEngineSettingsFragment : Fragment() {
    
    private var _binding: FragmentVideoEngineSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: VideoEngineSettingsViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoEngineSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        viewModel.initialize(requireContext())
    }
    
    private fun setupUI() {
        binding.btnUpdate.setOnClickListener {
            when (binding.btnUpdate.text.toString()) {
                "Install Engine" -> viewModel.installOrUpdateEngine()
                "Update Now" -> viewModel.installOrUpdateEngine()
            }
        }
        
        binding.btnCheckUpdates.setOnClickListener {
            viewModel.checkForUpdates()
        }
        
        binding.btnUninstall.setOnClickListener {
            viewModel.uninstallEngine()
        }
        
        // Engine selection spinner
        binding.spinnerEngine.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedEngine = parent?.getItemAtPosition(position) as? String
                if (selectedEngine != null && selectedEngine != viewModel.selectedEngine.value) {
                    viewModel.switchEngine(requireContext(), selectedEngine)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.engineInfo.collectLatest { info ->
                updateEngineInfoUI(info)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isUpdating.collectLatest { isUpdating ->
                binding.btnUpdate.isEnabled = !isUpdating
                binding.btnCheckUpdates.isEnabled = !isUpdating
                binding.spinnerEngine.isEnabled = !isUpdating
                binding.progressBar.visibility = if (isUpdating) View.VISIBLE else View.GONE
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateProgress.collectLatest { progress ->
                binding.progressBar.progress = progress
                if (progress > 0) {
                    binding.tvProgress.visibility = View.VISIBLE
                    binding.tvProgress.text = "$progress%"
                } else {
                    binding.tvProgress.visibility = View.GONE
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateResult.collectLatest { result ->
                result?.let { handleUpdateResult(it) }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableEngines.collectLatest { engines ->
                setupEngineSpinner(engines)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedEngine.collectLatest { selectedEngine ->
                updateSelectedEngine(selectedEngine)
            }
        }
    }
    
    private fun setupEngineSpinner(engines: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, engines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEngine.adapter = adapter
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun updateSelectedEngine(selectedEngine: String) {
        val adapter = binding.spinnerEngine.adapter as? ArrayAdapter<String>
        val position = adapter?.getPosition(selectedEngine) ?: 0
        binding.spinnerEngine.setSelection(position)
    }
    
    private fun updateEngineInfoUI(info: com.android.music.videoplayer.engine.core.VideoEngineInfo) {
        binding.tvEngineName.text = info.name
        
        if (info.isInstalled) {
            binding.tvInstalledVersion.text = "Installed: ${info.installedVersion ?: "Unknown"}"
            binding.tvInstalledVersion.visibility = View.VISIBLE
            binding.btnUninstall.visibility = View.VISIBLE
            binding.ivStatusIcon.setImageResource(android.R.drawable.presence_online)
            binding.tvStatus.text = "Ready"
            binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            
            if (info.isUpdateAvailable) {
                binding.btnUpdate.text = "Update Now"
                binding.btnUpdate.visibility = View.VISIBLE
                binding.btnCheckUpdates.visibility = View.GONE
                binding.updateWarning.visibility = View.VISIBLE
                binding.tvUpdateWarning.text = "A new version of ${info.name} is available."
            } else {
                binding.btnUpdate.visibility = View.GONE
                binding.btnCheckUpdates.text = "Check for Updates"
                binding.btnCheckUpdates.visibility = View.VISIBLE
                binding.updateWarning.visibility = View.GONE
            }
        } else {
            binding.tvInstalledVersion.text = "Not installed"
            binding.tvInstalledVersion.visibility = View.VISIBLE
            binding.btnUninstall.visibility = View.GONE
            binding.ivStatusIcon.setImageResource(android.R.drawable.presence_offline)
            binding.tvStatus.text = "Not installed"
            binding.tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            
            binding.btnUpdate.text = "Install Engine"
            binding.btnUpdate.visibility = View.VISIBLE
            binding.btnCheckUpdates.visibility = View.GONE
            binding.updateWarning.visibility = View.VISIBLE
            binding.tvUpdateWarning.text = "Video engine needs to be installed to play videos."
        }
        
        if (info.latestVersion != null && info.latestVersion != info.installedVersion && info.isInstalled) {
            binding.tvLatestVersion.text = "Latest: ${info.latestVersion}"
            binding.tvLatestVersion.visibility = View.VISIBLE
        } else {
            binding.tvLatestVersion.visibility = View.GONE
        }
        
        // Last checked
        if (info.lastChecked > 0) {
            val lastCheckedText = formatLastChecked(info.lastChecked)
            binding.tvLastChecked.text = "Last checked: $lastCheckedText"
            binding.tvLastChecked.visibility = View.VISIBLE
        } else {
            binding.tvLastChecked.visibility = View.GONE
        }
    }
    
    private fun handleUpdateResult(result: VideoEngineUpdateResult) {
        when (result) {
            is VideoEngineUpdateResult.Success -> {
                Toast.makeText(
                    requireContext(),
                    "Engine installed successfully (${result.newVersion})",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is VideoEngineUpdateResult.AlreadyUpToDate -> {
                Toast.makeText(
                    requireContext(),
                    "Already up to date (${result.version})",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is VideoEngineUpdateResult.Failed -> {
                Toast.makeText(
                    requireContext(),
                    "Operation failed: ${result.error}",
                    Toast.LENGTH_LONG
                ).show()
            }
            VideoEngineUpdateResult.Downloading -> {
                // Progress is shown via progressBar
            }
        }
        viewModel.clearUpdateResult()
    }
    
    private fun formatLastChecked(timestamp: Long): String {
        val elapsed = System.currentTimeMillis() - timestamp
        val minutes = elapsed / (60 * 1000)
        val hours = elapsed / (60 * 60 * 1000)
        val days = elapsed / (24 * 60 * 60 * 1000)
        
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hours ago"
            else -> "$days days ago"
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "VideoEngineSettingsFragment"
        
        fun newInstance() = VideoEngineSettingsFragment()
    }
}