package app.ochre.notes.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.data.settings.SettingsRepository
import app.ochre.notes.domain.FeedFilter
import app.ochre.notes.domain.FeedOrder
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.domain.SearchQuery
import app.ochre.notes.domain.TagUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(FeedFilter.ALL)
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val order = MutableStateFlow(FeedOrder.NEWEST)

    val selectedFilter = filter.asStateFlow()
    val selectedOrder = order.asStateFlow()

    val notes: kotlinx.coroutines.flow.StateFlow<List<MemoUi>> =
        combine(filter, limit, order) { selected, size, sort -> Triple(selected, size, sort) }
            .flatMapLatest { (selected, size, sort) -> repository.observeFeed(selected, SearchQuery(), size, sort) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: kotlinx.coroutines.flow.StateFlow<List<TagUi>> =
        repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCount: kotlinx.coroutines.flow.StateFlow<Int> =
        repository.observeActiveCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _echo = MutableStateFlow<List<MemoUi>>(emptyList())
    val echo = _echo.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    init {
        refreshEcho()
    }

    fun setFilter(next: FeedFilter) {
        if (filter.value == next) return
        filter.value = next
        limit.value = PAGE_SIZE
    }

    fun setOrder(next: FeedOrder) {
        if (order.value == next) return
        order.value = next
        limit.value = PAGE_SIZE
    }

    fun loadMore() {
        if (notes.value.size >= limit.value) limit.value += PAGE_SIZE
    }

    fun refreshEcho() {
        viewModelScope.launch {
            _echo.value = repository.echoPicks()
        }
    }

    fun togglePin(uid: String) = viewModelScope.launch { repository.togglePin(uid) }

    fun toggleArchive(uid: String) = viewModelScope.launch {
        repository.toggleArchive(uid)
        messages.trySend("Moved to archive")
    }

    fun trash(uid: String) = viewModelScope.launch {
        repository.trash(uid)
        messages.trySend("Moved to trash")
    }

    fun setPinned(uids: Collection<String>, pinned: Boolean) = viewModelScope.launch {
        uids.forEach { repository.setPinned(it, pinned) }
        messages.trySend(if (pinned) "Pinned ${countLabel(uids.size)}" else "Unpinned ${countLabel(uids.size)}")
    }

    fun setArchived(uids: Collection<String>, archived: Boolean) = viewModelScope.launch {
        uids.forEach { repository.setArchived(it, archived) }
        messages.trySend(if (archived) "Archived ${countLabel(uids.size)}" else "Unarchived ${countLabel(uids.size)}")
    }

    fun addTag(uids: Collection<String>, rawTag: String) = viewModelScope.launch {
        val display = rawTag.trim().removePrefix("#")
            .replace(Regex("[^\\p{L}\\p{N}_-]+"), "-")
            .trim('-')
        if (display.isBlank()) {
            messages.trySend("Tag needs a name")
            return@launch
        }
        var tagged = 0
        uids.forEach { uid -> if (repository.addTag(uid, display)) tagged++ }
        messages.trySend(
            if (tagged == 0) "Already tagged" else "Tagged ${countLabel(tagged)} with #$display",
        )
    }

    fun trashMany(uids: Collection<String>) = viewModelScope.launch {
        uids.forEach { repository.trash(it) }
        messages.trySend("Moved ${countLabel(uids.size)} to trash")
    }

    private fun countLabel(count: Int): String = if (count == 1) "1 note" else "$count notes"

    fun keepEcho(uid: String) = viewModelScope.launch {
        repository.keepEcho(uid)
        messages.trySend("Kept. We will bring it back later.")
        refreshEcho()
    }

    fun dismissEcho(uid: String) = viewModelScope.launch {
        repository.dismissEcho(uid)
        refreshEcho()
    }

    fun markOnboardingComplete() = viewModelScope.launch { settingsRepository.completeOnboarding() }

    private companion object {
        const val PAGE_SIZE = 60
    }
}