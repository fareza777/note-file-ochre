package app.ochre.notes.ui.editor

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.data.local.AttachmentEntity
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.data.media.AttachmentStore
import app.ochre.notes.data.media.AudioRecorder
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.domain.OutgoingLinkUi
import app.ochre.notes.notifications.ReminderScheduler
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorAttachment(
    val uid: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val waveform: List<Float>,
    val file: File?,
    val thumbnail: File?,
    val staged: Boolean,
)

data class EditorState(
    val uid: String? = null,
    val content: String = "",
    val initialized: Boolean = false,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val memo: MemoUi? = null,
    val staged: List<EditorAttachment> = emptyList(),
    val error: String? = null,
    val importing: Int = 0,
) {
    val attachments: List<EditorAttachment>
        get() = memo?.attachments?.map { attachment ->
            EditorAttachment(
                uid = attachment.uid,
                kind = attachment.kind,
                mimeType = attachment.mimeType,
                originalName = attachment.originalName,
                sizeBytes = attachment.sizeBytes,
                width = attachment.width,
                height = attachment.height,
                durationMs = attachment.durationMs,
                waveform = attachment.waveform,
                file = attachment.file,
                thumbnail = attachment.thumbnail,
                staged = false,
            )
        }.orEmpty() + staged

    val title: String
        get() = if (content.isBlank()) "" else MarkdownUtils.extractTitle(content)
}

data class RecordingState(
    val active: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = emptyList(),
    val error: String? = null,
)

