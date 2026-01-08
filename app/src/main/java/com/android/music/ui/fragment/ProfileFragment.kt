package com.android.music.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.auth.AuthManager
import com.android.music.data.model.User
import com.android.music.databinding.FragmentProfileBinding
import com.android.music.duo.webrtc.DuoIdManager
import com.android.music.ui.viewmodel.AuthViewModel
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Profile fragment - User profile and settings
 * Contains all sidebar drawer functionality
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private val authViewModel: AuthViewModel by activityViewModels()
    private var authManager: AuthManager? = null
    private lateinit var duoIdManager: DuoIdManager
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.let { authViewModel.signInWithGoogle(it) }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        duoIdManager = DuoIdManager.getInstance(requireContext())
        setupUI()
        observeAuthState()
    }

    private fun setupUI() {
        // Sign In Button
        binding.btnSignIn.setOnClickListener {
            startGoogleSignIn()
        }
        
        // Menu Items
        binding.menuDownloads.setOnClickListener {
            navigateToDownloads()
        }
        
        binding.menuSettings.setOnClickListener {
            openSettings()
        }
        
        binding.menuEqualizer.setOnClickListener {
            openEqualizer()
        }
        
        binding.menuDuoId.setOnClickListener {
            openDuoIdBottomSheet()
        }
        
        binding.menuUserAgreement.setOnClickListener {
            openUserAgreement()
        }
        
        binding.menuPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }
        
        binding.menuUpdateAutomatically.setOnClickListener {
            // Toggle is handled by the switch
        }
        
        // Sign Out
        binding.menuSignOut.setOnClickListener {
            showSignOutConfirmation()
        }
    }
    
    private fun observeAuthState() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            updateProfileUI(user)
            loadDuoIdPreview()
        }
        
        authViewModel.signInState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthViewModel.SignInState.Loading -> {
                    // Could show loading indicator
                }
                is AuthViewModel.SignInState.Success -> {
                    Toast.makeText(requireContext(), "Welcome ${state.user.displayName}!", Toast.LENGTH_SHORT).show()
                }
                is AuthViewModel.SignInState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun loadDuoIdPreview() {
        if (duoIdManager.isUserSignedIn()) {
            lifecycleScope.launch {
                val duoId = duoIdManager.getOrCreateDuoId()
                duoId?.let { id ->
                    // Show last 4 digits as preview
                    binding.tvDuoIdPreview.text = "•••${id.takeLast(4)}"
                    binding.tvDuoIdPreview.visibility = View.VISIBLE
                }
            }
        } else {
            binding.tvDuoIdPreview.visibility = View.GONE
        }
    }
    
    private fun updateProfileUI(user: User?) {
        if (user != null) {
            // User is signed in
            binding.tvUserName.text = user.displayName ?: "User"
            binding.tvUserEmail.text = user.email
            binding.tvUserEmail.visibility = if (user.email != null) View.VISIBLE else View.GONE
            binding.btnSignIn.visibility = View.GONE
            binding.signOutSection.visibility = View.VISIBLE
            
            // Load profile picture
            user.photoUrl?.let { photoUrl ->
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivProfilePicture)
            }
        } else {
            // User is not signed in
            binding.tvUserName.text = getString(R.string.guest_user)
            binding.tvUserEmail.visibility = View.GONE
            binding.btnSignIn.visibility = View.VISIBLE
            binding.signOutSection.visibility = View.GONE
            binding.ivProfilePicture.setImageResource(R.drawable.ic_person)
            binding.tvDuoIdPreview.visibility = View.GONE
        }
    }
    
    private fun startGoogleSignIn() {
        authManager?.let { manager ->
            val signInIntent = manager.getGoogleSignInClient().signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }
    
    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.sign_out_confirm_title)
            .setMessage(R.string.sign_out_confirm_message)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                signOut()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun signOut() {
        authManager?.signOut()
        duoIdManager.clearLocalDuoId()
        // Refresh the UI by re-checking auth state
        authViewModel.refreshAuthState()
        Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()
    }
    
    private fun navigateToDownloads() {
        // Navigate to Downloads tab via bottom navigation
        activity?.let { act ->
            if (act is com.android.music.ui.activity.MainActivity) {
                act.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                    ?.selectedItemId = R.id.nav_downloads
            }
        }
    }
    
    private fun openSettings() {
        val intent = Intent(requireContext(), com.android.music.settings.SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openEqualizer() {
        val intent = Intent(requireContext(), com.android.music.equalizer.ui.EqualizerActivity::class.java)
        startActivity(intent)
    }
    
    private fun openDuoIdBottomSheet() {
        DuoIdBottomSheet.newInstance().show(childFragmentManager, DuoIdBottomSheet.TAG)
    }
    
    private fun openUserAgreement() {
        com.android.music.legal.LegalDocumentActivity.startUserAgreement(requireContext())
    }
    
    private fun openPrivacyPolicy() {
        com.android.music.legal.LegalDocumentActivity.startPrivacyPolicy(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}
