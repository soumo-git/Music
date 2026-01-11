package com.android.music.equalizer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.music.R
import com.android.music.databinding.ActivityEqualizerBinding
import com.android.music.equalizer.data.model.EqualizerPreset
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for the Equalizer UI
 * Provides a professional, production-grade equalizer interface
 */
class EqualizerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEqualizerBinding
    private val viewModel: EqualizerViewModel by viewModels()
    
    private val bandViews = mutableListOf<BandViewHolder>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeEqualizer()
        setupUI()
        observeState()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun initializeEqualizer() {
        // Use the EqualizerManager to ensure consistent behavior

        // The engine should already be initialized by MusicService
        // Just initialize the ViewModel with the current state
        viewModel.initialize(0) // Will use existing engine state
    }
    
    private fun setupUI() {
        // Enable switch
        binding.switchEqualizer.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
            updateUIEnabled(isChecked)
        }
        
        // Bass boost slider
        binding.sliderBassBoost.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setBassBoost(value.toInt())
            }
            binding.tvBassBoostValue.text = "${(value / 10).toInt()}%"
        }
        
        // Virtualizer slider
        binding.sliderVirtualizer.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setVirtualizer(value.toInt())
            }
            binding.tvVirtualizerValue.text = "${(value / 10).toInt()}%"
        }
        
        // Reset button
        binding.btnReset.setOnClickListener {
            viewModel.reset()
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUI(state)
            }
        }
        
        lifecycleScope.launch {
            viewModel.presets.collectLatest { presets ->
                setupPresetChips(presets)
            }
        }
    }
    
    private fun updateUI(state: EqualizerUiState) {
        // Update switch
        binding.switchEqualizer.isChecked = state.isEnabled
        updateUIEnabled(state.isEnabled)
        
        // Setup band sliders if not already done
        if (bandViews.isEmpty() && state.isInitialized) {
            setupBandSliders(state)
        }
        
        // Update band levels
        state.bandLevels.forEachIndexed { index, level ->
            if (index < bandViews.size) {
                bandViews[index].updateLevel(level, state.minBandLevel, state.maxBandLevel)
            }
        }
        
        // Update bass boost
        binding.sliderBassBoost.value = state.bassBoost.toFloat()
        binding.tvBassBoostValue.text = "${state.bassBoost / 10}%"
        
        // Update virtualizer
        binding.sliderVirtualizer.value = state.virtualizer.toFloat()
        binding.tvVirtualizerValue.text = "${state.virtualizer / 10}%"
        
        // Show/hide bass boost and virtualizer cards
        binding.cardBassBoost.visibility = if (state.isBassBoostSupported) View.VISIBLE else View.GONE
        binding.cardVirtualizer.visibility = if (state.isVirtualizerSupported) View.VISIBLE else View.GONE
        
        // Update selected preset chip
        updateSelectedPreset(state.currentPresetId)
    }
    
    private fun updateUIEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.5f
        
        binding.chipGroupPresets.alpha = alpha
        binding.bandSlidersContainer.alpha = alpha
        binding.cardBassBoost.alpha = alpha
        binding.cardVirtualizer.alpha = alpha
        
        // Enable/disable interactions
        binding.chipGroupPresets.isEnabled = enabled
        bandViews.forEach { it.setEnabled(enabled) }
        binding.sliderBassBoost.isEnabled = enabled
        binding.sliderVirtualizer.isEnabled = enabled
    }

    
    private fun setupBandSliders(state: EqualizerUiState) {
        binding.bandSlidersContainer.removeAllViews()
        bandViews.clear()
        
        for (i in 0 until state.numberOfBands) {
            val bandView = LayoutInflater.from(this)
                .inflate(R.layout.item_eq_band, binding.bandSlidersContainer, false)
            
            val holder = BandViewHolder(bandView, i, state.bandFrequencies.getOrElse(i) { "" })
            holder.setOnLevelChangeListener { band, level ->
                viewModel.setBandLevel(band, level)
            }
            
            bandViews.add(holder)
            binding.bandSlidersContainer.addView(bandView)
        }
    }
    
    private fun setupPresetChips(presets: List<EqualizerPreset>) {
        binding.chipGroupPresets.removeAllViews()
        
        presets.forEach { preset ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = preset.name
                tag = preset.id
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.cardBackground)
                setTextColor(resources.getColorStateList(R.color.chip_text_color, theme))
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.textSecondary)
                
                setOnClickListener {
                    viewModel.applyPreset(preset)
                }
            }
            binding.chipGroupPresets.addView(chip)
        }
    }
    
    private fun updateSelectedPreset(presetId: String) {
        for (i in 0 until binding.chipGroupPresets.childCount) {
            val chip = binding.chipGroupPresets.getChildAt(i) as? Chip
            chip?.isChecked = chip.tag == presetId
        }
    }
    
    /**
     * ViewHolder for individual EQ band
     */
    class BandViewHolder(
        view: View,
        private val bandIndex: Int,
        frequency: String
    ) {
        private val tvValue: TextView = view.findViewById(R.id.tvBandValue)
        private val seekBar: SeekBar = view.findViewById(R.id.seekBarBand)
        private val tvFrequency: TextView = view.findViewById(R.id.tvBandFrequency)
        
        private var onLevelChangeListener: ((Int, Int) -> Unit)? = null
        private var minLevel = -1500
        private var maxLevel = 1500
        
        init {
            tvFrequency.text = frequency
            
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val level = progressToLevel(progress)
                        updateValueText(level)
                        onLevelChangeListener?.invoke(bandIndex, level)
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        
        fun setOnLevelChangeListener(listener: (Int, Int) -> Unit) {
            onLevelChangeListener = listener
        }
        
        fun updateLevel(level: Int, min: Int, max: Int) {
            minLevel = min
            maxLevel = max
            seekBar.progress = levelToProgress(level)
            updateValueText(level)
        }
        
        fun setEnabled(enabled: Boolean) {
            seekBar.isEnabled = enabled
        }
        
        private fun levelToProgress(level: Int): Int {
            // Convert millibels to 0-100 progress
            val range = maxLevel - minLevel
            return ((level - minLevel) * 100 / range).coerceIn(0, 100)
        }
        
        private fun progressToLevel(progress: Int): Int {
            // Convert 0-100 progress to millibels
            val range = maxLevel - minLevel
            return minLevel + (progress * range / 100)
        }
        
        private fun updateValueText(level: Int) {
            val db = level / 100f
            tvValue.text = when {
                db > 0 -> "+${String.format("%.1f", db)}dB"
                db < 0 -> "${String.format("%.1f", db)}dB"
                else -> "0dB"
            }
        }
    }
}
