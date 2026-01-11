package com.android.music.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.FileNotFoundException

object AlbumArtUtil {
    
    /**
     * Get album art with fallback strategy:
     * 1. Try MediaStore album art URI
     * 2. Try extracting embedded art from audio file
     * 3. Return null (caller will use placeholder)
     */
    fun getAlbumArt(
        contentResolver: ContentResolver,
        albumArtUri: Uri?,
        filePath: String?
    ): Bitmap? {
        // Try MediaStore album art first
        albumArtUri?.let {
            try {
                contentResolver.openInputStream(it)?.use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: FileNotFoundException) {
                // Album art not found in MediaStore, try embedded art
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Try extracting embedded album art from file
        filePath?.let {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(it)
                val art = retriever.embeddedPicture
                retriever.release()
                
                art?.let { bytes ->
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return null
    }
    
    /**
     * Load album art with Glide, using gradient as fallback
     */
    fun loadAlbumArtWithFallback(
        glideRequest: com.bumptech.glide.RequestManager,
        imageView: android.widget.ImageView,
        song: com.android.music.data.model.Song,
        sizeDp: Int = 48
    ) {
        val gradientFallback = GradientGenerator.generateTexturedGradient(
            imageView.context, song.id, sizeDp
        )
        
        glideRequest
            .load(song.albumArtUri)
            .signature(com.bumptech.glide.signature.ObjectKey("${song.id}_${song.albumArtUri}"))
            .placeholder(gradientFallback)
            .error(gradientFallback)
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .centerCrop()
            .into(imageView)
    }
    
    /**
     * Apply dynamic theming to player bar based on song
     */
    fun applyPlayerBarTheming(
        playerBarContainer: android.view.View,
        song: com.android.music.data.model.Song
    ) {
        val context = playerBarContainer.context
        val scheme = GradientGenerator.getGradientScheme(song.id)
        
        // Create a subtle gradient background using the song's color scheme
        val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            
            // Create a subtle gradient from primary to secondary color with low alpha
            val primaryColor = android.graphics.Color.argb(30, 
                android.graphics.Color.red(scheme.primary), 
                android.graphics.Color.green(scheme.primary), 
                android.graphics.Color.blue(scheme.primary))
            val secondaryColor = android.graphics.Color.argb(15, 
                android.graphics.Color.red(scheme.secondary), 
                android.graphics.Color.green(scheme.secondary), 
                android.graphics.Color.blue(scheme.secondary))
            
            colors = intArrayOf(primaryColor, secondaryColor)
            orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            cornerRadius = 100f * context.resources.displayMetrics.density
        }
        
        // Apply with smooth transition animation
        val currentDrawable = playerBarContainer.background
        if (currentDrawable != null) {
            val transition = android.graphics.drawable.TransitionDrawable(
                arrayOf(currentDrawable, gradientDrawable)
            )
            playerBarContainer.background = transition
            transition.startTransition(300) // 300ms smooth transition
        } else {
            playerBarContainer.background = gradientDrawable
        }
    }
}
