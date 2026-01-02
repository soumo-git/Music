package com.android.music.duo.chat.ui

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.music.duo.chat.model.ChatState
import com.android.music.duo.data.model.SignalStrength
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Full-screen bottom sheet for Duo chat
 */
class DuoChatBottomSheet : BottomSheetDialogFragment() {

    private val duoViewModel: DuoViewModel by activityViewModels()
    
    // Permission launcher for audio recording
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d(TAG, "Audio permission granted")
            duoViewModel.onAudioPermissionGranted()
        } else {
            android.util.Log.d(TAG, "Audio permission denied")
            android.widget.Toast.makeText(
                requireContext(),
                "Microphone permission is required for voice messages",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                val messages by duoViewModel.chatMessages.collectAsState()
                val isPartnerTyping by duoViewModel.isPartnerTyping.collectAsState()
                val signalStrength by duoViewModel.signalStrength.collectAsState()
                val connectionType by duoViewModel.connectionTypeText.collectAsState()
                val isConnected by duoViewModel.isConnectedFlow.collectAsState()
                val hasUnreadMessages by duoViewModel.hasUnreadMessages.collectAsState()
                val isRecording by duoViewModel.isRecordingVoice.collectAsState()

                val chatState = ChatState(
                    messages = messages,
                    isPartnerTyping = isPartnerTyping,
                    connectionType = connectionType,
                    signalStrength = signalStrengthToBars(signalStrength),
                    isConnected = isConnected,
                    hasUnreadMessages = hasUnreadMessages
                )

                DuoChatScreen(
                    chatState = chatState,
                    onSendMessage = { text ->
                        duoViewModel.sendChatMessage(text)
                    },
                    onTyping = {
                        duoViewModel.notifyTyping()
                    },
                    onDismiss = {
                        dismiss()
                    },
                    onVoiceRecord = {
                        duoViewModel.toggleVoiceRecording()
                    },
                    onMarkMessagesRead = {
                        duoViewModel.markMessagesAsRead()
                    },
                    isRecording = isRecording
                )
            }
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Observe permission request events
        viewLifecycleOwner.lifecycleScope.launch {
            duoViewModel.requestAudioPermission.collect {
                requestAudioPermission()
            }
        }
        
        // Make the bottom sheet full screen
        (dialog as? BottomSheetDialog)?.let { dialog ->
            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isDraggable = true
                peekHeight = 0
            }
            
            // Make it full screen
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Mark chat as open when visible
        duoViewModel.onChatOpened()
    }
    
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Always mark chat as closed when dismissed (any way)
        duoViewModel.onChatClosed()
    }
    
    private fun requestAudioPermission() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                // Permission is automatically granted on API < 23
                duoViewModel.onAudioPermissionGranted()
            }
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                duoViewModel.onAudioPermissionGranted()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show explanation dialog
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Microphone Permission")
                    .setMessage("Microphone access is needed to record and send voice messages in Duo chat.")
                    .setPositiveButton("Grant") { _, _ ->
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                // Request permission directly
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Material3_DayNight_BottomSheetDialog

    private fun signalStrengthToBars(strength: SignalStrength): Int {
        return when (strength) {
            SignalStrength.NONE -> 0
            SignalStrength.WEAK -> 1
            SignalStrength.FAIR -> 2
            SignalStrength.GOOD -> 3
            SignalStrength.EXCELLENT -> 4
        }
    }

    companion object {
        const val TAG = "DuoChatBottomSheet"

        fun newInstance(): DuoChatBottomSheet {
            return DuoChatBottomSheet()
        }
    }
}
