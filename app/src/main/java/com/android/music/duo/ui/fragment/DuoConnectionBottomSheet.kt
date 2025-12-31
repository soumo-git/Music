package com.android.music.duo.ui.fragment

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.music.R
import com.android.music.databinding.BottomSheetDuoConnectionBinding
import com.android.music.duo.data.model.DuoConnectionState
import com.android.music.duo.data.model.DuoDevice
import com.android.music.duo.ui.adapter.DuoDeviceAdapter
import com.android.music.duo.ui.viewmodel.DuoViewModel
import com.android.music.duo.util.DuoPermissionHelper
import com.android.music.duo.webrtc.SignalingManager
import com.android.music.duo.webrtc.model.WebRTCConnectionState
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DuoConnectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDuoConnectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuoViewModel by activityViewModels()
    private lateinit var deviceAdapter: DuoDeviceAdapter
    
    // Track if we were already connected when the sheet opened
    private var wasConnectedOnOpen = false
    
    // Current incoming request (offer)
    private var currentIncomingOffer: SignalingManager.IncomingOffer? = null

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startDiscoveryIfReady()
        } else {
            Toast.makeText(
                requireContext(),
                "Location permission is required for WiFi Direct",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDuoConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            if (!isAdded || context == null) return@setOnShowListener
            
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                // Set to 85% of screen height for more content
                val displayMetrics = resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.85).toInt()
                behavior.peekHeight = height
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                it.layoutParams.height = height
            }
        }
        
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check if already connected when sheet opens
        wasConnectedOnOpen = viewModel.connectionState.value is DuoConnectionState.Connected ||
                viewModel.webRTCConnectionState.value is WebRTCConnectionState.Connected
        
        setupUI()
        setupDevicesList()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupUI() {
        // Load Duo icon
        Glide.with(this)
            .load("file:///android_asset/Duo.png")
            .into(binding.ivDuoIcon)

        // Set device info
        binding.tvDeviceName.text = viewModel.deviceName
        
        // Set Duo ID (will be updated when loaded)
        binding.tvUserId.text = "ID: Loading..."
        
        // Load Duo ID
        viewLifecycleOwner.lifecycleScope.launch {
            val duoId = viewModel.getMyDuoId()
            if (isAdded && _binding != null) {
                binding.tvUserId.text = "ID: $duoId"
            }
        }
        
        // Make ID copyable on long press
        binding.tvUserId.setOnLongClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val duoId = viewModel.getMyDuoId()
                copyToClipboard(duoId)
            }
            true
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Duo ID", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.duo_id_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setupDevicesList() {
        deviceAdapter = DuoDeviceAdapter { device ->
            handleDeviceClick(device)
        }

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun handleDeviceClick(device: DuoDevice) {
        val currentState = viewModel.connectionState.value
        
        when {
            currentState is DuoConnectionState.Connected -> {
                showDisconnectConfirmDialog(device)
            }
            currentState is DuoConnectionState.Connecting && currentState.device.deviceAddress == device.deviceAddress -> {
                showCancelConnectionDialog(device)
            }
            device.status == com.android.music.duo.data.model.DeviceStatus.INVITED -> {
                showCancelConnectionDialog(device)
            }
            device.status == com.android.music.duo.data.model.DeviceStatus.CONNECTED -> {
                showDisconnectConfirmDialog(device)
            }
            else -> {
                showConnectConfirmDialog(device)
            }
        }
    }

    private fun showDisconnectConfirmDialog(device: DuoDevice) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Disconnect")
            .setMessage("Do you want to disconnect from ${device.deviceName}?")
            .setPositiveButton("Disconnect") { _, _ ->
                viewModel.disconnect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCancelConnectionDialog(device: DuoDevice) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Cancel Connection")
            .setMessage("Do you want to cancel the connection request to ${device.deviceName}?")
            .setPositiveButton("Cancel Request") { _, _ ->
                viewModel.cancelConnection()
            }
            .setNegativeButton("Keep Waiting", null)
            .show()
    }

    private fun setupClickListeners() {
        // Connect by ID button
        binding.btnConnectById.setOnClickListener {
            connectByDuoId()
        }
        
        // Enter key on ID input
        binding.etPartnerUserId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                connectByDuoId()
                true
            } else {
                false
            }
        }
        
        // Notify when online button
        binding.btnNotifyWhenOnline.setOnClickListener {
            val partnerId = binding.etPartnerUserId.text.toString().trim()
            if (partnerId.isNotEmpty()) {
                viewModel.requestNotifyWhenOnline(partnerId)
                Toast.makeText(requireContext(), R.string.duo_notify_requested, Toast.LENGTH_SHORT).show()
                binding.layoutPartnerStatus.visibility = View.GONE
            }
        }
        
        // Accept incoming request
        binding.btnAcceptRequest.setOnClickListener {
            currentIncomingOffer?.let { offer ->
                viewModel.acceptIncomingOffer(offer)
            }
        }
        
        // Reject incoming request
        binding.btnRejectRequest.setOnClickListener {
            currentIncomingOffer?.let { offer ->
                viewModel.rejectIncomingOffer(offer)
            }
        }
        
        binding.btnSearchNearby.setOnClickListener {
            checkPermissionsAndStartDiscovery()
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }
    }
    
    private fun connectByDuoId() {
        val partnerId = binding.etPartnerUserId.text.toString().trim()
        
        if (partnerId.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a Duo ID", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (partnerId.length != 12 || !partnerId.all { it.isDigit() }) {
            Toast.makeText(requireContext(), R.string.duo_invalid_id, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Hide keyboard
        binding.etPartnerUserId.clearFocus()
        
        // Connect via WebRTC
        viewModel.connectByDuoId(partnerId)
    }

    private fun checkPermissionsAndStartDiscovery() {
        val context = requireContext()
        
        if (!DuoPermissionHelper.isWifiEnabled(context)) {
            showWifiDisabledDialog()
            return
        }
        
        if (!DuoPermissionHelper.isLocationEnabled(context)) {
            showLocationDisabledDialog()
            return
        }
        
        if (!DuoPermissionHelper.hasRequiredPermissions(context)) {
            permissionLauncher.launch(DuoPermissionHelper.getRequiredPermissions())
            return
        }
        
        startDiscoveryIfReady()
    }

    private fun startDiscoveryIfReady() {
        val errorMessage = DuoPermissionHelper.getErrorMessage(requireContext())
        if (errorMessage != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.startDiscovery()
    }

    private fun showWifiDisabledDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("WiFi Required")
            .setMessage("WiFi must be enabled to use Duo. Would you like to enable it?")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationDisabledDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle("Location Required")
            .setMessage("Location services must be enabled to discover nearby devices. Would you like to enable it?")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConnectConfirmDialog(device: DuoDevice) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.duo_connect_confirm_title)
            .setMessage(getString(R.string.duo_connect_confirm_message, device.deviceName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.connectToDevice(device)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        // WiFi Direct connection state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collectLatest { state ->
                if (isAdded && _binding != null) {
                    updateWifiDirectConnectionUI(state)
                }
            }
        }
        
        // WebRTC connection state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.webRTCConnectionState.collectLatest { state ->
                if (isAdded && _binding != null) {
                    updateWebRTCConnectionUI(state)
                }
            }
        }
        
        // Incoming WebRTC requests (offers)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.incomingWebRTCOffer.collectLatest { offer ->
                if (isAdded && _binding != null) {
                    updateIncomingOfferUI(offer)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.discoveredDevices.collectLatest { devices ->
                if (isAdded && _binding != null) {
                    deviceAdapter.submitList(devices)
                    binding.tvDevicesHeader.visibility = if (devices.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.rvDevices.visibility = if (devices.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    
    private fun updateIncomingOfferUI(offer: SignalingManager.IncomingOffer?) {
        currentIncomingOffer = offer
        
        if (offer != null) {
            binding.cardIncomingRequest.visibility = View.VISIBLE
            binding.tvIncomingRequestFrom.text = getString(R.string.duo_request_from, offer.fromDeviceName)
        } else {
            binding.cardIncomingRequest.visibility = View.GONE
        }
    }
    
    private fun updateWebRTCConnectionUI(state: WebRTCConnectionState) {
        when (state) {
            is WebRTCConnectionState.Idle -> {
                // Don't change UI if WiFi Direct is active
                if (viewModel.connectionState.value !is DuoConnectionState.Connected) {
                    binding.layoutPartnerStatus.visibility = View.GONE
                }
            }
            is WebRTCConnectionState.CheckingPartner -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_checking_partner)
                binding.progressBar.visibility = View.VISIBLE
                binding.layoutPartnerStatus.visibility = View.GONE
            }
            is WebRTCConnectionState.PartnerOffline -> {
                binding.progressBar.visibility = View.GONE
                binding.layoutPartnerStatus.visibility = View.VISIBLE
                binding.tvPartnerStatusTitle.text = getString(R.string.duo_partner_offline)
                binding.tvPartnerStatusMessage.text = getString(
                    R.string.duo_last_seen,
                    viewModel.formatLastSeen(state.lastSeen)
                )
                binding.btnNotifyWhenOnline.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.duo_status_disconnected)
            }
            is WebRTCConnectionState.CreatingOffer -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_creating_offer)
                binding.progressBar.visibility = View.VISIBLE
                binding.layoutPartnerStatus.visibility = View.GONE
            }
            is WebRTCConnectionState.WaitingForAnswer -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_waiting_response)
                binding.progressBar.visibility = View.VISIBLE
                binding.tvSearching.text = getString(R.string.duo_waiting_response)
                binding.tvSearching.visibility = View.VISIBLE
            }
            is WebRTCConnectionState.Connecting -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_status_connecting)
                binding.progressBar.visibility = View.VISIBLE
            }
            is WebRTCConnectionState.Connected -> {
                binding.tvConnectionStatus.text = "${getString(R.string.duo_webrtc_connected)} to ${state.partnerDeviceName}"
                binding.progressBar.visibility = View.GONE
                binding.tvSearching.visibility = View.GONE
                binding.btnSearchNearby.visibility = View.GONE
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnDisconnect.text = getString(R.string.duo_disconnect)
                binding.etPartnerUserId.visibility = View.GONE
                binding.btnConnectById.visibility = View.GONE
                binding.layoutPartnerStatus.visibility = View.GONE
                binding.cardIncomingRequest.visibility = View.GONE
                binding.rvDevices.visibility = View.GONE
                binding.tvDevicesHeader.visibility = View.GONE
                
                // Show connected device card with mobile icon
                showConnectedDeviceCard(state.partnerDeviceName, isWebRTC = true)
                
                if (!wasConnectedOnOpen) {
                    dismissAllowingStateLoss()
                }
            }
            is WebRTCConnectionState.Error -> {
                binding.tvConnectionStatus.text = state.message
                binding.progressBar.visibility = View.GONE
                binding.tvSearching.visibility = View.GONE
                binding.layoutPartnerStatus.visibility = View.GONE
            }
            is WebRTCConnectionState.Disconnected -> {
                resetToDisconnectedState()
            }
        }
    }

    private fun updateWifiDirectConnectionUI(state: DuoConnectionState) {
        // Skip if WebRTC is connected
        if (viewModel.webRTCConnectionState.value is WebRTCConnectionState.Connected) {
            return
        }
        
        when (state) {
            is DuoConnectionState.Disconnected -> {
                resetToDisconnectedState()
            }
            is DuoConnectionState.Searching -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_status_searching)
                binding.progressBar.visibility = View.VISIBLE
                binding.tvSearching.text = getString(R.string.duo_searching)
                binding.tvSearching.visibility = View.VISIBLE
                binding.btnSearchNearby.isEnabled = false
            }
            is DuoConnectionState.Connecting -> {
                binding.tvConnectionStatus.text = getString(R.string.duo_status_connecting)
                binding.progressBar.visibility = View.VISIBLE
                binding.tvSearching.text = "Connecting to ${state.device.deviceName}..."
                binding.tvSearching.visibility = View.VISIBLE
                binding.btnSearchNearby.visibility = View.GONE
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnDisconnect.text = "Cancel"
                binding.btnDisconnect.setOnClickListener {
                    viewModel.cancelConnection()
                }
            }
            is DuoConnectionState.Connected -> {
                binding.tvConnectionStatus.text = "${getString(R.string.duo_wifi_direct_connected)} to ${state.device.deviceName}"
                binding.progressBar.visibility = View.GONE
                binding.tvSearching.visibility = View.GONE
                binding.btnSearchNearby.visibility = View.GONE
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.btnDisconnect.text = getString(R.string.duo_disconnect)
                binding.btnDisconnect.setOnClickListener {
                    viewModel.disconnect()
                }
                binding.etPartnerUserId.visibility = View.GONE
                binding.btnConnectById.visibility = View.GONE
                binding.layoutPartnerStatus.visibility = View.GONE
                binding.rvDevices.visibility = View.GONE
                binding.tvDevicesHeader.visibility = View.GONE
                
                // Show connected device card with mobile icon
                showConnectedDeviceCard(state.device.deviceName, isWebRTC = false)
                
                if (!wasConnectedOnOpen) {
                    dismissAllowingStateLoss()
                }
            }
            is DuoConnectionState.Error -> {
                binding.tvConnectionStatus.text = state.message
                binding.progressBar.visibility = View.GONE
                binding.tvSearching.visibility = View.GONE
                binding.btnSearchNearby.visibility = View.VISIBLE
                binding.btnSearchNearby.isEnabled = true
                binding.btnDisconnect.visibility = View.GONE
            }
        }
    }
    
    private fun resetToDisconnectedState() {
        binding.tvConnectionStatus.text = getString(R.string.duo_status_disconnected)
        binding.progressBar.visibility = View.GONE
        binding.tvSearching.visibility = View.GONE
        binding.btnSearchNearby.visibility = View.VISIBLE
        binding.btnSearchNearby.isEnabled = true
        binding.btnDisconnect.visibility = View.GONE
        binding.etPartnerUserId.visibility = View.VISIBLE
        binding.btnConnectById.visibility = View.VISIBLE
        binding.layoutPartnerStatus.visibility = View.GONE
        binding.rvDevices.visibility = View.VISIBLE
        binding.tvDevicesHeader.visibility = if (deviceAdapter.itemCount > 0) View.VISIBLE else View.GONE
        binding.cardConnectedDevice.visibility = View.GONE
    }
    
    /**
     * Show the connected device card with mobile icon
     */
    private fun showConnectedDeviceCard(deviceName: String, isWebRTC: Boolean) {
        binding.cardConnectedDevice.visibility = View.VISIBLE
        binding.tvConnectedDeviceName.text = deviceName
        binding.tvConnectedDeviceType.text = if (isWebRTC) {
            getString(R.string.duo_connection_online)
        } else {
            getString(R.string.duo_connection_wifi_direct)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DuoConnectionBottomSheet"

        fun newInstance() = DuoConnectionBottomSheet()
    }
}
