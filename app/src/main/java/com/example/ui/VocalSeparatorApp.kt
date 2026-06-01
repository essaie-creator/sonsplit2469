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

    val outputFormat by viewModel.outputFormat.collectAsStateWithLifecycle()
    val customOutputName by viewModel.customOutputName.collectAsStateWithLifecycle()
    val splitBass by viewModel.splitBass.collectAsStateWithLifecycle()
    val splitMelody by viewModel.splitMelody.collectAsStateWithLifecycle()

    val activeTrack by viewModel.activeTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()

    val vocalVol by viewModel.vocalVolume.collectAsStateWithLifecycle()
    val instVol by viewModel.instrumentalVolume.collectAsStateWithLifecycle()
    val bassVol by viewModel.bassVolume.collectAsStateWithLifecycle()
    val melodyVol by viewModel.melodyVolume.collectAsStateWithLifecycle()
    val isCompareActive by viewModel.isCompareModeActive.collectAsStateWithLifecycle()

    // Color palette definitions for a gorgeous premium Slate dark feel
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = "App Icon",
                            tint = neonTeal,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "AcouSTIC Splitter Pro",
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
                contentPadding = PaddingValues(bottom = if (activeTrack != null) 320.dp else 40.dp)
            ) {
                // Section: App Privacy Philosophy Card
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
                            text = "All voice separation and audio encoding take place strictly offline on-device. Your media is never uploaded to any cloud server.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section: Primary splitter controller Card
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
                                // Clear Picker Button State
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
                                // Audio loaded and Pre-generation Options Panel
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

                                // Custom Output File Name
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Custom Output Base Name",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    OutlinedTextField(
                                        value = customOutputName,
                                        onValueChange = { viewModel.customOutputName.value = it },
                                        placeholder = { Text("Output Prefix...", color = Color.White.copy(alpha = 0.3f)) },
                                        modifier = Modifier.fillMaxWidth().testTag("custom_name_input"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = neonTeal,
                                            unfocusedBorderColor = lightBorder,
                                            focusedContainerColor = darkSlateBg,
                                            unfocusedContainerColor = darkSlateBg
                                        ),
                                        singleLine = true
                                    )
                                }



                                // Interactive Multi-track target checklists
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Select Tracks to Extract",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    
                                    TrackIsolationCheckbox(
                                        title = "Vocals and Instrumentals (Essential)",
                                        subtitle = "Separates Voices and Core Backing tracks",
                                        checked = true,
                                        onCheckedChange = { /* Mandatory */ },
                                        enabled = false,
                                        accentColor = neonTeal
                                    )
                                    TrackIsolationCheckbox(
                                        title = "Rhythm Track (Bass & Drums)",
                                        subtitle = "Stems out the lower-end drive frequencies",
                                        checked = splitBass,
                                        onCheckedChange = { viewModel.splitBass.value = it },
                                        enabled = true,
                                        accentColor = neonTeal
                                    )
                                    TrackIsolationCheckbox(
                                        title = "Melody Track (Ambient Highs)",
                                        subtitle = "Extracts solos, leads, and high-panned elements",
                                        checked = splitMelody,
                                        onCheckedChange = { viewModel.splitMelody.value = it },
                                        enabled = true,
                                        accentColor = neonTeal
                                    )
                                }

                                // Original separation strength config
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Vocal Cancellation Intensity",
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
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Action processing triggering button
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
                                        contentDescription = "Separator trigger"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Separate Selected Audio Paths",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: Live process overlay hud panel
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
                                            text = "Duplicating audio stream to locally buffered sandbox paths to ensure continuous offline read permission...",
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
                                                text = "Analyzing & Parsing Steps...",
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

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                            if (state.estRemainingSeconds != null) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Timer,
                                                        contentDescription = "Time remaining",
                                                        tint = neonTeal.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Remaining: ${state.estRemainingSeconds}s",
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                            StepRow(
                                                label = "Decode input audio tracks into raw samples",
                                                isActive = state.progress > 0.0f,
                                                isCompleted = state.progress >= 0.85f
                                            )
                                            StepRow(
                                                label = "Apply multitrack separating biquads and filters",
                                                isActive = state.progress >= 0.15f,
                                                isCompleted = state.progress >= 0.94f
                                            )
                                            StepRow(
                                                label = "Compile and encode into $outputFormat standard containers",
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
                                                text = "Tracks Split Successfully!",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }
                                        Text(
                                            text = "Separated stems are fully rendered and saved. Trigger the interactive Mixer below to balance playback live!",
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
                                                text = "Separation Failed",
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
                                text = "Choose a song above to isolate your voice elements completely offline!",
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
                                val cleanName = file.originalName.substringBeforeLast(".")
                                val ext = File(file.vocalPath).extension
                                exportAudioLocal(context, File(file.vocalPath), "${cleanName}_Vocals.$ext")
                            },
                            onExportInstrumental = {
                                val cleanName = file.originalName.substringBeforeLast(".")
                                val ext = File(file.instrumentalPath).extension
                                exportAudioLocal(context, File(file.instrumentalPath), "${cleanName}_Instrumental.$ext")
                            },
                            onExportBass = {
                                file.bassPath?.let { path ->
                                    val cleanName = file.originalName.substringBeforeLast(".")
                                    val ext = File(path).extension
                                    exportAudioLocal(context, File(path), "${cleanName}_Bass.$ext")
                                }
                            },
                            onExportMelody = {
                                file.melodyPath?.let { path ->
                                    val cleanName = file.originalName.substringBeforeLast(".")
                                    val ext = File(path).extension
                                    exportAudioLocal(context, File(path), "${cleanName}_Melody.$ext")
                                }
                            },
                            neonTeal = neonTeal,
                            cardBg = cardBg,
                            lightBorder = lightBorder
                        )
                    }
                }
            }

            // Sync Interactive Multi-Track Player Mixer Board
            AnimatedVisibility(
                visible = activeTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                activeTrack?.let { track ->
                    MixerPlaybackPanel(
                        trackName = track.originalName,
                        vocalPath = track.vocalPath,
                        instrumentalPath = track.instrumentalPath,
                        bassPath = track.bassPath,
                        melodyPath = track.melodyPath,
                        isPlaying = isPlaying,
                        vocalVol = vocalVol,
                        instVol = instVol,
                        bassVol = bassVol,
                        melodyVol = melodyVol,
                        progress = playbackProgress,
                        durationMs = durationMs,
                        currentPositionMs = currentPositionMs,
                        onTogglePlay = { viewModel.togglePlayback() },
                        onSeek = { viewModel.seekToFraction(it) },
                        onVocalVolChange = { viewModel.vocalVolume.value = it },
                        onInstrumentVolChange = { viewModel.instrumentalVolume.value = it },
                        onBassVolChange = { viewModel.bassVolume.value = it },
                        onMelodyVolChange = { viewModel.melodyVolume.value = it },
                        onClose = { viewModel.stopPlayback() },
                        isCompareActive = isCompareActive,
                        onToggleCompareMode = { viewModel.toggleCompareMode() },
                        brightPurple = brightPurple,
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
fun FormatSelectionCard(
    format: String,
    label: String,
    subtext: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color,
    cardColor: Color,
    borderColor: Color
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.12f) else cardColor
        ),
        border = BorderStroke(1.dp, if (selected) accentColor else borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                color = if (selected) accentColor else Color.White,
                fontSize = 13.sp
            )
            Text(
                text = subtext,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun TrackIsolationCheckbox(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = accentColor,
                checkmarkColor = Color.White,
                uncheckedColor = Color.White.copy(alpha = 0.3f)
            )
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
    onExportBass: () -> Unit,
    onExportMelody: () -> Unit,
    neonTeal: Color,
    cardBg: Color,
    lightBorder: Color
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val formatLabel = if (file.vocalPath.endsWith(".m4a")) "M4A (AAC)" else "WAV"

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
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                        Text(
                            text = formatLabel,
                            color = neonTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
                            text = { Text("Save Isolated Vocals", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedMenu = false
                                onExportVocal()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Instrumentals (Core)", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                expandedMenu = false
                                onExportInstrumental()
                            }
                        )
                        
                        if (file.bassPath != null) {
                            DropdownMenuItem(
                                text = { Text("Save Bass & Drums", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    expandedMenu = false
                                    onExportBass()
                                }
                            )
                        }
                        
                        if (file.melodyPath != null) {
                            DropdownMenuItem(
                                text = { Text("Save Melodies & Highs", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = neonTeal, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    expandedMenu = false
                                    onExportMelody()
                                }
                            )
                        }

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
    vocalPath: String,
    instrumentalPath: String,
    bassPath: String?,
    melodyPath: String?,
    isPlaying: Boolean,
    vocalVol: Float,
    instVol: Float,
    bassVol: Float,
    melodyVol: Float,
    progress: Float,
    durationMs: Int,
    currentPositionMs: Int,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onVocalVolChange: (Float) -> Unit,
    onInstrumentVolChange: (Float) -> Unit,
    onBassVolChange: (Float) -> Unit,
    onMelodyVolChange: (Float) -> Unit,
    onClose: () -> Unit,
    isCompareActive: Boolean,
    onToggleCompareMode: () -> Unit,
    brightPurple: Color,
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
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DYNAMIC 4-STEM SYNC MIXER",
                        color = neonTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = trackName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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

            // Compare Mode custom switcher inside Mixer Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(darkSlateBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Original Mixed (Before) - click toggles Compare Mode ON
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCompareActive) neonTeal.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { if (!isCompareActive) onToggleCompareMode() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Original Mixed",
                            tint = if (isCompareActive) neonTeal else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Original (Before)",
                            color = if (isCompareActive) neonTeal else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (isCompareActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }

                // Button 2: Isolated Custom Mixer (After) - click toggles Compare Mode OFF
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isCompareActive) brightPurple.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { if (isCompareActive) onToggleCompareMode() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Isolated Stems",
                            tint = if (!isCompareActive) brightPurple else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Split Mix (After)",
                            color = if (!isCompareActive) brightPurple else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (!isCompareActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Waveform Visualizer
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

            // Real-Time 4-Track Mixing Dashboard
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Vocals
                    TrackSliderCard(
                        icon = Icons.Filled.Mic,
                        title = "Voices",
                        volume = vocalVol,
                        onVolumeChange = onVocalVolChange,
                        accentColor = neonTeal,
                        backgroundColor = darkSlateBg,
                        borderColor = lightBorder,
                        enabled = !isCompareActive,
                        modifier = Modifier.weight(1f)
                    )

                    // Core Music
                    TrackSliderCard(
                        icon = Icons.Filled.MusicNote,
                        title = "Instruments",
                        volume = instVol,
                        onVolumeChange = onInstrumentVolChange,
                        accentColor = neonTeal,
                        backgroundColor = darkSlateBg,
                        borderColor = lightBorder,
                        enabled = !isCompareActive,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (bassPath != null || melodyPath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bassPath != null) {
                            TrackSliderCard(
                                icon = Icons.Filled.GraphicEq,
                                title = "Bass/Drums",
                                volume = bassVol,
                                onVolumeChange = onBassVolChange,
                                accentColor = neonTeal,
                                backgroundColor = darkSlateBg,
                                borderColor = lightBorder,
                                enabled = !isCompareActive,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        if (melodyPath != null) {
                            TrackSliderCard(
                                icon = Icons.Filled.MusicNote,
                                title = "Melody/Highs",
                                volume = melodyVol,
                                onVolumeChange = onMelodyVolChange,
                                accentColor = neonTeal,
                                backgroundColor = darkSlateBg,
                                borderColor = lightBorder,
                                enabled = !isCompareActive,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Central Playback Controls
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(50.dp)
                        .background(neonTeal, CircleShape)
                        .testTag("mixer_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Mixer playback toggle",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackSliderCard(
    icon: ImageVector,
    title: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    accentColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1.0f else 0.4f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = alpha))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${(volume * 100).toInt()}%",
                color = accentColor.copy(alpha = alpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) accentColor else Color.Gray,
                activeTrackColor = if (enabled) accentColor else Color.DarkGray,
                inactiveTrackColor = borderColor
            )
        )
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
 * Robust export directly to Music folder using modern MediaStore, auto-detecting
 * correct MIME type and suffix for either lossless WAV or AAC M4A files.
 */
private fun exportAudioLocal(context: Context, file: File, displayName: String) {
    if (!file.exists()) {
        Toast.makeText(context, "Audio source file not found on disk.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val ext = file.extension.lowercase()
        val mimeType = if (ext == "m4a") "audio/mp4" else "audio/wav"
        
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
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

            Toast.makeText(context, "$displayName exported successfully!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Fail to register destination path in MediaStore", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("VocalSeparatorApp", "Audio export failure", e)
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
