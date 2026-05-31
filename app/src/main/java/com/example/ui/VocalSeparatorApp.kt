package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProcessedFile
import com.example.viewmodel.ProcessingState
import com.example.viewmodel.VocalSeparatorViewModel
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocalSeparatorApp(
    viewModel: VocalSeparatorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val historyFiles by viewModel.historyFiles.collectAsStateWithLifecycle()
    val selectedFileName by viewModel.selectedFileName.collectAsStateWithLifecycle()
    val selectedFileSize by viewModel.selectedFileSize.collectAsStateWithLifecycle()
    val selectedFileUri by viewModel.selectedFileUri.collectAsStateWithLifecycle()
    val separationIntensity by viewModel.separationIntensity.collectAsStateWithLifecycle()

    val activeTrack by viewModel.activeTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()

    val vocalVol by viewModel.vocalVolume.collectAsStateWithLifecycle()
    val instVol by viewModel.instrumentalVolume.collectAsStateWithLifecycle()

    // Color definitions for a gorgeous Slate/Cyan dark premium vibe
    val darkSlateBg = Color(0xFF0F172A)
    val cardBg = Color(0xFF1E293B)
    val neonTeal = Color(0xFF06B6D4)
    val softTeal = Color(0xFF0EA5E9)
    val brightPurple = Color(0xFF8B5CF6)
    val lightBorder = Color(0xFF334155)

    // Standard Audio file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFile(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = "App Icon",
                            tint = neonTeal,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AcouSTIC Vocal Splitter",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkSlateBg,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = darkSlateBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = if (activeTrack != null) 300.dp else 40.dp)
            ) {
                // Section: Trust philosophies
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        softTeal.copy(alpha = 0.15f),
                                        brightPurple.copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Shield Icon",
                                tint = neonTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Zero Fees • Offline-First Privacy",
                                fontWeight = FontWeight.Bold,
                                color = neonTeal,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All voice processing takes place strictly on your device. Your audio is never uploaded to any cloud server.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section: Pick & separate controller
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, lightBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Split New Audio",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            if (selectedFileUri == null) {
                                // Empty state pick button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(darkSlateBg)
                                        .clickable { filePickerLauncher.launch("audio/*") }
                                        .testTag("audio_picker_region"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = "Upload Icon",
                                            tint = neonTeal,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Text(
                                            text = "Tap to pick an audio file",
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Supports MP3, WAV, M4A, AAC, FLAC",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                // Audio file selected view
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(darkSlateBg)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AudioFile,
                                        contentDescription = "Audio file selected",
                                        tint = neonTeal,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFileName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatSize(selectedFileSize),
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearSelectedFile() },
                                        modifier = Modifier.testTag("clear_audio_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove file",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                // Separation strength control
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Vocal Separation Intensity",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "x${"%.1f".format(separationIntensity)}",
                                            color = neonTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = separationIntensity,
                                        onValueChange = { viewModel.separationIntensity.value = it },
                                        valueRange = 0.1f..2.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = neonTeal,
                                            activeTrackColor = neonTeal,
                                            inactiveTrackColor = lightBorder
                                        ),
                                        modifier = Modifier.testTag("strength_slider")
                                    )
                                    Text(
                                        text = if (separationIntensity < 0.8f) {
                                            "Preserves instrument wide stereos, mild vocal dampening"
                                        } else if (separationIntensity > 1.3f) {
                                            "High separation, extreme center cancellation (best vocal removal)"
                                        } else {
                                            "Balanced separation for clean voice track"
                                        },
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Start processing action button
                                Button(
                                    onClick = { viewModel.startProcessing() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("split_audio_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = neonTeal)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Split Vocal & Music Local",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: Processing HUD Overlay
                if (processingState !is ProcessingState.Idle) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, lightBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (val state = processingState) {
                                    is ProcessingState.Copying -> {
                                        Text(
                                            text = "Duplicating Audio...",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().testTag("copy_progress"),
                                            color = neonTeal,
                                            trackColor = lightBorder
                                        )
                                        Text(
                                            text = "Copying media stream locally to bypass Android file provider security sandbox...",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    is ProcessingState.Processing -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Analyzing & Splitting...",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "${(state.progress * 100).toInt()}%",
                                                fontWeight = FontWeight.Bold,
                                                color = neonTeal,
                                                fontSize = 14.sp
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier.fillMaxWidth().testTag("split_progress_bar"),
                                            color = neonTeal,
                                            trackColor = lightBorder
                                        )

                                        // Step status markers
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            StepRow(
                                                label = "Decode input audio streams",
                                                isActive = state.progress > 0.0f,
                                                isCompleted = state.progress >= 0.85f
                                            )
                                            StepRow(
                                                label = "Apply offline biquad voice filters & subtraction",
                                                isActive = state.progress >= 0.15f,
                                                isCompleted = state.progress >= 0.94f
                                            )
                                            StepRow(
                                                label = "Prepend high-fidelity WAV containers",
                                                isActive = state.progress >= 0.94f,
                                                isCompleted = state.progress >= 0.99f
                                            )
                                        }
                                    }
                                    is ProcessingState.Success -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Success",
                                                tint = Color.Green,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Splitting Complete!",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Text(
                                            text = "Separated vocal and instrumental tracks are ready. Open the Mixer Player below to control live music!",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                        Button(
                                            onClick = { viewModel.clearSelectedFile() },
                                            colors = ButtonDefaults.buttonColors(containerColor = lightBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Dismiss Success Panel", color = Color.White)
                                        }
                                    }
                                    is ProcessingState.Error -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Error,
                                                contentDescription = "Error",
                                                tint = Color.Red,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Separation Error",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Text(
                                            text = state.message,
                                            color = Color.Red.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                        Button(
                                            onClick = { viewModel.clearSelectedFile() },
                                            colors = ButtonDefaults.buttonColors(containerColor = lightBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Acknowledge", color = Color.White)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                // Section: Processing History
                item {
                    Text(
                        text = "History & Split Library (${historyFiles.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (historyFiles.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "History Empty",
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Library is empty",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Choose a song above to isolate your first voice track offline!",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(historyFiles) { file ->
                        HistoryTrackCard(
                            file = file,
                            isActive = activeTrack?.id == file.id,
                            isPlaying = isPlaying && activeTrack?.id == file.id,
                            onPlay = { viewModel.loadTrackIntoPlayer(file) },
                            onDelete = { viewModel.deleteHistoryTrack(file) },
                            onExportVocal = {
                                exportWavLocal(context, File(file.vocalPath), "${file.originalName.substringBeforeLast(".")}_Vocals.wav")
                            },
                            onExportInstrumental = {
                                exportWavLocal(context, File(file.instrumentalPath), "${file.originalName.substringBeforeLast(".")}_Instrumental.wav")
                            },
                            neonTeal = neonTeal,
                            cardBg = cardBg,
                            lightBorder = lightBorder
                        )
                    }
                }
            }

            // Sync Interactive Multi-Track Player Mixer
            AnimatedVisibility(
                visible = activeTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                activeTrack?.let { track ->
                    MixerPlaybackPanel(
                        trackName = track.originalName,
                        isPlaying = isPlaying,
                        vocalVol = vocalVol,
                        instVol = instVol,
                        progress = playbackProgress,
                        durationMs = durationMs,
                        currentPositionMs = currentPositionMs,
                        onTogglePlay = { viewModel.togglePlayback() },
                        onSeek = { viewModel.seekToFraction(it) },
                        onVocalVolChange = { viewModel.vocalVolume.value = it },
                        onInstrumentVolChange = { viewModel.instrumentalVolume.value = it },
                        onClose = { viewModel.stopPlayback() },
                        darkSlateBg = darkSlateBg,
                        cardBg = cardBg,
                        neonTeal = neonTeal,
                        lightBorder = lightBorder
                    )
                }
            }
        }
    }
}

@Composable
fun StepRow(label: String, isActive: Boolean, isCompleted: Boolean) {
    val tint = if (isCompleted) Color.Green else if (isActive) Color(0xFF06B6D4) else Color.White.copy(alpha = 0.25f)
    val icon = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isCompleted) Color.White else if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun HistoryTrackCard(
    file: ProcessedFile,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onExportVocal: () -> Unit,
    onExportInstrumental: () -> Unit,
    neonTeal: Color,
    cardBg: Color,
    lightBorder: Color
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${file.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) neonTeal.copy(alpha = 0.08f) else cardBg
        ),
        border = BorderStroke(1.dp, if (isActive) neonTeal.copy(alpha = 0.5f) else lightBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play action circle
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isActive) neonTeal else Color.White.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .testTag("history_play_btn_${file.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Load play track",
                        tint = if (isActive) Color.White else neonTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.originalName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = formatMs(file.durationMs),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                        Text(
                            text = formatSize(file.fileSize),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Export & Delete Dropdown
                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Actions menu",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier.background(cardBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Isolated Vocals (.wav)", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedMenu = false
                                onExportVocal()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Instrumentals (.wav)", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedMenu = false
                                onExportInstrumental()
                            }
                        )
                        HorizontalDivider(color = lightBorder)
                        DropdownMenuItem(
                            text = { Text("Delete Track", color = Color.Red, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MixerPlaybackPanel(
    trackName: String,
    isPlaying: Boolean,
    vocalVol: Float,
    instVol: Float,
    progress: Float,
    durationMs: Int,
    currentPositionMs: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onVocalVolChange: (Float) -> Unit,
    onInstrumentVolChange: (Float) -> Unit,
    onClose: () -> Unit,
    darkSlateBg: Color,
    cardBg: Color,
    neonTeal: Color,
    lightBorder: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, lightBorder)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding() // Ensures bottom soft drawer is high enough on gesture navs
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYNC MULTI-TRACK MIXER",
                        color = neonTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = trackName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close player",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Real Canvas Visualizer Wave
            AudioVisualizer(
                progress = progress,
                isPlaying = isPlaying,
                onSeek = onSeek
            )

            // Timeline slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = neonTeal,
                        activeTrackColor = neonTeal,
                        inactiveTrackColor = lightBorder
                    ),
                    modifier = Modifier.height(24.dp).testTag("mixer_timeline_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(currentPositionMs.toLong()),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatMs(durationMs.toLong()),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }

            // Real-Time Mixer Sliders Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vocal track card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(darkSlateBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp))
                            Text("Voices", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "${(vocalVol * 100).toInt()}%",
                            color = neonTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = vocalVol,
                        onValueChange = onVocalVolChange,
                        modifier = Modifier.testTag("voc_volume_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = neonTeal,
                            activeTrackColor = neonTeal,
                            inactiveTrackColor = lightBorder
                        )
                    )
                }

                // Instrumental track card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(darkSlateBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp))
                            Text("Music", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "${(instVol * 100).toInt()}%",
                            color = neonTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = instVol,
                        onValueChange = onInstrumentVolChange,
                        modifier = Modifier.testTag("inst_volume_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = neonTeal,
                            activeTrackColor = neonTeal,
                            inactiveTrackColor = lightBorder
                        )
                    )
                }
            }

            // Primary Play / Pause controllers
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(56.dp)
                        .background(neonTeal, CircleShape)
                        .testTag("mixer_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Mixer playback toggle",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.2f MB", mb)
    } else {
        String.format("%.1f KB", kb)
    }
}

/**
 * Offline export directly to Music folder using modern MediaStore
 */
private fun exportWavLocal(context: Context, file: File, displayName: String) {
    if (!file.exists()) {
        Toast.makeText(context, "Audio source not found on disk.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AcousticSplitter")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Toast.makeText(context, "$displayName exported to Music directory", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Fail to register destination path in MediaStore", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("VocalSeparatorApp", "WAV export failure", e)
        Toast.makeText(context, "WAV Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
