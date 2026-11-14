package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.ReaderDatabase
import com.example.data.model.BookFormat
import com.example.data.model.ParsedBookContent
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.data.model.SearchMatch
import com.example.data.model.SortOrder
import com.example.data.model.WordDefinition
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ReaderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.RECENT,
    val selectedFormat: BookFormat? = null,
    val isImporting: Boolean = false,
    val totalReadingSeconds: Long = 0L,
    val completedBooksCount: Int = 0
)

data class ActiveReaderUiState(
    val isLoading: Boolean = false,
    val book: BookEntity? = null,
    val content: ParsedBookContent? = null,
    val currentParagraphIndex: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val inBookSearchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val selectedWordDefinition: WordDefinition? = null,
    val selectedParagraphForAction: Int? = null,
    val isTtsSpeaking: Boolean = false
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ReaderDatabase.getInstance(application)
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = ReaderRepository(
        context = application,
        dao = database.readerDao(),
        settingsDataStore = settingsDataStore
    )

    val settingsState: StateFlow<ReaderSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettings()
    )

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.RECENT)
    private val _selectedFormat = MutableStateFlow<BookFormat?>(null)
    private val _isImporting = MutableStateFlow(false)

    private val _errorDialogMessage = MutableStateFlow<String?>(null)
    val errorDialogMessage: StateFlow<String?> = _errorDialogMessage.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val libraryState: StateFlow<LibraryUiState> = combine(
        repository.allBooksFlow,
        _searchQuery,
        _sortOrder,
        _selectedFormat,
        _isImporting
    ) { allBooks, query, sortOrder, formatFilter, isImporting ->
        val filtered = allBooks.filter { book ->
            val matchesQuery = query.isBlank() ||
                book.title.contains(query, ignoreCase = true) ||
                book.author.contains(query, ignoreCase = true)
            val matchesFormat = formatFilter == null ||
                book.format.equals(formatFilter.name, ignoreCase = true)
            matchesQuery && matchesFormat
        }

        val sorted = when (sortOrder) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.lastOpenedAt }
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.AUTHOR -> filtered.sortedBy { it.author.lowercase() }
        }

        LibraryUiState(
            books = sorted,
            searchQuery = query,
            sortOrder = sortOrder,
            selectedFormat = formatFilter,
            isImporting = isImporting,
            totalReadingSeconds = allBooks.sumOf { it.readingTimeSeconds },
            completedBooksCount = allBooks.count { it.progressPercent >= 98f }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private val _activeReaderState = MutableStateFlow(ActiveReaderUiState())
    val activeReaderState: StateFlow<ActiveReaderUiState> = _activeReaderState.asStateFlow()

    private var bookmarksJob: Job? = null
    private var readingTimerJob: Job? = null
    private var searchJob: Job? = null

    // Offline Android System TTS
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        viewModelScope.launch {
            repository.ensureSampleBooksSeeded()
        }
    }

    // =========================================================================
    // Library Actions
    // =========================================================================

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun updateFormatFilter(format: BookFormat?) {
        _selectedFormat.value = format
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            var successCount = 0
            val errors = mutableListOf<String>()

            for (uri in uris) {
                val res = repository.importUri(uri)
                res.onSuccess {
                    successCount++
                }.onFailure { err ->
                    errors.add(err.localizedMessage ?: "Не удалось прочитать файл")
                }
            }
            _isImporting.value = false

            if (errors.isNotEmpty()) {
                _errorDialogMessage.value = errors.joinToString("\n\n")
            }
            if (successCount > 0) {
                _snackbarMessage.value = "Добавлено книг в библиотеку: $successCount"
            }
        }
    }

    fun restoreSampleBooks() {
        viewModelScope.launch {
            _isImporting.value = true
            val count = repository.seedSampleBooks()
            _isImporting.value = false
            _snackbarMessage.value = if (count > 0) {
                "Загружено тестовых книг (EPUB, FB2, TXT): $count"
            } else {
                "Все 3 тестовые книги уже находятся в библиотеке"
            }
        }
    }

    fun verifyCorruptedFileHandling() {
        viewModelScope.launch {
            val result = repository.testCorruptedFileImport()
            result.onFailure { err ->
                _errorDialogMessage.value = err.localizedMessage
                    ?: "Файл повреждён и не может быть открыт."
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            if (_activeReaderState.value.book?.id == book.id) {
                closeBook()
            }
            repository.deleteBook(book)
            _snackbarMessage.value = "Книга «${book.title}» удалена из библиотеки"
        }
    }

    fun exportProgressBackup(uri: Uri) {
        viewModelScope.launch {
            repository.exportBackupToUri(uri)
                .onSuccess { count ->
                    _snackbarMessage.value = "Прогресс и закладки ($count кн.) сохранены в файл"
                }
                .onFailure { e ->
                    _errorDialogMessage.value = e.localizedMessage ?: "Ошибка резервного копирования"
                }
        }
    }

    fun importProgressBackup(uri: Uri) {
        viewModelScope.launch {
            repository.importBackupFromUri(uri)
                .onSuccess { count ->
                    _snackbarMessage.value = "Восстановлен прогресс для книг: $count"
                }
                .onFailure { e ->
                    _errorDialogMessage.value = e.localizedMessage ?: "Ошибка восстановления копии"
                }
        }
    }

    fun dismissErrorDialog() {
        _errorDialogMessage.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // =========================================================================
    // Reader Actions
    // =========================================================================

    fun openBook(bookId: Long) {
        viewModelScope.launch {
            _activeReaderState.value = ActiveReaderUiState(isLoading = true)
            val result = repository.loadBookContent(bookId)
            result.onSuccess { (entity, parsed) ->
                val startPara = entity.currentParagraphIndex.coerceIn(
                    0,
                    (parsed.paragraphs.size - 1).coerceAtLeast(0)
                )
                _activeReaderState.value = ActiveReaderUiState(
                    isLoading = false,
                    book = entity,
                    content = parsed,
                    currentParagraphIndex = startPara
                )
                // Save timestamp immediately so book moves to top of Recent
                repository.saveReadingPosition(
                    bookId = entity.id,
                    paragraphIndex = startPara,
                    totalParagraphs = parsed.paragraphs.size
                )
                observeBookmarksForActiveBook(entity.id)
                startReadingSessionTimer(entity.id)
            }.onFailure { err ->
                _activeReaderState.value = ActiveReaderUiState(isLoading = false)
                _errorDialogMessage.value = err.localizedMessage
                    ?: "Не удалось открыть книгу: файл повреждён."
            }
        }
    }

    fun closeBook() {
        stopTts()
        bookmarksJob?.cancel()
        readingTimerJob?.cancel()
        _activeReaderState.value = ActiveReaderUiState()
    }

    private fun observeBookmarksForActiveBook(bookId: Long) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            repository.observeBookmarks(bookId).collect { list ->
                _activeReaderState.value = _activeReaderState.value.copy(bookmarks = list)
            }
        }
    }

    private fun startReadingSessionTimer(bookId: Long) {
        readingTimerJob?.cancel()
        readingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                repository.incrementReadingTime(bookId, 15L)
            }
        }
    }

    fun onParagraphPositionChanged(paragraphIndex: Int) {
        val state = _activeReaderState.value
        val book = state.book ?: return
        val content = state.content ?: return
        val clamped = paragraphIndex.coerceIn(0, (content.paragraphs.size - 1).coerceAtLeast(0))

        if (state.currentParagraphIndex == clamped) return
        _activeReaderState.value = state.copy(currentParagraphIndex = clamped)

        viewModelScope.launch {
            repository.saveReadingPosition(
                bookId = book.id,
                paragraphIndex = clamped,
                totalParagraphs = content.paragraphs.size
            )
        }
    }

    fun toggleBookmarkAtCurrentPosition(note: String = "") {
        val state = _activeReaderState.value
        val book = state.book ?: return
        val content = state.content ?: return
        val paraIdx = state.currentParagraphIndex
        val para = content.paragraphs.getOrNull(paraIdx) ?: return
        val chapterTitle = content.chapters.getOrNull(para.chapterIndex)?.title ?: book.title

        viewModelScope.launch {
            val added = repository.toggleBookmark(
                bookId = book.id,
                paragraphIndex = paraIdx,
                chapterTitle = chapterTitle,
                paragraphText = para.text,
                totalParagraphs = content.paragraphs.size,
                existingBookmarks = state.bookmarks,
                note = note
            )
            _snackbarMessage.value = if (added) "Закладка добавлена" else "Закладка удалена"
        }
    }

    fun addBookmarkWithNote(paragraphIndex: Int, note: String) {
        val state = _activeReaderState.value
        val book = state.book ?: return
        val content = state.content ?: return
        val para = content.paragraphs.getOrNull(paragraphIndex) ?: return
        val chapterTitle = content.chapters.getOrNull(para.chapterIndex)?.title ?: book.title

        viewModelScope.launch {
            repository.toggleBookmark(
                bookId = book.id,
                paragraphIndex = paragraphIndex,
                chapterTitle = chapterTitle,
                paragraphText = para.text,
                totalParagraphs = content.paragraphs.size,
                existingBookmarks = state.bookmarks,
                note = note
            )
            _activeReaderState.value = _activeReaderState.value.copy(
                selectedWordDefinition = null,
                selectedParagraphForAction = null
            )
            _snackbarMessage.value = "Закладка с заметкой сохранена"
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
            _snackbarMessage.value = "Закладка удалена"
        }
    }

    fun updateInBookSearch(query: String) {
        _activeReaderState.value = _activeReaderState.value.copy(inBookSearchQuery = query)
        val content = _activeReaderState.value.content ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val matches = repository.searchInBook(content, query)
            _activeReaderState.value = _activeReaderState.value.copy(searchMatches = matches)
        }
    }

    fun onParagraphLongPress(paragraphIndex: Int) {
        val content = _activeReaderState.value.content ?: return
        val para = content.paragraphs.getOrNull(paragraphIndex) ?: return
        val def = repository.lookupOfflineDictionary(para.text)
        _activeReaderState.value = _activeReaderState.value.copy(
            selectedWordDefinition = def,
            selectedParagraphForAction = paragraphIndex
        )
    }

    fun dismissWordDefinition() {
        _activeReaderState.value = _activeReaderState.value.copy(
            selectedWordDefinition = null,
            selectedParagraphForAction = null
        )
    }

    // =========================================================================
    // Offline System TTS (Чтение вслух)
    // =========================================================================

    fun toggleTtsReadAloud() {
        if (_activeReaderState.value.isTtsSpeaking) {
            stopTts()
        } else {
            startTtsFromParagraph(_activeReaderState.value.currentParagraphIndex)
        }
    }

    fun startTtsFromParagraph(paragraphIndex: Int) {
        val content = _activeReaderState.value.content ?: return
        val safeIdx = paragraphIndex.coerceIn(0, (content.paragraphs.size - 1).coerceAtLeast(0))
        onParagraphPositionChanged(safeIdx)

        if (tts == null) {
            tts = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    tts?.language = Locale("ru", "RU")
                    setupTtsListener()
                    speakParagraphInternal(safeIdx)
                } else {
                    _snackbarMessage.value = "Синтезатор речи (TTS) недоступен на устройстве"
                }
            }
        } else if (isTtsInitialized) {
            speakParagraphInternal(safeIdx)
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                val nextIdx = (utteranceId?.toIntOrNull() ?: return) + 1
                val content = _activeReaderState.value.content ?: return
                if (nextIdx < content.paragraphs.size && _activeReaderState.value.isTtsSpeaking) {
                    viewModelScope.launch {
                        onParagraphPositionChanged(nextIdx)
                        speakParagraphInternal(nextIdx)
                    }
                } else {
                    viewModelScope.launch {
                        _activeReaderState.value = _activeReaderState.value.copy(isTtsSpeaking = false)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                viewModelScope.launch {
                    _activeReaderState.value = _activeReaderState.value.copy(isTtsSpeaking = false)
                }
            }
        })
    }

    private fun speakParagraphInternal(index: Int) {
        val content = _activeReaderState.value.content ?: return
        val para = content.paragraphs.getOrNull(index) ?: return
        _activeReaderState.value = _activeReaderState.value.copy(isTtsSpeaking = true)
        tts?.speak(para.text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
    }

    fun stopTts() {
        tts?.stop()
        _activeReaderState.value = _activeReaderState.value.copy(isTtsSpeaking = false)
    }

    // =========================================================================
    // Display Settings Updates (Instant persistence via DataStore)
    // =========================================================================

    fun setFontSize(sizeSp: Float) {
        viewModelScope.launch { settingsDataStore.updateFontSize(sizeSp) }
    }

    fun setLineHeight(multiplier: Float) {
        viewModelScope.launch { settingsDataStore.updateLineHeight(multiplier) }
    }

    fun setMargins(horizontalDp: Int, verticalDp: Int) {
        viewModelScope.launch { settingsDataStore.updateMargins(horizontalDp, verticalDp) }
    }

    fun setFontFamily(fontFamily: ReaderFontFamily) {
        viewModelScope.launch { settingsDataStore.updateFontFamily(fontFamily) }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch { settingsDataStore.updateTheme(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateDynamicColor(enabled) }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch { settingsDataStore.updateReadingMode(mode) }
    }

    fun setBrightness(brightness: Float, useSystem: Boolean) {
        viewModelScope.launch { settingsDataStore.updateBrightness(brightness, useSystem) }
    }

    fun setJustifyText(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateJustify(enabled) }
    }

    fun setHyphenation(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateHyphenation(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
