package com.android.music.duo.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

data class Particle(
    val id: Int,
    val x: Float,
    val y: Float,
    val char: Char,
    val speed: Float,
    val size: Int,
    val opacity: Float
)

@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier,
    particleText: String = "DuoMusicDuoMusicDuoMusicDuoMusicDuoMusic",
    particleColor: Color = Color(0xFF04FF00),
    backgroundColor: Color = Color.Black
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val particles = remember {
        (0..80).map { id ->
            Particle(
                id = id,
                x = Random.nextFloat() * screenWidth,
                y = Random.nextFloat() * -300f,
                char = particleText[Random.nextInt(particleText.length)],
                speed = Random.nextFloat() * 1.2f + 0.5f,
                size = Random.nextInt(10, 16),
                opacity = Random.nextFloat() * 0.6f + 0.2f
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = backgroundColor)
    ) {
        particles.forEach { particle ->
            AnimatedParticle(
                particle = particle,
                screenHeight = screenHeight.toFloat(),
                particleColor = particleColor
            )
        }
    }
}

@Composable
fun AnimatedParticle(
    particle: Particle,
    screenHeight: Float,
    particleColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle_${particle.id}")
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = particle.y,
        targetValue = screenHeight + 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (15000 / particle.speed).toInt(),
                easing = LinearEasing
            )
        ),
        label = "yOffset_${particle.id}"
    )

    Box(
        modifier = Modifier
            .offset(x = particle.x.dp, y = yOffset.dp)
            .alpha(particle.opacity * 0.7f)
    ) {
        Text(
            text = particle.char.toString(),
            style = TextStyle(
                fontSize = particle.size.sp,
                fontFamily = FontFamily.Monospace,
                color = particleColor,
                letterSpacing = 2.sp
            )
        )
    }
}
