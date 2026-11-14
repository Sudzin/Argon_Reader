package com.example.data.model

enum class BookFormat(val label: String, val extension: String) {
    EPUB("EPUB", "epub"),
    FB2("FB2", "fb2"),
    TXT("TXT", "txt");

    companion object {
        fun fromString(value: String): BookFormat {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TXT
        }
    }
}

enum class ParagraphType {
    CHAPTER_TITLE,
    SUBTITLE,
    BODY,
    EPIGRAPH,
    QUOTE,
    POEM
}

data class BookParagraph(
    val index: Int,
    val text: String,
    val type: ParagraphType = ParagraphType.BODY,
    val chapterIndex: Int = 0
)

data class BookChapter(
    val index: Int,
    val title: String,
    val startParagraphIndex: Int
)

data class ParsedBookContent(
    val title: String,
    val author: String,
    val format: BookFormat,
    val coverBytes: ByteArray? = null,
    val chapters: List<BookChapter>,
    val paragraphs: List<BookParagraph>,
    val encoding: String = "UTF-8"
)

enum class SortOrder(val label: String) {
    RECENT("Недавние"),
    TITLE("Название"),
    AUTHOR("Автор")
}

enum class ReaderTheme(val key: String, val label: String) {
    LIGHT("LIGHT", "Светлая"),
    OLED_DARK("OLED_DARK", "Тёмная OLED"),
    SEPIA("SEPIA", "Сепия");

    companion object {
        fun fromKey(key: String): ReaderTheme =
            entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

enum class ReaderFontFamily(val key: String, val label: String, val category: String) {
    LITERATA("LITERATA", "Literata", "С засечками"),
    MERRIWEATHER("MERRIWEATHER", "Merriweather", "С засечками"),
    INTER("INTER", "Inter", "Без засечек"),
    JETBRAINS_MONO("JETBRAINS_MONO", "JetBrains Mono", "Моноширинный");

    companion object {
        fun fromKey(key: String): ReaderFontFamily =
            entries.firstOrNull { it.key == key } ?: LITERATA
    }
}

enum class ReadingMode(val key: String, val label: String) {
    PAGED("PAGED", "Постраничный"),
    SCROLL("SCROLL", "Вертикальный");

    companion object {
        fun fromKey(key: String): ReadingMode =
            entries.firstOrNull { it.key == key } ?: PAGED
    }
}

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.45f,
    val horizontalMarginDp: Int = 24,
    val verticalMarginDp: Int = 16,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.LITERATA,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val useDynamicColor: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val brightness: Float = 0.75f,
    val useSystemBrightness: Boolean = true,
    val justifyText: Boolean = true,
    val hyphenation: Boolean = true
)

data class SearchMatch(
    val paragraphIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val matchStartInSnippet: Int,
    val matchLength: Int
)

data class WordDefinition(
    val word: String,
    val partOfSpeech: String,
    val definition: String
)
