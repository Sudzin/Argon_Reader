package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ReaderTheme
import com.example.ui.library.LibraryScreen
import com.example.ui.reader.ReaderScreen
import com.example.ui.theme.ReaderAppTheme
import com.example.viewmodel.ReaderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReaderRootApp()
        }
    }
}

@Composable
fun ReaderRootApp(
    viewModel: ReaderViewModel = viewModel()
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val activeReaderState by viewModel.activeReaderState.collectAsStateWithLifecycle()
    val errorDialogMessage by viewModel.errorDialogMessage.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    // SAF Multi-file picker for EPUB, FB2, TXT, ZIP
    val openMultipleBooksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    // SAF Export Progress Backup
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportProgressBackup(uri)
        }
    }

    // SAF Import Progress Backup
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProgressBackup(uri)
        }
    }

    ReaderAppTheme(
        readerTheme = settings.theme,
        useDynamicColor = settings.useDynamicColor
    ) {
        val isReadingBook = activeReaderState.book != null || activeReaderState.isLoading

        Crossfade(targetState = isReadingBook, label = "reader_navigation") { inReader ->
            if (inReader) {
                ReaderScreen(
                    readerState = activeReaderState,
                    settings = settings,
                    snackbarHostState = snackbarHostState,
                    onBackToLibrary = { viewModel.closeBook() },
                    onParagraphChanged = { viewModel.onParagraphPositionChanged(it) },
                    onToggleBookmark = { viewModel.toggleBookmarkAtCurrentPosition() },
                    onDeleteBookmark = { viewModel.deleteBookmark(it) },
                    onInBookSearchQueryChange = { viewModel.updateInBookSearch(it) },
                    onParagraphLongPress = { viewModel.onParagraphLongPress(it) },
                    onDismissWordDefinition = { viewModel.dismissWordDefinition() },
                    onSaveBookmarkWithNote = { idx, note -> viewModel.addBookmarkWithNote(idx, note) },
                    onToggleTts = { viewModel.toggleTtsReadAloud() },
                    onStartTtsFromParagraph = { viewModel.startTtsFromParagraph(it) },
                    onFontSizeChange = { viewModel.setFontSize(it) },
                    onLineHeightChange = { viewModel.setLineHeight(it) },
                    onMarginsChange = { h, v -> viewModel.setMargins(h, v) },
                    onFontFamilyChange = { viewModel.setFontFamily(it) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onDynamicColorChange = { viewModel.setDynamicColor(it) },
                    onReadingModeChange = { viewModel.setReadingMode(it) },
                    onBrightnessChange = { b, sys -> viewModel.setBrightness(b, sys) },
                    onJustifyChange = { viewModel.setJustifyText(it) },
                    onHyphenationChange = { viewModel.setHyphenation(it) }
                )
            } else {
                LibraryScreen(
                    uiState = libraryState,
                    settings = settings,
                    snackbarHostState = snackbarHostState,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSortOrderChange = { viewModel.updateSortOrder(it) },
                    onFormatFilterChange = { viewModel.updateFormatFilter(it) },
                    onAddBooksClick = {
                        openMultipleBooksLauncher.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/x-fictionbook+xml",
                                "text/xml",
                                "application/xml",
                                "text/plain",
                                "application/zip",
                                "*/*"
                            )
                        )
                    },
                    onOpenBook = { bookId -> viewModel.openBook(bookId) },
                    onDeleteBook = { book -> viewModel.deleteBook(book) },
                    onCycleTheme = {
                        val nextTheme = when (settings.theme) {
                            ReaderTheme.LIGHT -> ReaderTheme.OLED_DARK
                            ReaderTheme.OLED_DARK -> ReaderTheme.SEPIA
                            ReaderTheme.SEPIA -> ReaderTheme.LIGHT
                        }
                        viewModel.setTheme(nextTheme)
                    },
                    onRestoreSampleBooks = { viewModel.restoreSampleBooks() },
                    onTestCorruptedFile = { viewModel.verifyCorruptedFileHandling() },
                    onExportBackup = {
                        exportBackupLauncher.launch("reader_backup_progress.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
            }
        }

        // Friendly Error Dialog for Corrupted or Unsupported Files (Criterion 5)
        errorDialogMessage?.let { errorMsg ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissErrorDialog() },
                title = { Text("Не удалось открыть файл") },
                text = {
                    Text(
                        text = errorMsg,
                        modifier = Modifier.testTag("error_dialog_message")
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.dismissErrorDialog() },
                        modifier = Modifier.testTag("dismiss_error_dialog_button")
                    ) {
                        Text("Понятно")
                    }
                }
            )
        }
    }
}