class EditorViewModel(
    private val repository: NoteRepository,
    private val attachmentStore: AttachmentStore,
    private val contentResolver: ContentResolver,
    private val initialUid: String?,
    context: Context,
    private val reminderScheduler: ReminderScheduler,
    initialContent: String? = null,
) : ViewModel() {

    private val draftUid = UUID.randomUUID().toString()
    private val recorder = AudioRecorder(context)
    private var amplitudeJob: Job? = null

    private val _recording = MutableStateFlow(RecordingState())
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()

    private val _state = MutableStateFlow(
        EditorState(
            uid = initialUid,
            initialized = initialUid == null,
            content = initialContent.orEmpty(),
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _uid = MutableStateFlow(initialUid)
    private val _titleNorm = MutableStateFlow("")

    val backlinks = combine(_uid, _titleNorm) { uid, title -> uid to title }
        .flatMapLatest { (uid, title) ->
            if (uid == null || title.isBlank() || title == NoteRepository.UNTITLED) {
                flowOf(emptyList())
            } else {
                repository.observeBacklinks(title)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoingLinks = _uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList<OutgoingLinkUi>()) else repository.observeOutgoingLinks(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var observingUid: String? = null

    init {
        if (initialUid != null) startObserving(initialUid)
    }

    fun onContentChange(content: String) {
        if (content == _state.value.content) return
        _state.update { it.copy(content = content) }
        scheduleAutosave()
    }

    fun toggleChecklist(lineIndex: Int) {
        onContentChange(MarkdownUtils.toggleChecklist(_state.value.content, lineIndex))
    }

    fun flush(onSaved: (() -> Unit)? = null) {
        saveJob?.cancel()
        saveJob = null
        viewModelScope.launch {
            persist()
            onSaved?.invoke()
        }
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importing = it.importing + uris.size) }
            uris.forEach { uri ->
                runCatching {
                    val mime = contentResolver.getType(uri).orEmpty()
                    addAttachment(uri, if (mime.startsWith("image/")) AttachmentKind.IMAGE else AttachmentKind.FILE)
                }.onFailure { error ->
                    messages.trySend(error.message ?: "Could not add attachment")
                }
            }
            _state.update { it.copy(importing = (it.importing - uris.size).coerceAtLeast(0)) }
        }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importing = it.importing + uris.size) }
            uris.forEach { uri ->
                runCatching { addAttachment(uri, AttachmentKind.FILE) }
                    .onFailure { messages.trySend(it.message ?: "Could not add file") }
            }
            _state.update { it.copy(importing = (it.importing - uris.size).coerceAtLeast(0)) }
        }
    }

    fun addRecording(file: File, durationMs: Long, waveform: List<Float>) {
        viewModelScope.launch {
            runCatching {
                val wave = waveform.take(96).joinToString(",") { "%.2f".format(it) }
                val existing = _state.value.uid
                if (existing != null) {
                    repository.attachRecording(existing, file, durationMs, wave)
                } else {
                    val entity = repository.stageRecording(file, draftUid, durationMs, wave)
                    _state.update { it.copy(staged = it.staged + entity.toEditor(staged = true)) }
                }
            }.onFailure { messages.trySend(it.message ?: "Could not save recording") }
        }
    }

    fun addSketch(file: File) {
        viewModelScope.launch {
            runCatching {
                val existing = _state.value.uid
                if (existing != null) {
                    repository.attachSketch(existing, file)
                } else {
                    val entity = repository.stageSketch(file, draftUid)
                    _state.update { it.copy(staged = it.staged + entity.toEditor(staged = true)) }
                }
            }.onFailure {
                file.delete()
                messages.trySend(it.message ?: "Could not add sketch")
            }
        }
    }

    fun startRecording() {
        if (recorder.isRecording) return
        runCatching { recorder.start() }.onFailure {
            val message = it.message ?: "Microphone unavailable"
            _recording.value = RecordingState(error = message)
            messages.trySend(message)
            return
        }
        _recording.value = RecordingState(active = true)
        amplitudeJob = viewModelScope.launch {
            val bars = mutableListOf<Float>()
            while (isActive && recorder.isRecording) {
                val raw = (recorder.maxAmplitude() / 32767f).coerceIn(0f, 1f)
                bars += sqrt(raw).coerceAtLeast(0.06f)
                _recording.update {
                    it.copy(elapsedMs = recorder.elapsedMillis(), amplitudes = bars.takeLast(240))
                }
                if (recorder.elapsedMillis() >= MAX_RECORDING_MS) {
                    stopRecording()
                    break
                }
                delay(60)
            }
        }
    }

    fun stopRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        val bars = _recording.value.amplitudes
        val result = recorder.stop()
        _recording.value = RecordingState()
        if (result == null) {
            messages.trySend("That was too short to keep")
            return
        }
        addRecording(result.file, result.durationMs, resample(bars, 64))
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        recorder.cancel()
        _recording.value = RecordingState()
    }

    fun removeAttachment(uid: String) {
        viewModelScope.launch {
            val staged = _state.value.staged.firstOrNull { it.uid == uid }
            if (staged != null) {
                _state.update { it.copy(staged = it.staged.filterNot { attachment -> attachment.uid == uid }) }
                runCatching { attachmentStore.deleteFiles(staged.toEntity()) }
                runCatching { File(attachmentStore.thumbnailFor(uid)?.path ?: "").delete() }
            } else {
                repository.removeAttachment(uid)
            }
        }
    }

    fun setReminder(fireAt: Long) {
        viewModelScope.launch {
            persist()
            val uid = _state.value.uid ?: return@launch
            val reminder = repository.setReminder(uid, fireAt)
            reminderScheduler.schedule(reminder.uid, reminder.fireAt)
            messages.trySend("Reminder set")
        }
    }

    fun clearReminder(uid: String) {
        viewModelScope.launch {
            repository.removeReminder(uid)
            reminderScheduler.cancel(uid)
        }
    }

    fun setCapsule(until: Long?) {
        viewModelScope.launch {
            persist()
            val uid = _state.value.uid ?: return@launch
            repository.setCapsule(uid, until)
            messages.trySend(if (until == null) "Capsule unsealed" else "Capsule sealed")
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            persist()
            val uid = _state.value.uid ?: return@launch
            repository.togglePin(uid)
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            persist()
            val uid = _state.value.uid ?: return@launch
            repository.toggleArchive(uid)
            messages.trySend("Moved to archive")
        }
    }

    fun trash(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch {
            persist()
            val uid = _state.value.uid
            if (uid != null) repository.trash(uid)
            onDone()
        }
    }

    fun duplicate(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            saveJob = null
            persist()
            val uid = _state.value.uid ?: return@launch
            runCatching { repository.duplicateMemo(uid) }
                .onSuccess { copyUid ->
                    if (copyUid != null) {
                        messages.trySend("Note duplicated")
                        onCreated(copyUid)
                    } else {
                        messages.trySend("Could not duplicate note")
                    }
                }
                .onFailure { messages.trySend(it.message ?: "Could not duplicate note") }
        }
    }

    fun createLinkedNote(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val uid = repository.createLinkedMemo(title)
            onCreated(uid)
        }
    }

    private fun scheduleAutosave() {
        saveJob?.cancel()
        if (_state.value.uid == null && _state.value.content.isBlank() && _state.value.staged.isEmpty()) return
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY)
            persist()
        }
    }

    private suspend fun addAttachment(uri: Uri, kind: AttachmentKind) {
        val existing = _state.value.uid
        if (existing != null) {
            repository.attach(existing, uri, kind)
        } else {
            val entity = repository.stageAttachment(uri, kind, draftUid)
            _state.update { it.copy(staged = it.staged + entity.toEditor(staged = true)) }
        }
    }

    private suspend fun persist() {
        saveMutex.withLock {
            val snapshot = _state.value
            val uid = snapshot.uid ?: ensureMemo()
            if (uid == null) {
                _state.update { it.copy(saving = false) }
                return
            }
            if (snapshot.uid != null && snapshot.content == snapshot.memo?.content && snapshot.staged.isEmpty()) return
            _state.update { it.copy(saving = true) }
            try {
                repository.updateContent(uid, snapshot.content)
                _titleNorm.value = MarkdownUtils.parse(snapshot.content).titleNorm
                _state.update { it.copy(saving = false, savedAt = System.currentTimeMillis(), error = null) }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(saving = false, error = error.message ?: "Could not save note")
                }
            }
        }
    }

    private suspend fun ensureMemo(): String? {
        val snapshot = _state.value
        snapshot.uid?.let { return it }
        if (snapshot.content.isBlank() && snapshot.staged.isEmpty()) return null
        val uid = repository.createMemoWithAttachments(
            content = snapshot.content,
            staged = snapshot.staged.map { it.toEntity() },
        )
        _state.update { it.copy(uid = uid, staged = emptyList()) }
        _uid.value = uid
        _titleNorm.value = MarkdownUtils.parse(snapshot.content).titleNorm
        startObserving(uid)
        return uid
    }

    private fun startObserving(uid: String) {
        if (observingUid == uid) return
        observingUid = uid
        viewModelScope.launch {
            repository.observeMemo(uid).collect { memo ->
                if (memo == null) return@collect
                _state.update { current ->
                    if (!current.initialized) {
                        current.copy(
                            content = memo.content,
                            initialized = true,
                            memo = memo,
                            savedAt = memo.updatedAt,
                        )
                    } else {
                        current.copy(memo = memo)
                    }
                }
                _titleNorm.value = memo.titleNorm
            }
        }
    }

    private fun AttachmentEntity.toEditor(staged: Boolean): EditorAttachment = EditorAttachment(
        uid = uid,
        kind = kind,
        mimeType = mimeType,
        originalName = originalName,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        waveform = waveform?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }.orEmpty(),
        file = attachmentStore.fileFor(this),
        thumbnail = attachmentStore.thumbnailFor(uid),
        staged = staged,
    )

    private fun EditorAttachment.toEntity(): AttachmentEntity = AttachmentEntity(
        uid = uid,
        memoUid = draftUid,
        kind = kind,
        fileName = file?.name ?: "$uid.jpg",
        originalName = originalName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        waveform = waveform.takeIf { it.isNotEmpty() }?.joinToString(",") { "%.2f".format(it) },
        createdAt = System.currentTimeMillis(),
    )

    override fun onCleared() {
        amplitudeJob?.cancel()
        if (recorder.isRecording) recorder.cancel()
        super.onCleared()
    }

    private fun resample(values: List<Float>, size: Int): List<Float> {
        if (values.isEmpty()) return emptyList()
        return List(size) { index ->
            val start = index * values.size / size
            val end = ((index + 1) * values.size / size).coerceAtLeast(start + 1).coerceAtMost(values.size)
            var peak = 0f
            for (i in start until end) peak = max(peak, values[i])
            peak
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY = 800L
        const val MAX_RECORDING_MS = 10 * 60_000L
    }
}