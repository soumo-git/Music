package com.android.music.browse.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.android.music.R
import com.android.music.browse.ui.viewmodel.SpotifyViewModel
import com.android.music.databinding.FragmentSpotifyProfileBinding
import com.bumptech.glide.Glide

class SpotifyProfileFragment : Fragment() {

    private var _binding: FragmentSpotifyProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SpotifyViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotifyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        
        // Load profile if not cached
        if (viewModel.userProfile.value == null) {
            viewModel.loadUserProfile()
        }
    }

    private fun setupUI() {
        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
            Toast.makeText(requireContext(), "Signed out from Spotify", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            binding.apply {
                tvDisplayName.text = profile.displayName ?: "Spotify User"
                tvEmail.text = profile.email ?: ""
                tvFollowers.text = profile.followers ?: "0"
                tvCountry.text = profile.country ?: "N/A"
                tvSubscription.text = profile.product?.replaceFirstChar { it.uppercase() } ?: "Free"

                // Load profile image
                Glide.with(requireContext())
                    .load(profile.imageUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(ivProfileImage)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
