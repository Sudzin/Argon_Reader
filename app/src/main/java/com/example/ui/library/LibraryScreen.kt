package com.example.ui.library

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookEntity
import com.example.data.model.BookFormat
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTheme
import com.example.data.model.SortOrder
import com.example.ui.theme.CoverPalette
import com.example.ui.theme.LiterataFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: com.example.viewmodel.LibraryUiState,
    settings: ReaderSettings,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onFormatFilterChange: (BookFormat?) -> Unit,
    onAddBooksClick: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onCycleTheme: () -> Unit,
    onRestoreSampleBooks: () -> Unit,
    onTestCorruptedFile: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Reader",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Личная офлайн-библиотека • Pixel 9",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCycleTheme,
                        modifier = Modifier.testTag("cycle_theme_button")
                    ) {
                        Icon(
                            imageVector = when (settings.theme) {
                                ReaderTheme.OLED_DARK -> Icons.Default.DarkMode
                                ReaderTheme.SEPIA -> Icons.Outlined.AutoStories
                                ReaderTheme.LIGHT -> Icons.Default.LightMode
                            },
                            contentDescription = "Сменить тему (${settings.theme.label})"
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("library_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Меню библиотеки"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Загрузить 3 тестовые книги (EPUB, FB2, TXT)") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onRestoreSampleBooks()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Проверить защиту от битого файла") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.BugReport, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onTestCorruptedFile()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Сохранить копию прогресса в файл") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Backup, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onExportBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Восстановить прогресс из файла") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Restore, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onImportBackup()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBooksClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить книги"
                    )
                },
                text = { Text("Добавить книги") },
                modifier = Modifier.testTag("add_books_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search & Statistics Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поиск по названию или автору…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск"
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Очистить поиск"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                // Sort & Format Filter Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(SortOrder.entries) { order ->
                        val selected = uiState.sortOrder == order
                        FilterChip(
                            selected = selected,
                            onClick = { onSortOrderChange(order) },
                            label = { Text(order.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}")
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    item {
                        FilterChip(
                            selected = uiState.selectedFormat == null,
                            onClick = { onFormatFilterChange(null) },
                            label = { Text("Все форматы") }
                        )
                    }

                    items(BookFormat.entries) { fmt ->
                        FilterChip(
                            selected = uiState.selectedFormat == fmt,
                            onClick = {
                                onFormatFilterChange(if (uiState.selectedFormat == fmt) null else fmt)
                            },
                            label = { Text(fmt.label) },
                            modifier = Modifier.testTag("format_chip_${fmt.name.lowercase()}")
                        )
                    }
                }

                // Compact Reading Stats Banner
                ReadingStatsStrip(
                    totalBooks = uiState.books.size,
                    completedBooks = uiState.completedBooksCount,
                    totalReadingSeconds = uiState.totalReadingSeconds,
                    currentThemeLabel = settings.theme.label
                )
            }

            AnimatedVisibility(visible = uiState.isImporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Обработка и индексация книг…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.books.isEmpty()) {
                EmptyLibraryState(
                    hasSearchFilter = uiState.searchQuery.isNotBlank() || uiState.selectedFormat != null,
                    onAddBooksClick = onAddBooksClick,
                    onRestoreSamplesClick = onRestoreSampleBooks
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("books_grid")
                ) {
                    items(uiState.books, key = { it.id }) { book ->
                        BookGridCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            onDeleteClick = { bookToDelete = book }
                        )
                    }
                }
            }
        }
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Удалить книгу?") },
            text = {
                Text("Книга «${target.title}» (${target.author}) и её закладки будут удалены из библиотеки.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBook(target)
                        bookToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_book_button")
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ReadingStatsStrip(
    totalBooks: Int,
    completedBooks: Int,
    totalReadingSeconds: Long,
    currentThemeLabel: String
) {
    val minutes = (totalReadingSeconds / 60).toInt()
    val timeLabel = if (minutes < 1) "Готово к чтению" else "$minutes мин в чтении"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Книг: $totalBooks • Прочитано: $completedBooks • $timeLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = currentThemeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BookGridCard(
    book: BookEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val coverBitmap by produceState<ImageBitmap?>(initialValue = null, book.coverPath) {
        value = withContext(Dispatchers.IO) {
            val path = book.coverPath ?: return@withContext null
            val file = File(path)
            if (file.exists()) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            } else {
                null
            }
        }
    }

    val palettePair = CoverPalette[book.coverColorSeed % CoverPalette.size]
    val progressPct = book.progressPercent.roundToInt().coerceIn(0, 100)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                palettePair.first,
                                palettePair.first.copy(alpha = 0.85f),
                                Color(0xFF121318)
                            )
                        )
                    )
            ) {
                val bmp = coverBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Обложка ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Editorial typographic cover fallback
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = book.author,
                                color = Color(0xFFE0D6C3),
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = book.title,
                                fontFamily = LiterataFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFBF4),
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(palettePair.second.copy(alpha = 0.6f))
                        )
                    }
                }

                // Top Row: Format Badge & Delete Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (book.format == "TXT" && book.encoding.contains("1251")) {
                                "TXT • CP1251"
                            } else {
                                book.format
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF5DEB3),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("delete_book_${book.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить книгу ${book.title}",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bottom Progress Pill over cover
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(topStart = 10.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (book.currentParagraphIndex > 0) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = palettePair.second,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "$progressPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // Book Metadata & Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasSearchFilter: Boolean,
    onAddBooksClick: () -> Unit,
    onRestoreSamplesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (hasSearchFilter) "Ничего не найдено" else "Ваша библиотека пуста",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasSearchFilter) {
                "Попробуйте изменить поисковый запрос или сбросить фильтр форматов."
            } else {
                "Добавьте файлы EPUB, FB2 или TXT (UTF-8 / Windows-1251) через системный проводник или восстановите 3 встроенные тестовые книги."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!hasSearchFilter) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRestoreSamplesClick,
                    modifier = Modifier.testTag("restore_samples_button")
                ) {
                    Text("3 тестовые книги")
                }
                OutlinedButton(onClick = onAddBooksClick) {
                    Text("Выбрать файлы")
                }
            }
        }
    }
}
