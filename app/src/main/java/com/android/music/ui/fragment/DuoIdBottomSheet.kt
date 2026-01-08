package com.android.music.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.auth.AuthManager
import com.android.music.databinding.BottomSheetDuoIdBinding
import com.android.music.duo.webrtc.DuoIdManager
import com.android.music.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Bottom sheet for managing Duo ID
 * Allows users to view, copy, share, and change their Duo ID
 */
class DuoIdBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDuoIdBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var duoIdManager: DuoIdManager
    private var authManager: AuthManager? = null
    private var currentDuoId: String? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.let { 
                authViewModel.signInWithGoogle(it)
                // After sign in, load Duo ID
                loadDuoId()
            }
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DuoIdBottomSheetStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDuoIdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        duoIdManager = DuoIdManager.getInstance(requireContext())
        authManager = AuthManager(requireContext())
        
        setupBottomSheetBehavior()
        setupUI()
        observeAuthState()
        loadDuoId()
    }

    private fun setupBottomSheetBehavior() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                // Set to 70% of screen height
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                behavior.peekHeight = (screenHeight * 0.7).toInt()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    private fun setupUI() {
        // Copy ID button
        binding.btnCopyId.setOnClickListener {
            currentDuoId?.let { id ->
                copyToClipboard(id)
                Toast.makeText(requireContext(), R.string.duo_id_copied, Toast.LENGTH_SHORT).show()
            }
        }

        // Share ID button
        binding.btnShareId.setOnClickListener {
            currentDuoId?.let { id ->
                shareId(id)
            }
        }

        // Custom ID input listener
        binding.etCustomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                binding.btnSaveCustom.isEnabled = text.length == DuoIdManager.ID_LENGTH
                binding.tvError.visibility = View.GONE
            }
        })

        // Auto generate button
        binding.btnAutoGenerate.setOnClickListener {
            regenerateDuoId()
        }

        // Save custom ID button
        binding.btnSaveCustom.setOnClickListener {
            val customId = binding.etCustomId.text?.toString() ?: ""
            if (customId.length == DuoIdManager.ID_LENGTH) {
                saveCustomId(customId)
            }
        }

        // Sign in button (for not signed in state)
        binding.btnSignInForDuo.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun observeAuthState() {
        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                showSignedInState()
                loadDuoId()
            } else {
                showNotSignedInState()
            }
        }
    }

    private fun loadDuoId() {
        if (!duoIdManager.isUserSignedIn()) {
            showNotSignedInState()
            return
        }

        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val duoId = duoIdManager.getOrCreateDuoId()
                if (duoId != null) {
                    currentDuoId = duoId
                    binding.tvCurrentId.text = formatDuoId(duoId)
                    showSignedInState()
                } else {
                    showNotSignedInState()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading Duo ID: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun regenerateDuoId() {
        showLoading(true)
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = duoIdManager.regenerateDuoId()
                result.onSuccess { newId ->
                    currentDuoId = newId
                    binding.tvCurrentId.text = formatDuoId(newId)
                    binding.etCustomId.text?.clear()
                    Toast.makeText(requireContext(), R.string.duo_id_changed, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    binding.tvError.text = e.message
                    binding.tvError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.tvError.text = e.message
                binding.tvError.visibility = View.VISIBLE
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveCustomId(customId: String) {
        showLoading(true)
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = duoIdManager.changeDuoId(customId)
                result.onSuccess { newId ->
                    currentDuoId = newId
                    binding.tvCurrentId.text = formatDuoId(newId)
                    binding.etCustomId.text?.clear()
                    Toast.makeText(requireContext(), R.string.duo_id_changed, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    binding.tvError.text = e.message
                    binding.tvError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.tvError.text = e.message
                binding.tvError.visibility = View.VISIBLE
            } finally {
                showLoading(false)
            }
        }
    }

    private fun formatDuoId(id: String): String {
        // Format as XXX-XXX-XXX-XXX for better readability
        return id.chunked(3).joinToString("-")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Duo ID", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun shareId(id: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.duo_share_id_message, id))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun startGoogleSignIn() {
        authManager?.let { manager ->
            val signInIntent = manager.getGoogleSignInClient().signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun showSignedInState() {
        binding.cardCurrentId.visibility = View.VISIBLE
        binding.dividerSection.visibility = View.VISIBLE
        binding.tilCustomId.visibility = View.VISIBLE
        binding.buttonsContainer.visibility = View.VISIBLE
        binding.notSignedInState.visibility = View.GONE
    }

    private fun showNotSignedInState() {
        binding.cardCurrentId.visibility = View.GONE
        binding.dividerSection.visibility = View.GONE
        binding.tilCustomId.visibility = View.GONE
        binding.buttonsContainer.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.notSignedInState.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAutoGenerate.isEnabled = !show
        binding.btnSaveCustom.isEnabled = !show && (binding.etCustomId.text?.length == DuoIdManager.ID_LENGTH)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DuoIdBottomSheet"

        fun newInstance(): DuoIdBottomSheet {
            return DuoIdBottomSheet()
        }
    }
}
