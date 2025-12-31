package com.android.music.duo.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.music.R
import com.android.music.databinding.ItemDuoDeviceBinding
import com.android.music.duo.data.model.DeviceStatus
import com.android.music.duo.data.model.DuoDevice

class DuoDeviceAdapter(
    private val onDeviceClick: (DuoDevice) -> Unit
) : ListAdapter<DuoDevice, DuoDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDuoDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDuoDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeviceClick(getItem(position))
                }
            }
        }

        fun bind(device: DuoDevice) {
            binding.tvDeviceName.text = device.deviceName
            binding.tvDeviceStatus.text = getStatusText(device.status)
            binding.tvDeviceStatus.setTextColor(getStatusColor(device.status))
            
            // Update icon based on status
            val iconTint = when (device.status) {
                DeviceStatus.CONNECTED -> itemView.context.getColor(R.color.colorSuccess)
                DeviceStatus.INVITED -> itemView.context.getColor(R.color.colorWarning)
                else -> itemView.context.getColor(R.color.colorPrimary)
            }
            binding.ivDeviceIcon.setColorFilter(iconTint)
        }

        private fun getStatusText(status: DeviceStatus): String {
            return when (status) {
                DeviceStatus.AVAILABLE -> "Available"
                DeviceStatus.INVITED -> "Invitation sent..."
                DeviceStatus.CONNECTED -> "Connected"
                DeviceStatus.FAILED -> "Connection failed"
                DeviceStatus.UNAVAILABLE -> "Unavailable"
            }
        }

        private fun getStatusColor(status: DeviceStatus): Int {
            return when (status) {
                DeviceStatus.AVAILABLE -> itemView.context.getColor(R.color.colorPrimary)
                DeviceStatus.CONNECTED -> itemView.context.getColor(R.color.colorSuccess)
                DeviceStatus.INVITED -> itemView.context.getColor(R.color.colorWarning)
                DeviceStatus.FAILED -> itemView.context.getColor(R.color.colorError)
                DeviceStatus.UNAVAILABLE -> itemView.context.getColor(R.color.textSecondary)
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<DuoDevice>() {
        override fun areItemsTheSame(oldItem: DuoDevice, newItem: DuoDevice): Boolean {
            return oldItem.deviceAddress == newItem.deviceAddress
        }

        override fun areContentsTheSame(oldItem: DuoDevice, newItem: DuoDevice): Boolean {
            return oldItem == newItem
        }
    }
}
