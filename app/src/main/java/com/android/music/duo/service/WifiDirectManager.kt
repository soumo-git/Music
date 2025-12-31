package com.android.music.duo.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.android.music.duo.data.model.DeviceStatus
import com.android.music.duo.data.model.DuoDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages WiFi Direct connections for Duo feature
 */
class WifiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectManager"
    }

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiDirectBroadcastReceiver? = null

    private val _discoveredDevices = MutableStateFlow<List<DuoDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DuoDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _groupInfo = MutableStateFlow<WifiP2pGroup?>(null)
    val groupInfo: StateFlow<WifiP2pGroup?> = _groupInfo.asStateFlow()

    fun initialize() {
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper()) {
            Log.d(TAG, "Channel disconnected")
        }
        registerReceiver()
    }

    private fun registerReceiver() {
        receiver = WifiDirectBroadcastReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        if (wifiP2pManager == null || channel == null) {
            Log.e(TAG, "WiFi P2P not initialized")
            onFailure(-1)
            return
        }
        
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                val errorMsg = when (reason) {
                    WifiP2pManager.ERROR -> "Internal error"
                    WifiP2pManager.P2P_UNSUPPORTED -> "P2P not supported on this device"
                    WifiP2pManager.BUSY -> "System is busy, try again"
                    WifiP2pManager.NO_SERVICE_REQUESTS -> "No service requests added"
                    else -> "Unknown error: $reason"
                }
                Log.e(TAG, "Discovery failed: $errorMsg (code: $reason)")
                onFailure(reason)
            }
        })
    }

    fun stopDiscovery() {
        wifiP2pManager?.stopPeerDiscovery(channel, null)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: DuoDevice, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
                onFailure(reason)
            }
        })
    }

    fun cancelConnect(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        wifiP2pManager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection cancelled")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Cancel connect failed: $reason")
                onFailure(reason)
            }
        })
    }

    fun disconnect(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
                onFailure(reason)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            Log.d(TAG, "Connection info received: groupFormed=${info?.groupFormed}, isGroupOwner=${info?.isGroupOwner}, groupOwnerAddress=${info?.groupOwnerAddress}")
            _connectionInfo.value = info
        }
    }

    @SuppressLint("MissingPermission")
    fun requestGroupInfo() {
        wifiP2pManager?.requestGroupInfo(channel) { group ->
            _groupInfo.value = group
        }
    }

    fun cleanup() {
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        stopDiscovery()
    }

    private fun mapDeviceStatus(status: Int): DeviceStatus {
        return when (status) {
            WifiP2pDevice.AVAILABLE -> DeviceStatus.AVAILABLE
            WifiP2pDevice.INVITED -> DeviceStatus.INVITED
            WifiP2pDevice.CONNECTED -> DeviceStatus.CONNECTED
            WifiP2pDevice.FAILED -> DeviceStatus.FAILED
            WifiP2pDevice.UNAVAILABLE -> DeviceStatus.UNAVAILABLE
            else -> DeviceStatus.UNAVAILABLE
        }
    }

    private inner class WifiDirectBroadcastReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isWifiP2pEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WiFi P2P enabled: ${_isWifiP2pEnabled.value}")
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                        val devices = peers?.deviceList?.map { device ->
                            DuoDevice(
                                deviceName = device.deviceName,
                                deviceAddress = device.deviceAddress,
                                isGroupOwner = device.isGroupOwner,
                                status = mapDeviceStatus(device.status)
                            )
                        } ?: emptyList()
                        _discoveredDevices.value = devices
                        Log.d(TAG, "Peers found: ${devices.size}")
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    Log.d(TAG, "Connection changed action received")
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }

                    Log.d(TAG, "Network info: isConnected=${networkInfo?.isConnected}")
                    if (networkInfo?.isConnected == true) {
                        Log.d(TAG, "Network connected, requesting connection info...")
                        requestConnectionInfo()
                        requestGroupInfo()
                    } else {
                        Log.d(TAG, "Network disconnected")
                        _connectionInfo.value = null
                        _groupInfo.value = null
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device: ${device?.deviceName}")
                }
            }
        }
    }
}
