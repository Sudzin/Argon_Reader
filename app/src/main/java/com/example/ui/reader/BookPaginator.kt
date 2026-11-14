package com.example.ui.reader

import com.example.data.model.BookChapter
import com.example.data.model.BookParagraph
import com.example.data.model.ParagraphType
import com.example.data.model.ReaderFontFamily
import com.example.data.model.ReaderSettings
import kotlin.math.ceil
import kotlin.math.max

data class BookPage(
    val pageIndex: Int,
    val startParagraphIndex: Int,
    val endParagraphIndex: Int,
    val chapterTitle: String,
    val items: List<BookParagraph>
)

object BookPaginator {

    private val russianVowels = setOf(
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я'
    )
    private val noBreakChars = setOf('ь', 'ъ', 'Ь', 'Ъ', 'й', 'Й')

    /**
     * Paginates book paragraphs into screen-fitted pages while preserving exact paragraph indices
     * so changing font size, font family, margins, or rotating the screen keeps the exact reading position.
     */
    fun paginate(
        paragraphs: List<BookParagraph>,
        chapters: List<BookChapter>,
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        settings: ReaderSettings
    ): List<BookPage> {
        if (paragraphs.isEmpty()) return emptyList()

        val usableWidthDp = (viewportWidthDp - settings.horizontalMarginDp * 2).coerceAtLeast(180f)
        val usableHeightDp = (viewportHeightDp - settings.verticalMarginDp * 2 - 44f).coerceAtLeast(220f)

        // Character width factor depends slightly on font family
        val charWidthFactor = when (settings.fontFamily) {
            ReaderFontFamily.JETBRAINS_MONO -> 0.62f
            ReaderFontFamily.MERRIWEATHER -> 0.56f
            ReaderFontFamily.LITERATA -> 0.53f
            ReaderFontFamily.INTER -> 0.51f
        }

        val avgCharWidthDp = settings.fontSizeSp * charWidthFactor
        val charsPerLine = (usableWidthDp / avgCharWidthDp).toInt().coerceAtLeast(22)
        val lineHeightDp = settings.fontSizeSp * settings.lineHeightMultiplier
        val maxLinesPerPage = (usableHeightDp / lineHeightDp).toInt().coerceAtLeast(6)

        val pages = mutableListOf<BookPage>()
        var currentItems = mutableListOf<BookParagraph>()
        var currentLinesUsed = 0f

        fun flushPage() {
            if (currentItems.isEmpty()) return
            val firstPara = currentItems.first()
            val lastPara = currentItems.last()
            val chapterTitle = chapters.getOrNull(firstPara.chapterIndex)?.title
                ?: chapters.lastOrNull { it.startParagraphIndex <= firstPara.index }?.title
                ?: ""

            pages.add(
                BookPage(
                    pageIndex = pages.size,
                    startParagraphIndex = firstPara.index,
                    endParagraphIndex = lastPara.index,
                    chapterTitle = chapterTitle,
                    items = currentItems.toList()
                )
            )
            currentItems = mutableListOf()
            currentLinesUsed = 0f
        }

        for (para in paragraphs) {
            // Start a fresh page on a new chapter title if the current page already has content
            if (para.type == ParagraphType.CHAPTER_TITLE && currentItems.isNotEmpty()) {
                flushPage()
            }

            val effectiveCharsPerLine = when (para.type) {
                ParagraphType.CHAPTER_TITLE -> (charsPerLine * 0.72f).toInt().coerceAtLeast(14)
                ParagraphType.SUBTITLE -> (charsPerLine * 0.85f).toInt().coerceAtLeast(18)
                ParagraphType.EPIGRAPH, ParagraphType.QUOTE, ParagraphType.POEM ->
                    (charsPerLine * 0.88f).toInt().coerceAtLeast(18)
                ParagraphType.BODY -> charsPerLine
            }

            val textLineCount = ceil(para.text.length.toFloat() / effectiveCharsPerLine.toFloat())
                .coerceAtLeast(1f)
            val paragraphSpacingLines = when (para.type) {
                ParagraphType.CHAPTER_TITLE -> 1.8f
                ParagraphType.SUBTITLE -> 1.1f
                else -> 0.65f
            }
            val totalParaLines = textLineCount + paragraphSpacingLines

            // If a single paragraph is extraordinarily long, split it into chunks sharing the same paragraph index
            if (totalParaLines > maxLinesPerPage && para.type == ParagraphType.BODY) {
                val maxCharsPerChunk = max((maxLinesPerPage - 1) * effectiveCharsPerLine, 160)
                val chunks = splitLongParagraph(para.text, maxCharsPerChunk)
                for (chunk in chunks) {
                    val chunkLines = ceil(chunk.length.toFloat() / effectiveCharsPerLine.toFloat()) + 0.65f
                    if (currentItems.isNotEmpty() && currentLinesUsed + chunkLines > maxLinesPerPage) {
                        flushPage()
                    }
                    currentItems.add(para.copy(text = chunk))
                    currentLinesUsed += chunkLines
                }
            } else {
                if (currentItems.isNotEmpty() && currentLinesUsed + totalParaLines > maxLinesPerPage) {
                    flushPage()
                }
                currentItems.add(para)
                currentLinesUsed += totalParaLines
            }
        }
        flushPage()

        return pages
    }

