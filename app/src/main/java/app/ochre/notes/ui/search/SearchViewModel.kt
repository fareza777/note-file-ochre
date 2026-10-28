package app.ochre.notes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.data.settings.SettingsRepository
import app.ochre.notes.domain.FeedFilter
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.domain.SearchQuery
import app.ochre.notes.domain.SearchQueryParser
import app.ochre.notes.domain.TagUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickFilter { PINNED, PHOTOS, VOICE, REMINDERS, ARCHIVED }

data class SearchInput(
    val raw: String = "",
    val quick: Set<QuickFilter> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    initialQuery: String,
) : ViewModel() {

    private val _input = MutableStateFlow(SearchInput(raw = initialQuery))
    val input = _input.asStateFlow()

    private val parsed = _input
        .map { it.raw }
        .debounce(180)
        .map { SearchQueryParser.parse(it) }
        .distinctUntilChanged()

    val query = combine(parsed, _input.map { it.quick }) { base, quick ->
        base.copy(
            pinnedOnly = base.pinnedOnly || quick.contains(QuickFilter.PINNED),
            imageOnly = base.imageOnly || quick.contains(QuickFilter.PHOTOS),
            audioOnly = base.audioOnly || quick.contains(QuickFilter.VOICE),
            reminderOnly = base.reminderOnly || quick.contains(QuickFilter.REMINDERS),
            archivedOnly = base.archivedOnly || quick.contains(QuickFilter.ARCHIVED),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchQuery(raw = initialQuery))

    val results: kotlinx.coroutines.flow.StateFlow<List<MemoUi>> = query
        .flatMapLatest { current ->
            if (current.raw.isBlank() && current.filterLabels.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.observeFeed(FeedFilter.ALL, current, 200)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: kotlinx.coroutines.flow.StateFlow<List<TagUi>> =
        repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches = settingsRepository.settings
        .map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _input.update { it.copy(raw = value) }
    }

    fun setQuery(value: String) {
        _input.update { it.copy(raw = value) }
    }

    fun toggleQuick(filter: QuickFilter) {
        _input.update { current ->
            val next = if (current.quick.contains(filter)) current.quick - filter else current.quick + filter
            current.copy(quick = next)
        }
    }

    fun submit() {
        val raw = _input.value.raw.trim()
        if (raw.isBlank()) return
        viewModelScope.launch { settingsRepository.pushRecentSearch(raw) }
    }

    fun clearRecent() {
        viewModelScope.launch { settingsRepository.clearRecentSearches() }
    }
}