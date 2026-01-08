package com.android.music.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.music.databinding.FragmentDownloadsTabBinding

/**
 * Downloads tab fragment - Wrapper for DownloadsFragment with custom top bar
 */
class DownloadsTabFragment : Fragment() {

    private var _binding: FragmentDownloadsTabBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLinkInput()
        loadDownloadsFragment()
    }

    private fun setupLinkInput() {
        // Handle extract button click
        binding.btnExtract.setOnClickListener {
            val url = binding.etLinkInput.text.toString().trim()
            if (url.isNotBlank()) {
                // Get the DownloadsFragment and trigger extraction
                val downloadsFragment = childFragmentManager.findFragmentById(com.android.music.R.id.downloadsFragmentContainer)
                    as? com.android.music.download.ui.fragment.DownloadsFragment
                downloadsFragment?.extractFromUrl(url)

                // Hide keyboard but keep the link visible
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.etLinkInput.windowToken, 0)
                binding.etLinkInput.clearFocus()
            } else {
                Toast.makeText(requireContext(), "Please paste a video or playlist link", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle keyboard done action
        binding.etLinkInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnExtract.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun loadDownloadsFragment() {
        // Add DownloadsFragment if not already added
        if (childFragmentManager.findFragmentById(com.android.music.R.id.downloadsFragmentContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(com.android.music.R.id.downloadsFragmentContainer, com.android.music.download.ui.fragment.DownloadsFragment.newInstance())
                .commit()
        }
    }
    
    /**
     * Handle back press - returns true if handled (was in subfolder)
     */
    fun handleBackPress(): Boolean {
        val downloadsFragment = childFragmentManager.findFragmentById(com.android.music.R.id.downloadsFragmentContainer)
            as? com.android.music.download.ui.fragment.DownloadsFragment
        return downloadsFragment?.handleBackPress() ?: false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DownloadsTabFragment()
    }
}
