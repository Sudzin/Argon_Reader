package com.example.ui.reader

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookmarkEntity
import com.example.data.model.BookChapter
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import com.example.data.model.SearchMatch
import com.example.data.model.WordDefinition
import com.example.ui.theme.toComposeFontFamily
import kotlin.math.roundToInt

// =============================================================================
// 1. Table of Contents & Bookmarks Sheet (Tabs in a single bottom sheet)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocAndBookmarksSheet(
    chapters: List<BookChapter>,
    bookmarks: List<BookmarkEntity>,
    currentParagraphIndex: Int,
    totalParagraphs: Int,
    initialTab: Int = 0,
    onSelectParagraph: (Int) -> Unit,
    onAddBookmarkHere: () -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val activeChapterIndex = remember(chapters, currentParagraphIndex) {
        chapters.indexOfLast { it.startParagraphIndex <= currentParagraphIndex }.coerceAtLeast(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 340.dp, max = 580.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Оглавление (${chapters.size})") },
                    modifier = Modifier.testTag("toc_tab_button")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Закладки (${bookmarks.size})") },
                    modifier = Modifier.testTag("bookmarks_tab_button")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(chapters, key = { it.index }) { chapter ->
                        val isCurrent = chapter.index == activeChapterIndex
                        val pct = if (totalParagraphs <= 1) {
                            0
                        } else {
                            ((chapter.startParagraphIndex.toFloat() / (totalParagraphs - 1)) * 100f)
                                .roundToInt()
                                .coerceIn(0, 100)
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectParagraph(chapter.startParagraphIndex)
                                    onDismiss()
                                }
                                .testTag("chapter_item_${chapter.index}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (isCurrent) {
                                        Text(
                                            text = "Текущая глава",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddBookmarkHere,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_add_bookmark_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить закладку на текущем абзаце")
                    }

                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Закладок пока нет. Нажмите на значок закладки в верхней панели при чтении.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(bookmarks, key = { it.id }) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectParagraph(bm.paragraphIndex)
                                            onDismiss()
                                        }
                                        .testTag("bookmark_item_${bm.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = bm.chapterTitle,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "• ${bm.progressPercent.roundToInt()}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "«${bm.snippet}»",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (bm.note.isNotBlank()) {
                                                Text(
                                                    text = "Заметка: ${bm.note}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDeleteBookmark(bm.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Удалить закладку",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. Display Settings Bottom Sheet (Instant typography, themes, OLED #000000, brightness)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onMarginsChange: (Int, Int) -> Unit,
    onFontFamilyChange: (ReaderFontFamily) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onBrightnessChange: (Float, Boolean) -> Unit,
    onJustifyChange: (Boolean) -> Unit,
    onHyphenationChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Настройки отображения",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 1. Themes (Light, OLED #000000, Sepia)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Тема чтения",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        label = "Светлая",
                        subLabel = "Бумага",
                        bgColor = Color(0xFFFAF7F2),
                        textColor = Color(0xFF1E1B18),
                        selected = settings.theme == ReaderTheme.LIGHT,
                        onClick = { onThemeChange(ReaderTheme.LIGHT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_light")
                    )
                    ThemeOptionCard(
                        label = "Тёмная OLED",
                        subLabel = "#000000",
                        bgColor = Color(0xFF000000),
                        textColor = Color(0xFFE6E1DA),
                        selected = settings.theme == ReaderTheme.OLED_DARK,
                        onClick = { onThemeChange(ReaderTheme.OLED_DARK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_oled")
                    )
                    ThemeOptionCard(
                        label = "Сепия",
                        subLabel = "Пергамент",
                        bgColor = Color(0xFFF4ECD8),
                        textColor = Color(0xFF3B3126),
                        selected = settings.theme == ReaderTheme.SEPIA,
                        onClick = { onThemeChange(ReaderTheme.SEPIA) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_sepia")
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Динамические акценты Material You",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = settings.useDynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }

            HorizontalDivider()

            // 2. Reading Mode (Paged vs Vertical Scroll)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Режим перелистывания",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReadingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.readingMode == mode,
                            onClick = { onReadingModeChange(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("reading_mode_${mode.key.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider()

            // 3. Built-in Fonts (4 bundled fonts: 2 serif, 1 sans, 1 mono)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Шрифт (встроенные офлайн-шрифты)",
                    style = MaterialTheme.typography.titleMedium
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFontFamily.entries.chunked(2).forEach { rowFonts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowFonts.forEach { font ->
                                val isSelected = settings.fontFamily == font
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = if (isSelected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onFontFamilyChange(font) }
                                        .testTag("font_option_${font.key.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = font.label,
                                            fontFamily = font.toComposeFontFamily(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = font.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 4. Font Size, Line Height, Margins
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Размер шрифта", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onFontSizeChange(settings.fontSizeSp - 1f) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("decrease_font_button")
                        ) {
                            Text("A−")
                        }
                        Text(
                            text = "${settings.fontSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { onFontSizeChange(settings.fontSizeSp + 1f) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("increase_font_button")
                        ) {
                            Text("A+")
                        }
                    }
                }

                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..32f,
                    steps = 19,
                    modifier = Modifier.testTag("font_size_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Межстрочный интервал", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = String.format("%.2fx", settings.lineHeightMultiplier),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Slider(
                    value = settings.lineHeightMultiplier,
                    onValueChange = onLineHeightChange,
                    valueRange = 1.15f..2.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Боковые поля", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${settings.horizontalMarginDp} dp",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Slider(
                    value = settings.horizontalMarginDp.toFloat(),
                    onValueChange = { onMarginsChange(it.roundToInt(), settings.verticalMarginDp) },
                    valueRange = 12f..44f
                )
            }

            HorizontalDivider()

            // 5. Justify & Hyphenation
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Выравнивание по ширине", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Книжная вёрстка абзацев с ровным правым краем",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.justifyText,
                        onCheckedChange = onJustifyChange,
                        modifier = Modifier.testTag("justify_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автоматические переносы", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Мягкие переносы слов по слогам",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hyphenation,
                        onCheckedChange = onHyphenationChange,
                        modifier = Modifier.testTag("hyphenation_switch")
                    )
                }
            }

            HorizontalDivider()

            // 6. In-App Brightness Control
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BrightnessHigh, contentDescription = null)
                        Text("Системная яркость экрана", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = settings.useSystemBrightness,
                        onCheckedChange = { useSys ->
                            onBrightnessChange(settings.brightness, useSys)
                        }
                    )
                }

                if (!settings.useSystemBrightness) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Яркость в читалке", style = MaterialTheme.typography.bodyMedium)
                        Text("${(settings.brightness * 100).roundToInt()}%")
                    }
                    Slider(
                        value = settings.brightness,
                        onValueChange = { onBrightnessChange(it, false) },
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier.testTag("brightness_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemeOptionCard(
    label: String,
    subLabel: String,
    bgColor: Color,
    textColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = label,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            Text(
                text = subLabel,
                color = textColor.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
        }
    }
}

// =============================================================================
// 3. In-Book Full-Text Search Sheet
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InBookSearchSheet(
    query: String,
    matches: List<SearchMatch>,
    onQueryChange: (String) -> Unit,
    onSelectMatch: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 360.dp, max = 580.dp)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Поиск слова или фразы в книге…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("in_book_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when {
                    query.trim().length < 2 -> "Введите минимум 2 символа для поиска по тексту"
                    matches.isEmpty() -> "Совпадений не найдено"
                    else -> "Найдено совпадений: ${matches.size}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(matches, key = { "${it.paragraphIndex}_${it.matchStartInSnippet}" }) { match ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMatch(match.paragraphIndex)
                                onDismiss()
                            }
                            .testTag("search_result_${match.paragraphIndex}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = match.chapterTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Абз. ${match.paragraphIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val annotated = buildAnnotatedString {
                                val s = match.snippet
                                val start = match.matchStartInSnippet.coerceIn(0, s.length)
                                val end = (start + match.matchLength).coerceIn(start, s.length)
                                append(s.substring(0, start))
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    append(s.substring(start, end))
                                }
                                append(s.substring(end))
                            }
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. Offline Dictionary & Paragraph Note Dialog (Long-press on paragraph)
// =============================================================================

@Composable
fun DictionaryAndNoteDialog(
    definition: WordDefinition,
    paragraphIndex: Int,
    onSpeakFromHere: (Int) -> Unit,
    onSaveBookmarkWithNote: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = definition.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = definition.partOfSpeech,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = definition.definition,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = {
                        onSpeakFromHere(paragraphIndex)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Озвучить с этого абзаца (TTS)")
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Заметка к закладке (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBookmarkWithNote(paragraphIndex, noteText.trim())
                }
            ) {
                Text("В закладки")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
