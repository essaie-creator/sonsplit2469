package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProcessedFile
import com.example.data.ProcessedFileRepository
import com.example.dsp.AudioSeparationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VocalSeparatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProcessedFileRepository
    val historyFiles: StateFlow<List<ProcessedFile>>

    // Processing variables
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    // Selected file descriptor (before processing)
    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    private val _selectedFileName = MutableStateFlow<String>("")
    val selectedFileName: StateFlow<String> = _selectedFileName.asStateFlow()

    private val _selectedFileSize = MutableStateFlow<Long>(0L)
    val selectedFileSize: StateFlow<Long> = _selectedFileSize.asStateFlow()

    // Configurable choices before extracting
    val outputFormat = MutableStateFlow("M4A") // "WAV" or "M4A" (AAC)
    val customOutputName = MutableStateFlow("") // Custom output file name
    val splitBass = MutableStateFlow(true)
    val splitMelody = MutableStateFlow(true)

    // Separators strength configuration
    val separationIntensity = MutableStateFlow(1.0f)

    // Sync Mixer Playback Player variables
    private var vocalPlayer: MediaPlayer? = null
    private var instrumentalPlayer: MediaPlayer? = null
    private var bassPlayer: MediaPlayer? = null
    private var melodyPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    // Volume sliders for live 4-track mixing
    val vocalVolume = MutableStateFlow(1.0f)
    val instrumentalVolume = MutableStateFlow(1.0f)
    val bassVolume = MutableStateFlow(1.0f)
    val melodyVolume = MutableStateFlow(1.0f)

    // Bypass/Compare mode to toggle between original sum-mix (1.0 each) and custom sliders
    val isCompareModeActive = MutableStateFlow(false)

    // Current active track info loaded in player
    private val _activeTrack = MutableStateFlow<ProcessedFile?>(null)
    val activeTrack: StateFlow<ProcessedFile?> = _activeTrack.asStateFlow()

    private var playbackProgressJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProcessedFileRepository(database.processedFileDao())
        historyFiles = repository.allProcessedFiles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe volume state and compare mode changes to adjust player volumes instantly
        viewModelScope.launch {
            vocalVolume.collect { applyVolumes() }
        }
        viewModelScope.launch {
            instrumentalVolume.collect { applyVolumes() }
        }
        viewModelScope.launch {
            bassVolume.collect { applyVolumes() }
        }
        viewModelScope.launch {
            melodyVolume.collect { applyVolumes() }
        }
        viewModelScope.launch {
            isCompareModeActive.collect { applyVolumes() }
        }
    }

    fun applyVolumes() {
        val isBypass = isCompareModeActive.value
        val v = if (isBypass) 1.0f else vocalVolume.value
        val i = if (isBypass) 1.0f else instrumentalVolume.value
        val b = if (isBypass) 1.0f else bassVolume.value
        val m = if (isBypass) 1.0f else melodyVolume.value

        vocalPlayer?.setVolume(v, v)
        instrumentalPlayer?.setVolume(i, i)
        bassPlayer?.setVolume(b, b)
        melodyPlayer?.setVolume(m, m)
    }

    fun toggleCompareMode() {
        isCompareModeActive.value = !isCompareModeActive.value
    }

    fun selectFile(uri: Uri) {
        _selectedFileUri.value = uri
        val entry = getFileInfo(uri, getApplication())
        val originalFullName = entry?.first ?: "Unknown Audio"
        _selectedFileName.value = originalFullName
        _selectedFileSize.value = entry?.second ?: 0L

        // Default custom name is the original name without extension
        val nameWithoutExt = if (originalFullName.contains('.')) {
            originalFullName.substringBeforeLast('.')
        } else {
            originalFullName
        }
        customOutputName.value = nameWithoutExt

        _processingState.value = ProcessingState.Idle
    }

    fun clearSelectedFile() {
        _selectedFileUri.value = null
        _selectedFileName.value = ""
        _selectedFileSize.value = 0L
        customOutputName.value = ""
        _processingState.value = ProcessingState.Idle
    }

    /**
     * Start the separation engine
     */
    fun startProcessing() {
        val uri = _selectedFileUri.value ?: return
        val strength = separationIntensity.value
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch(Dispatchers.Main) {
            _processingState.value = ProcessingState.Copying
            
            // 1. Copy URI file to cache directory to ensure continuous storage permission access
            val cachedFile = withContext(Dispatchers.IO) {
                copyUriToCache(uri, context)
            }

            if (cachedFile == null || !cachedFile.exists()) {
                _processingState.value = ProcessingState.Error("Failed to cache audio source. Make sure you selected a valid file.")
                return@launch
            }

            _processingState.value = ProcessingState.Processing(0f, null)

            val formatChoice = outputFormat.value
            val isBassTrue = splitBass.value
            val isMelodyTrue = splitMelody.value
            val startTime = System.currentTimeMillis()

            // 2. Perform splitting on Dispatchers.Default
            val result = withContext(Dispatchers.Default) {
                val engine = AudioSeparationEngine(context)
                engine.separateAudio(
                    inputPath = cachedFile.absolutePath,
                    vocalStrength = strength,
                    outputFormat = formatChoice,
                    splitBass = isBassTrue,
                    splitMelody = isMelodyTrue,
                    listener = object : AudioSeparationEngine.ProgressListener {
                        override fun onProgress(progress: Float) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val est = if (progress > 0.02f) {
                                val total = (elapsed / progress).toLong()
                                val remaining = (total - elapsed) / 1000
                                remaining.toInt().coerceAtLeast(1)
                            } else {
                                null
                            }
                            _processingState.value = ProcessingState.Processing(progress, est)
                        }

                        override fun onError(error: String) {
                            _processingState.value = ProcessingState.Error(error)
                        }
                    }
                )
            }

            // Clean up cache file
            if (cachedFile.exists()) {
                cachedFile.delete()
            }

            if (result != null) {
                // Determine naming: use custom name if provided, otherwise original
                val userChosenName = customOutputName.value.trim()
                val originalCleanName = if (userChosenName.isNotEmpty()) {
                    val ext = if (formatChoice.uppercase() == "M4A") "m4a" else "wav"
                    "$userChosenName.$ext"
                } else {
                    _selectedFileName.value
                }

                // 3. Save reference in local database (Room)
                val vocFile = File(result.vocalPath)
                val instFile = File(result.instrumentalPath)
                val bassLen = result.bassPath?.let { File(it).length() } ?: 0L
                val melLen = result.melodyPath?.let { File(it).length() } ?: 0L

                val totalFileSize = vocFile.length() + instFile.length() + bassLen + melLen

                val processedFile = ProcessedFile(
                    originalName = originalCleanName,
                    vocalPath = result.vocalPath,
                    instrumentalPath = result.instrumentalPath,
                    bassPath = result.bassPath,
                    melodyPath = result.melodyPath,
                    durationMs = result.durationMs,
                    fileSize = totalFileSize
                )

                withContext(Dispatchers.IO) {
                    val id = repository.insert(processedFile)
                    val fullFile = processedFile.copy(id = id.toInt())
                    withContext(Dispatchers.Main) {
                        _processingState.value = ProcessingState.Success(fullFile)
                        loadTrackIntoPlayer(fullFile)
                    }
                }
            } else {
                if (_processingState.value !is ProcessingState.Error) {
                    _processingState.value = ProcessingState.Error("Splitting failed. The audio file format may be incompatible or corrupted.")
                }
            }
        }
    }

    /**
     * Set up player for the selected file from history or processing success page
     */
    fun loadTrackIntoPlayer(file: ProcessedFile) {
        stopPlayback()
        _activeTrack.value = file
        _durationMs.value = file.durationMs.toInt()
        _playbackProgress.value = 0f
        _currentPositionMs.value = 0
        isCompareModeActive.value = false // reset compare mode on new track selection

        try {
            vocalPlayer = MediaPlayer().apply {
                setDataSource(file.vocalPath)
                prepare()
            }
            instrumentalPlayer = MediaPlayer().apply {
                setDataSource(file.instrumentalPath)
                prepare()
            }
            file.bassPath?.let { path ->
                bassPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            } ?: run { bassPlayer = null }

            file.melodyPath?.let { path ->
                melodyPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            } ?: run { melodyPlayer = null }

            applyVolumes()

        } catch (e: Exception) {
            Log.e("VocalSeparatorViewModel", "Error creating media players", e)
            _processingState.value = ProcessingState.Error("Playback setup failed. Files might have been moved or deleted.")
        }
    }

    fun togglePlayback() {
        val active = _activeTrack.value ?: return
        if (vocalPlayer == null || instrumentalPlayer == null) {
            loadTrackIntoPlayer(active)
        }

        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        try {
            vocalPlayer?.start()
            instrumentalPlayer?.start()
            bassPlayer?.start()
            melodyPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            Log.e("VocalSeparatorViewModel", "Error starting players", e)
        }
    }

    fun pausePlayback() {
        try {
            vocalPlayer?.pause()
            instrumentalPlayer?.pause()
            bassPlayer?.pause()
            melodyPlayer?.pause()
            _isPlaying.value = false
            playbackProgressJob?.cancel()
        } catch (e: Exception) {
            Log.e("VocalSeparatorViewModel", "Error pausing players", e)
        }
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        _isPlaying.value = false
        _currentPositionMs.value = 0
        _playbackProgress.value = 0f
        
        try {
            vocalPlayer?.stop()
            vocalPlayer?.release()
        } catch (ignored: Exception) {}
        try {
            instrumentalPlayer?.stop()
            instrumentalPlayer?.release()
        } catch (ignored: Exception) {}
        try {
            bassPlayer?.stop()
            bassPlayer?.release()
        } catch (ignored: Exception) {}
        try {
            melodyPlayer?.stop()
            melodyPlayer?.release()
        } catch (ignored: Exception) {}

        vocalPlayer = null
        instrumentalPlayer = null
        bassPlayer = null
        melodyPlayer = null
    }

    fun seekToFraction(fraction: Float) {
        val total = _durationMs.value
        if (total <= 0) return
        val targetMs = (fraction * total).toInt().coerceIn(0, total)
        seekToMs(targetMs)
    }

    fun seekToMs(targetMs: Int) {
        _currentPositionMs.value = targetMs
        val total = _durationMs.value
        if (total > 0) {
            _playbackProgress.value = targetMs.toFloat() / total.toFloat()
        }
        viewModelScope.launch {
            try {
                vocalPlayer?.seekTo(targetMs)
                instrumentalPlayer?.seekTo(targetMs)
                bassPlayer?.seekTo(targetMs)
                melodyPlayer?.seekTo(targetMs)
            } catch (e: Exception) {
                Log.e("VocalSeparatorViewModel", "Error seeking players", e)
            }
        }
    }

    private fun startProgressTracker() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch(Dispatchers.Main) {
            while (_isPlaying.value) {
                val currentLocal = vocalPlayer?.currentPosition ?: 0
                val total = _durationMs.value
                _currentPositionMs.value = currentLocal
                if (total > 0) {
                    _playbackProgress.value = currentLocal.toFloat() / total.toFloat()
                    if (currentLocal >= total - 100) {
                        seekToFraction(0f)
                        pausePlayback()
                    }
                }
                delay(100)
            }
        }
    }

    fun deleteHistoryTrack(file: ProcessedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fVoc = File(file.vocalPath)
                if (fVoc.exists()) fVoc.delete()
                val fInst = File(file.instrumentalPath)
                if (fInst.exists()) fInst.delete()
                
                file.bassPath?.let {
                    val f = File(it)
                    if (f.exists()) f.delete()
                }
                file.melodyPath?.let {
                    val f = File(it)
                    if (f.exists()) f.delete()
                }
            } catch (e: Exception) {
                Log.e("VocalSeparatorViewModel", "Failed to delete files from disk", e)
            }

            // Remove from DB
            repository.delete(file)

            withContext(Dispatchers.Main) {
                if (_activeTrack.value?.id == file.id) {
                    stopPlayback()
                    _activeTrack.value = null
                }
            }
        }
    }

    private fun getFileInfo(uri: Uri, context: Context): Pair<String, Long>? {
        var name = "Audio File"
        var size = 0L
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex) ?: "Audio File"
                    }
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("VocalSeparatorViewModel", "Error picking file info from resolver", e)
            if (uri.scheme == "file") {
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    name = file.name
                    size = file.length()
                }
            } else {
                name = uri.lastPathSegment ?: "Audio File"
            }
        }
        return Pair(name, size)
    }

    private fun copyUriToCache(uri: Uri, context: Context): File? {
        val resolver = context.contentResolver
        val info = getFileInfo(uri, context)
        val name = info?.first ?: "${System.currentTimeMillis()}_input"
        val ext = name.substringAfterLast('.', "mp3")
        val cacheFile = File(context.cacheDir, "input_source.$ext")

        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            return cacheFile
        } catch (e: Exception) {
            Log.e("VocalSeparatorViewModel", "Failed to duplicate URI content to cache: ", e)
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}

sealed interface ProcessingState {
    object Idle : ProcessingState
    object Copying : ProcessingState
    data class Processing(val progress: Float, val estRemainingSeconds: Int? = null) : ProcessingState
    data class Success(val file: ProcessedFile) : ProcessingState
    data class Error(val message: String) : ProcessingState
}