    fun findPageForParagraph(pages: List<BookPage>, targetParagraphIndex: Int): Int {
        if (pages.isEmpty()) return 0
        val exact = pages.indexOfFirst { page ->
            targetParagraphIndex in page.startParagraphIndex..page.endParagraphIndex
        }
        if (exact >= 0) return exact

        val closestBefore = pages.indexOfLast { it.startParagraphIndex <= targetParagraphIndex }
        return if (closestBefore >= 0) closestBefore else 0
    }

    private fun splitLongParagraph(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val words = text.split(" ")
        val current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxChars && current.isNotEmpty()) {
                result.add(current.toString().trim())
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    /**
     * Inserts soft hyphens (\u00AD) into Russian words longer than 5 letters according to
     * Liang/Christoffersen Russian syllable rules for clean justified text layout.
     */
    fun applySoftHyphens(text: String): String {
        if (text.length < 6) return text
        val sb = StringBuilder(text.length + text.length / 5)
        var wordStart = -1

        for (i in text.indices) {
            val ch = text[i]
            if (ch.isLetter()) {
                if (wordStart == -1) wordStart = i
            } else {
                if (wordStart != -1) {
                    appendHyphenatedWord(sb, text, wordStart, i)
                    wordStart = -1
                }
                sb.append(ch)
            }
        }
        if (wordStart != -1) {
            appendHyphenatedWord(sb, text, wordStart, text.length)
        }
        return sb.toString()
    }

    private fun appendHyphenatedWord(sb: StringBuilder, source: String, start: Int, end: Int) {
        val len = end - start
        if (len < 6) {
            sb.append(source, start, end)
            return
        }

        // Collect vowel positions inside the word
        var hasVowel = false
        for (i in start until end) {
            if (source[i] in russianVowels) {
                hasVowel = true
                break
            }
        }
        if (!hasVowel) {
            sb.append(source, start, end)
            return
        }

        for (i in start until end) {
            sb.append(source[i])
            val relIdx = i - start
            // Leave at least 2 letters at start and 2 letters at end
            if (relIdx >= 1 && relIdx <= len - 3) {
                val curr = source[i]
                val next = source[i + 1]
                val afterNext = source[i + 2]

                if (next !in noBreakChars) {
                    val currIsVowel = curr in russianVowels
                    val nextIsVowel = next in russianVowels
                    val afterNextIsVowel = afterNext in russianVowels

                    // Break after vowel if followed by Consonant + Vowel, or between two consonants if preceded by vowel
                    val shouldHyphenate = (currIsVowel && !nextIsVowel && afterNextIsVowel) ||
                        (!currIsVowel && curr !in noBreakChars && !nextIsVowel && afterNextIsVowel &&
                            (i > start && source[i - 1] in russianVowels))

                    if (shouldHyphenate) {
                        sb.append('\u00AD')
                    }
                }
            }
        }
    }
}
