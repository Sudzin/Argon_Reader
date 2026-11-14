package com.example.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookParagraph
import com.example.data.model.ParagraphType
import com.example.data.model.ReaderSettings
import com.example.data.model.ReadingMode
import com.example.ui.theme.toComposeFontFamily
import com.example.viewmodel.ActiveReaderUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    readerState: ActiveReaderUiState,
    settings: ReaderSettings,
    snackbarHostState: SnackbarHostState,
    onBackToLibrary: () -> Unit,
    onParagraphChanged: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onInBookSearchQueryChange: (String) -> Unit,
    onParagraphLongPress: (Int) -> Unit,
    onDismissWordDefinition: () -> Unit,
    onSaveBookmarkWithNote: (Int, String) -> Unit,
    onToggleTts: () -> Unit,
    onStartTtsFromParagraph: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onMarginsChange: (Int, Int) -> Unit,
    onFontFamilyChange: (com.example.data.model.ReaderFontFamily) -> Unit,
    onThemeChange: (com.example.data.model.ReaderTheme) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onBrightnessChange: (Float, Boolean) -> Unit,
    onJustifyChange: (Boolean) -> Unit,
    onHyphenationChange: (Boolean) -> Unit
) {
    BackHandler(enabled = true) {
        onBackToLibrary()
    }

    val context = LocalContext.current

    // Apply in-app window brightness without requiring system permissions
    LaunchedEffect(settings.brightness, settings.useSystemBrightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val params = window.attributes
        params.screenBrightness = if (settings.useSystemBrightness) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            settings.brightness.coerceIn(0.05f, 1.0f)
        }
        window.attributes = params
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    var showHud by rememberSaveable { mutableStateOf(false) }
    var showTocAndBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var tocSheetInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }

    val book = readerState.book
    val content = readerState.content

    if (readerState.isLoading || book == null || content == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val totalParagraphs = content.paragraphs.size.coerceAtLeast(1)
    val currentParaIdx = readerState.currentParagraphIndex.coerceIn(0, totalParagraphs - 1)
    val progressFraction = if (totalParagraphs <= 1) {
        1f
    } else {
        (currentParaIdx.toFloat() / (totalParagraphs - 1).toFloat()).coerceIn(0f, 1f)
    }
    val progressPercentInt = (progressFraction * 100f).roundToInt()

    val currentChapterTitle by remember(content, currentParaIdx) {
        derivedStateOf {
            val para = content.paragraphs.getOrNull(currentParaIdx)
            content.chapters.getOrNull(para?.chapterIndex ?: 0)?.title ?: book.title
        }
    }

    // Main Edge-to-Edge Container respecting Display Cutout (Camera notch in Portrait & Landscape)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("reader_screen_root")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWidthDp = maxWidth.value
            val viewportHeightDp = maxHeight.value

            if (settings.readingMode == ReadingMode.PAGED) {
                PagedReadingCanvas(
                    content = content,
                    currentParagraphIndex = currentParaIdx,
                    bookmarks = readerState.bookmarks,
                    settings = settings,
                    viewportWidthDp = viewportWidthDp,
                    viewportHeightDp = viewportHeightDp,
                    progressPercentInt = progressPercentInt,
                    onParagraphChanged = onParagraphChanged,
                    onCenterTap = { showHud = !showHud },
                    onParagraphLongPress = onParagraphLongPress
                )
            } else {
                VerticalScrollReadingCanvas(
                    content = content,
                    currentParagraphIndex = currentParaIdx,
                    bookmarks = readerState.bookmarks,
                    settings = settings,
                    currentChapterTitle = currentChapterTitle,
                    progressPercentInt = progressPercentInt,
                    onParagraphChanged = onParagraphChanged,
                    onCenterTap = { showHud = !showHud },
                    onParagraphLongPress = onParagraphLongPress
                )
            }
        }

        // Top HUD Bar (Animated on center tap)
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val isBookmarkedOnPage = remember(readerState.bookmarks, currentParaIdx) {
                readerState.bookmarks.any { kotlin.math.abs(it.paragraphIndex - currentParaIdx) <= 2 }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToLibrary,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад в библиотеку"
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$currentChapterTitle • ${book.format} (${book.encoding})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onToggleTts,
                        modifier = Modifier.testTag("reader_tts_button")
                    ) {
                        Icon(
                            imageVector = if (readerState.isTtsSpeaking) {
                                Icons.Default.StopCircle
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = "Чтение вслух (TTS)",
                            tint = if (readerState.isTtsSpeaking) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    IconButton(
                        onClick = { showSearchSheet = true },
                        modifier = Modifier.testTag("reader_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск по тексту"
                        )
                    }

                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.testTag("reader_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarkedOnPage) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = "Закладка",
                            tint = if (isBookmarkedOnPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        // Bottom HUD Bar (Quick progress scrubber, TOC/Bookmarks button, Display Settings button)
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Абзац ${currentParaIdx + 1} из $totalParagraphs",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "$progressPercentInt%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = currentParaIdx.toFloat(),
                        onValueChange = { newIdx ->
                            onParagraphChanged(newIdx.roundToInt())
                        },
                        valueRange = 0f..(totalParagraphs - 1).coerceAtLeast(1).toFloat(),
                        modifier = Modifier.testTag("reader_progress_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                                .combinedClickable(
                                    onClick = {
                                        tocSheetInitialTab = 0
                                        showTocAndBookmarksSheet = true
                                    }
                                )
                                .testTag("open_toc_sheet_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Оглавление и закладки",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp)
                                .combinedClickable(
                                    onClick = { showSettingsSheet = true }
                                )
                                .testTag("open_settings_sheet_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Отображение (Аа)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showHud) 150.dp else 36.dp)
        )
    }

    // Bottom Sheets & Dialogs
    if (showTocAndBookmarksSheet) {
        TocAndBookmarksSheet(
            chapters = content.chapters,
            bookmarks = readerState.bookmarks,
            currentParagraphIndex = currentParaIdx,
            totalParagraphs = totalParagraphs,
            initialTab = tocSheetInitialTab,
            onSelectParagraph = { targetPara ->
                onParagraphChanged(targetPara)
            },
            onAddBookmarkHere = onToggleBookmark,
            onDeleteBookmark = onDeleteBookmark,
            onDismiss = { showTocAndBookmarksSheet = false }
        )
    }

    if (showSettingsSheet) {
        ReaderSettingsSheet(
            settings = settings,
            onFontSizeChange = onFontSizeChange,
            onLineHeightChange = onLineHeightChange,
            onMarginsChange = onMarginsChange,
            onFontFamilyChange = onFontFamilyChange,
            onThemeChange = onThemeChange,
            onDynamicColorChange = onDynamicColorChange,
            onReadingModeChange = onReadingModeChange,
            onBrightnessChange = onBrightnessChange,
            onJustifyChange = onJustifyChange,
            onHyphenationChange = onHyphenationChange,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showSearchSheet) {
        InBookSearchSheet(
            query = readerState.inBookSearchQuery,
            matches = readerState.searchMatches,
            onQueryChange = onInBookSearchQueryChange,
            onSelectMatch = { targetPara ->
                onParagraphChanged(targetPara)
                showHud = false
            },
            onDismiss = { showSearchSheet = false }
        )
    }

    val wordDef = readerState.selectedWordDefinition
    val actionPara = readerState.selectedParagraphForAction
    if (wordDef != null && actionPara != null) {
        DictionaryAndNoteDialog(
            definition = wordDef,
            paragraphIndex = actionPara,
            onSpeakFromHere = onStartTtsFromParagraph,
            onSaveBookmarkWithNote = onSaveBookmarkWithNote,
            onDismiss = onDismissWordDefinition
        )
    }
}

// =============================================================================
// Paged Reading Canvas (120Hz smooth HorizontalPager + Edge-safe 28dp tap zones)
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedReadingCanvas(
    content: com.example.data.model.ParsedBookContent,
    currentParagraphIndex: Int,
    bookmarks: List<com.example.data.local.BookmarkEntity>,
    settings: ReaderSettings,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    progressPercentInt: Int,
    onParagraphChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onParagraphLongPress: (Int) -> Unit
) {
    val pages = remember(
        content,
        viewportWidthDp,
        viewportHeightDp,
        settings.fontSizeSp,
        settings.lineHeightMultiplier,
        settings.horizontalMarginDp,
        settings.verticalMarginDp,
        settings.fontFamily
    ) {
        BookPaginator.paginate(
            paragraphs = content.paragraphs,
            chapters = content.chapters,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            settings = settings
        )
    }

    val initialPage = remember(pages) {
        BookPaginator.findPageForParagraph(pages, currentParagraphIndex)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size.coerceAtLeast(1) }
    )

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // When pages are recalculated (font/size/margins change or orientation change) OR
    // when user jumps via TOC/Bookmarks/Search, lock onto the exact page containing currentParagraphIndex!
    LaunchedEffect(pages, currentParagraphIndex) {
        if (pages.isNotEmpty()) {
            val targetPage = BookPaginator.findPageForParagraph(pages, currentParagraphIndex)
            val currentPageObj = pages.getOrNull(pagerState.currentPage)
            val alreadyContainsParagraph = currentPageObj != null &&
                currentParagraphIndex in currentPageObj.startParagraphIndex..currentPageObj.endParagraphIndex

            if (!alreadyContainsParagraph && targetPage != pagerState.currentPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // When user swipes or taps to turn a page, update currentParagraphIndex to the page's start paragraph
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPageIdx ->
                val page = pages.getOrNull(settledPageIdx) ?: return@collect
                if (currentParagraphIndex !in page.startParagraphIndex..page.endParagraphIndex) {
                    onParagraphChanged(page.startParagraphIndex)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pages.size) {
                // CRITICAL: 28.dp edge safe inset so Android system "Back" swipe from left/right edge
                // never triggers an accidental page turn!
                val edgeSafeInsetPx = with(density) { 28.dp.toPx() }
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        if (offset.x < edgeSafeInsetPx || offset.x > width - edgeSafeInsetPx) {
                            // Inside 28dp system edge gesture exclusion zone -> ignore tap
                            return@detectTapGestures
                        }
                        val leftZoneEnd = width / 3f
                        val rightZoneStart = width * 2f / 3f

                        when {
                            offset.x < leftZoneEnd -> {
                                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                if (prev != pagerState.currentPage) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = prev,
                                            animationSpec = tween(
                                                durationMillis = 210,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                }
                            }
                            offset.x > rightZoneStart -> {
                                val next = (pagerState.currentPage + 1).coerceAtMost((pages.size - 1).coerceAtLeast(0))
                                if (next != pagerState.currentPage) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = next,
                                            animationSpec = tween(
                                                durationMillis = 210,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                }
                            }
                            else -> onCenterTap()
                        }
                    }
                )
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("reader_horizontal_pager")
        ) { pageIdx ->
            val page = pages.getOrNull(pageIdx)
            if (page != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = settings.horizontalMarginDp.dp,
                            vertical = settings.verticalMarginDp.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (para in page.items) {
                        val hasBookmark = bookmarks.any { it.paragraphIndex == para.index }
                        ParagraphItemView(
                            paragraph = para,
                            settings = settings,
                            isBookmarked = hasBookmark,
                            onLongClick = { onParagraphLongPress(para.index) }
                        )
                    }
                }
            }
        }

        // Subtle Footer Status Bar (Chapter title • Page X / Y • Progress %)
        val currentPageObj = pages.getOrNull(pagerState.currentPage)
        ReaderBottomStatusFooter(
            chapterTitle = currentPageObj?.chapterTitle ?: content.title,
            pageLabel = "Стр. ${pagerState.currentPage + 1} из ${pages.size.coerceAtLeast(1)}",
            progressPercentInt = progressPercentInt,
            horizontalMarginDp = settings.horizontalMarginDp
        )
    }
}

// =============================================================================
// Vertical Scroll Reading Canvas (Alternative continuous scroll mode)
// =============================================================================

@Composable
private fun VerticalScrollReadingCanvas(
    content: com.example.data.model.ParsedBookContent,
    currentParagraphIndex: Int,
    bookmarks: List<com.example.data.local.BookmarkEntity>,
    settings: ReaderSettings,
    currentChapterTitle: String,
    progressPercentInt: Int,
    onParagraphChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onParagraphLongPress: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentParagraphIndex)

    LaunchedEffect(currentParagraphIndex) {
        val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (currentParagraphIndex !in visibleIndices &&
            kotlin.math.abs(listState.firstVisibleItemIndex - currentParagraphIndex) > 1
        ) {
            listState.scrollToItem(currentParagraphIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIdx ->
                onParagraphChanged(firstIdx)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val edgeSafeInsetPx = 28.dp.toPx()
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width.toFloat()
                        if (offset.x in (width / 4f)..(width * 3f / 4f) &&
                            offset.x > edgeSafeInsetPx &&
                            offset.x < width - edgeSafeInsetPx
                        ) {
                            onCenterTap()
                        }
                    }
                )
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = settings.horizontalMarginDp.dp,
                vertical = settings.verticalMarginDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("reader_vertical_scroll_list")
        ) {
            items(content.paragraphs, key = { it.index }) { para ->
                val hasBookmark = bookmarks.any { it.paragraphIndex == para.index }
                ParagraphItemView(
                    paragraph = para,
                    settings = settings,
                    isBookmarked = hasBookmark,
                    onLongClick = { onParagraphLongPress(para.index) }
                )
            }
        }

        ReaderBottomStatusFooter(
            chapterTitle = currentChapterTitle,
            pageLabel = "Абз. ${currentParagraphIndex + 1} / ${content.paragraphs.size}",
            progressPercentInt = progressPercentInt,
            horizontalMarginDp = settings.horizontalMarginDp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphItemView(
    paragraph: BookParagraph,
    settings: ReaderSettings,
    isBookmarked: Boolean,
    onLongClick: () -> Unit
) {
    val fontFamily = settings.fontFamily.toComposeFontFamily()
    val baseFontSize = settings.fontSizeSp.sp
    val baseLineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp

    val displayText = remember(paragraph.text, settings.hyphenation) {
        if (settings.hyphenation && paragraph.type == ParagraphType.BODY) {
            BookPaginator.applySoftHyphens(paragraph.text)
        } else {
            paragraph.text
        }
    }

    val style = when (paragraph.type) {
        ParagraphType.CHAPTER_TITLE -> TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (settings.fontSizeSp * 1.28f).sp,
            lineHeight = (settings.fontSizeSp * 1.45f).sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start
        )
        ParagraphType.SUBTITLE -> TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (settings.fontSizeSp * 1.1f).sp,
            lineHeight = (settings.fontSizeSp * 1.35f).sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start
        )
        ParagraphType.EPIGRAPH, ParagraphType.QUOTE, ParagraphType.POEM -> TextStyle(
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
            fontSize = (settings.fontSizeSp * 0.94f).sp,
            lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier * 0.95f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
        ParagraphType.BODY -> TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = baseFontSize,
            lineHeight = baseLineHeight,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (settings.justifyText) TextAlign.Justify else TextAlign.Start,
            textIndent = TextIndent(firstLine = (settings.fontSizeSp * 1.1f).sp),
            hyphens = if (settings.hyphenation) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph
        )
    }

    val topPadding = when (paragraph.type) {
        ParagraphType.CHAPTER_TITLE -> 10.dp
        ParagraphType.SUBTITLE -> 6.dp
        else -> 0.dp
    }

    val startPadding = when (paragraph.type) {
        ParagraphType.EPIGRAPH, ParagraphType.QUOTE, ParagraphType.POEM -> 20.dp
        else -> 0.dp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = startPadding)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.Top
    ) {
        if (isBookmarked) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Закладка",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 6.dp, top = 4.dp)
                    .size(16.dp)
            )
        }
        Text(
            text = displayText,
            style = style,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReaderBottomStatusFooter(
    chapterTitle: String,
    pageLabel: String,
    progressPercentInt: Int,
    horizontalMarginDp: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMarginDp.dp, vertical = 6.dp)
    ) {
        LinearProgressIndicator(
            progress = { (progressPercentInt / 100f).coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Text(
                text = "$pageLabel • $progressPercentInt%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.testTag("reader_status_footer_text")
            )
        }
    }
}
