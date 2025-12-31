package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.browse.ui.viewmodel.BrowseViewModel
import com.android.music.databinding.FragmentBrowseProfileBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class BrowseProfileFragment : Fragment() {

    private var _binding: FragmentBrowseProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BrowseViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowseProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                viewModel.getAuthManager().signOut()
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
                updateProfileUI()
            }
        }
        updateProfileUI()
    }

    private fun updateProfileUI() {
        val authManager = viewModel.getAuthManager()
        
        if (authManager.isAuthenticated()) {
            binding.tvProfileName.text = authManager.getDisplayName() ?: "YouTube User"
            binding.tvProfileEmail.text = authManager.getEmail() ?: ""
            binding.tvProfileEmail.visibility = View.VISIBLE
            binding.btnSignOut.visibility = View.VISIBLE

            val photoUrl = authManager.getPhotoUrl()
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .circleCrop()
                    .into(binding.ivProfileAvatar)
            }
        } else {
            binding.tvProfileName.text = "Not signed in"
            binding.tvProfileEmail.visibility = View.GONE
            binding.btnSignOut.visibility = View.GONE
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_music_note)
        }
    }

    private fun observeViewModel() {
        viewModel.isAuthenticated.observe(viewLifecycleOwner) {
            updateProfileUI()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
