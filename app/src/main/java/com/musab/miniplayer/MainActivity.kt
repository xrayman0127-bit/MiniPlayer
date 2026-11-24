
package com.musab.miniplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101016)
private val SurfaceDark = Color(0xFF1D1C27)
private val Accent = Color(0xFFB69CFF)
private val TextPrimary = Color(0xFFF5F2FA)
private val TextSecondary = Color(0xFF9995A7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MiniPlayerApp()
        }
    }
}

@Composable
fun MiniPlayerApp() {
    var isPlaying by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.35f) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MINIPLAYER",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )

                    Text(
                        text = "⋮",
                        color = TextPrimary,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "NOW PLAYING",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF563C91),
                                    Color(0xFFB65F83),
                                    Color(0xFFEFAD78)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF30204F),
                                        Color(0xFF171522)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFE9A1B5),
                                            Color(0xFF7953A7)
                                        )
                                    )
                                )
                        )
                    }

                    Text(
                        text = "M",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Your First Track",
                            color = TextPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Unknown Artist",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = if (isFavorite) "♥" else "♡",
                        color = Accent,
                        fontSize = 30.sp,
                        modifier = Modifier.clickable {
                            isFavorite = !isFavorite
                        }
                    )
                }

                Spacer(modifier = Modifier.height(25.dp))

                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = SurfaceDark
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1:24", color = TextSecondary, fontSize = 12.sp)
                    Text("3:58", color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↻",
                        color = TextSecondary,
                        fontSize = 25.sp
                    )

                    Text(
                        text = "◀",
                        color = TextPrimary,
                        fontSize = 27.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFC6A9FF),
                                        Color(0xFF8E72D9)
                                    )
                                )
                            )
                            .clickable {
                                isPlaying = !isPlaying
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPlaying) "Ⅱ" else "▶",
                            color = Color.White,
                            fontSize = 30.sp
                        )
                    }

                    Text(
                        text = "▶",
                        color = TextPrimary,
                        fontSize = 27.sp
                    )

                    Text(
                        text = "⇄",
                        color = TextSecondary,
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☷", color = TextSecondary, fontSize = 23.sp)
                    Text("♫", color = Accent, fontSize = 23.sp)
                    Text("☰", color = TextSecondary, fontSize = 23.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
