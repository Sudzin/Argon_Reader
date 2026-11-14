package com.example.data.parser

import android.util.Base64
import android.util.Xml
import com.example.data.model.BookChapter
import com.example.data.model.BookFormat
import com.example.data.model.BookParagraph
import com.example.data.model.ParagraphType
import com.example.data.model.ParsedBookContent
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class BookParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BookParser {

    fun parseFile(file: File, fallbackFileName: String = file.name): ParsedBookContent {
        if (!file.exists() || file.length() == 0L) {
            throw BookParseException("Файл «$fallbackFileName» пуст или недоступен для чтения.")
        }

        val lowerName = fallbackFileName.lowercase()
        val fallbackTitle = fallbackFileName
            .removeSuffix(".fb2.zip")
            .substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
            .ifEmpty { "Без названия" }

        return try {
            when {
                lowerName.endsWith(".epub") -> parseEpub(file, fallbackTitle)
                lowerName.endsWith(".fb2.zip") -> parseFb2Zip(file, fallbackTitle)
                lowerName.endsWith(".fb2") -> parseFb2Bytes(file.readBytes(), fallbackTitle)
                lowerName.endsWith(".txt") -> parseTxt(file.readBytes(), fallbackTitle)
                else -> {
                    // Try sniffing header bytes (PK zip vs XML vs plain text)
                    val bytes = file.readBytes()
                    if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                        parseEpub(file, fallbackTitle)
                    } else {
                        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
                        if (head.contains("<FictionBook", ignoreCase = true)) {
                            parseFb2Bytes(bytes, fallbackTitle)
                        } else {
                            parseTxt(bytes, fallbackTitle)
                        }
                    }
                }
            }
        } catch (e: BookParseException) {
            throw e
        } catch (e: ZipException) {
            throw BookParseException("Архив книги «$fallbackFileName» повреждён (ошибка ZIP/EPUB).", e)
        } catch (e: Exception) {
            throw BookParseException(
                "Не удалось открыть файл «$fallbackFileName»: формат повреждён или не поддерживается (${e.localizedMessage ?: "ошибка структуры"}).",
                e
            )
        }
    }

    // =========================================================================
    // 1. TXT Parser with UTF-8 & Windows-1251 Auto-Detection
    // =========================================================================

    fun detectCharsetAndDecode(bytes: ByteArray): Pair<String, String> {
        if (bytes.isEmpty()) throw BookParseException("Файл не содержит текста.")

        // Check UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8 (BOM)"
        }

        // Check UTF-16 BOMs
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16BE"
        }

        // Check for binary corruption (many control chars excluding \t, \n, \r)
        val sampleSize = minOf(bytes.size, 4096)
        var suspiciousControlCount = 0
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0 || (b in 1..8) || (b in 14..31 && b != 27)) {
                suspiciousControlCount++
            }
        }
        if (suspiciousControlCount > sampleSize / 10) {
            throw BookParseException("Файл повреждён: обнаружены бинарные данные вместо текста.")
        }

        // Strict UTF-8 validation
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            val decoded = utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
            decoded to "UTF-8"
        } catch (_: Exception) {
            val win1251 = Charset.forName("windows-1251")
            String(bytes, win1251) to "Windows-1251"
        }
    }

    private fun parseTxt(bytes: ByteArray, fallbackTitle: String): ParsedBookContent {
        val (rawText, encodingName) = detectCharsetAndDecode(bytes)
        val lines = rawText.replace("\r\n", "\n").replace('\r', '\n').lines()

        var detectedTitle = fallbackTitle
        var detectedAuthor = "Неизвестный автор"

        // Check first few non-empty lines for Title / Author metadata
        val nonEmptyTop = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(5)
        if (nonEmptyTop.isNotEmpty()) {
            val first = nonEmptyTop[0]
            if (first.startsWith("Название:", ignoreCase = true)) {
                detectedTitle = first.substringAfter(":").trim().ifEmpty { fallbackTitle }
            }
            val second = nonEmptyTop.getOrNull(1) ?: ""
            if (second.startsWith("Автор:", ignoreCase = true)) {
                detectedAuthor = second.substringAfter(":").trim().ifEmpty { detectedAuthor }
            } else if (nonEmptyTop.size >= 2 && first.length in 3..70 && second.length in 3..60 &&
                !first.startsWith("Глава", ignoreCase = true)
            ) {
                // Often first line is Author or Title
                if (first.split(" ").size in 2..3 && first.all { it.isLetter() || it.isWhitespace() || it == '.' || it == '-' }) {
                    detectedAuthor = first
                    detectedTitle = second
                }
            }
        }

        val chapters = mutableListOf<BookChapter>()
        val paragraphs = mutableListOf<BookParagraph>()
        val currentParagraphBuilder = StringBuilder()

        val chapterRegex = Regex(
            """^(Глава\s+\S+.*|ГЛАВА\s+\S+.*|Часть\s+\S+.*|ЧАСТЬ\s+\S+.*|Пролог.*|ПРОЛОГ.*|Эпилог.*|ЭПИЛОГ.*|Chapter\s+\S+.*|[IVXLCDM]{1,7}\.\s+.*|\d{1,3}\.\s+.{2,60})$"""
        )

        fun flushParagraph() {
            val text = currentParagraphBuilder.toString().trim()
            currentParagraphBuilder.setLength(0)
            if (text.isNotEmpty()) {
                val chapterIdx = (chapters.size - 1).coerceAtLeast(0)
                paragraphs.add(
                    BookParagraph(
                        index = paragraphs.size,
                        text = text,
                        type = ParagraphType.BODY,
                        chapterIndex = chapterIdx
                    )
                )
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }

            if (chapterRegex.matches(trimmed) && trimmed.length <= 90) {
                flushParagraph()
                val chapterIndex = chapters.size
                val startParaIdx = paragraphs.size
                chapters.add(
                    BookChapter(
                        index = chapterIndex,
                        title = trimmed,
                        startParagraphIndex = startParaIdx
                    )
                )
                paragraphs.add(
                    BookParagraph(
                        index = startParaIdx,
                        text = trimmed,
                        type = ParagraphType.CHAPTER_TITLE,
                        chapterIndex = chapterIndex
                    )
                )
            } else {
                if (currentParagraphBuilder.isNotEmpty()) {
                    // If line starts with indent or dialog dash, treat as new paragraph
                    if (rawLine.startsWith("  ") || rawLine.startsWith("\t") ||
                        trimmed.startsWith("—") || trimmed.startsWith("- ") || trimmed.startsWith("–")
                    ) {
                        flushParagraph()
                    } else {
                        currentParagraphBuilder.append(' ')
                    }
                }
                currentParagraphBuilder.append(trimmed)
            }
        }
        flushParagraph()

        if (paragraphs.isEmpty()) {
            throw BookParseException("В текстовом файле «$fallbackTitle» не найдено читаемого текста.")
        }

        if (chapters.isEmpty()) {
            chapters.add(BookChapter(index = 0, title = detectedTitle, startParagraphIndex = 0))
        }

        return ParsedBookContent(
            title = detectedTitle,
            author = detectedAuthor,
            format = BookFormat.TXT,
            coverBytes = null,
            chapters = chapters,
            paragraphs = paragraphs,
            encoding = encodingName
        )
    }

    // =========================================================================
    // 2. FB2 & FB2.ZIP Parser
    // =========================================================================

    private fun parseFb2Zip(file: File, fallbackTitle: String): ParsedBookContent {
        ZipFile(file).use { zip ->
            val fb2Entry = zip.entries().asSequence().firstOrNull { entry ->
                !entry.isDirectory && entry.name.lowercase().endsWith(".fb2")
            } ?: throw BookParseException("В архиве «$fallbackTitle» не найден файл .fb2.")

            val bytes = zip.getInputStream(fb2Entry).use { it.readBytes() }
            return parseFb2Bytes(bytes, fallbackTitle)
        }
    }

    private fun parseFb2Bytes(bytes: ByteArray, fallbackTitle: String): ParsedBookContent {
        if (bytes.size < 32) {
            throw BookParseException("Файл FB2 «$fallbackTitle» повреждён или слишком мал.")
        }

        val headerAscii = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
        if (!headerAscii.contains("<FictionBook", ignoreCase = true) &&
            !headerAscii.contains("<?xml", ignoreCase = true)
        ) {
            throw BookParseException("Файл «$fallbackTitle» не является корректным документом FB2.")
        }

        val declaredEncoding = Regex("""encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(headerAscii)?.groupValues?.getOrNull(1)?.uppercase()

        val (xmlString, encodingUsed) = when {
            declaredEncoding != null && declaredEncoding.contains("1251") -> {
                String(bytes, Charset.forName("windows-1251")) to "Windows-1251"
            }
            declaredEncoding != null && declaredEncoding.contains("KOI8") -> {
                String(bytes, Charset.forName("KOI8-R")) to "KOI8-R"
            }
            else -> detectCharsetAndDecode(bytes)
        }

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(InputStreamReader(ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8)), Charsets.UTF_8))

        var bookTitle = ""
        var authorFirstName = ""
        var authorMiddleName = ""
        var authorLastName = ""
        var coverHrefId: String? = null
        val binaries = mutableMapOf<String, ByteArray>()

        val chapters = mutableListOf<BookChapter>()
        val paragraphs = mutableListOf<BookParagraph>()

        var inTitleInfo = false
        var inAuthor = false
        var inBody = false
        var inNotesBody = false
        var inTitle = false
        var inEpigraph = false
        var inPoem = false
        var inCite = false
        var currentBinaryId: String? = null
        val textBuffer = StringBuilder()
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val rawName = parser.name ?: ""
                    val tag = rawName.substringAfter(':')
                    currentTag = tag

                    when (tag) {
                        "title-info" -> inTitleInfo = true
                        "author" -> if (inTitleInfo) inAuthor = true
                        "image" -> {
                            if (inTitleInfo && coverHrefId == null) {
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i)
                                    if (attrName.endsWith("href")) {
                                        coverHrefId = parser.getAttributeValue(i)?.removePrefix("#")
                                    }
                                }
                            }
                        }
                        "body" -> {
                            val bodyName = parser.getAttributeValue(null, "name")
                            if (bodyName.equals("notes", ignoreCase = true) ||
                                bodyName.equals("comments", ignoreCase = true)
                            ) {
                                inNotesBody = true
                            } else if (!inBody) {
                                inBody = true
                            }
                        }
                        "title" -> if (inBody && !inNotesBody) {
                            inTitle = true
                            textBuffer.setLength(0)
                        }
                        "epigraph" -> if (inBody && !inNotesBody) inEpigraph = true
                        "poem" -> if (inBody && !inNotesBody) inPoem = true
                        "cite" -> if (inBody && !inNotesBody) inCite = true
                        "p", "v", "subtitle" -> {
                            if ((inBody && !inNotesBody && !inTitle) || inTitleInfo) {
                                textBuffer.setLength(0)
                            }
                        }
                        "binary" -> {
                            currentBinaryId = parser.getAttributeValue(null, "id")
                            textBuffer.setLength(0)
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val txt = parser.text ?: ""
                    if (inTitle && inBody && !inNotesBody) {
                        if (textBuffer.isNotEmpty() && !textBuffer.endsWith(" ")) {
                            textBuffer.append(" ")
                        }
                        textBuffer.append(txt.trim())
                    } else {
                        textBuffer.append(txt)
                    }
                }

                XmlPullParser.END_TAG -> {
                    val rawName = parser.name ?: ""
                    val tag = rawName.substringAfter(':')

                    when (tag) {
                        "title-info" -> inTitleInfo = false
                        "author" -> inAuthor = false
                        "book-title" -> if (inTitleInfo && bookTitle.isEmpty()) {
                            bookTitle = textBuffer.toString().trim()
                        }
                        "first-name" -> if (inTitleInfo && inAuthor && authorFirstName.isEmpty()) {
                            authorFirstName = textBuffer.toString().trim()
                        }
                        "middle-name" -> if (inTitleInfo && inAuthor && authorMiddleName.isEmpty()) {
                            authorMiddleName = textBuffer.toString().trim()
                        }
                        "last-name" -> if (inTitleInfo && inAuthor && authorLastName.isEmpty()) {
                            authorLastName = textBuffer.toString().trim()
                        }
                        "body" -> {
                            inBody = false
                            inNotesBody = false
                        }
                        "title" -> if (inBody && !inNotesBody && inTitle) {
                            inTitle = false
                            val chapterTitle = textBuffer.toString()
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            textBuffer.setLength(0)
                            if (chapterTitle.isNotEmpty()) {
                                val chapIdx = chapters.size
                                val startPara = paragraphs.size
                                chapters.add(
                                    BookChapter(
                                        index = chapIdx,
                                        title = chapterTitle,
                                        startParagraphIndex = startPara
                                    )
                                )
                                paragraphs.add(
                                    BookParagraph(
                                        index = startPara,
                                        text = chapterTitle,
                                        type = ParagraphType.CHAPTER_TITLE,
                                        chapterIndex = chapIdx
                                    )
                                )
                            }
                        }
                        "epigraph" -> inEpigraph = false
                        "poem" -> inPoem = false
                        "cite" -> inCite = false
                        "subtitle" -> if (inBody && !inNotesBody && !inTitle) {
                            val sub = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
                            textBuffer.setLength(0)
                            if (sub.isNotEmpty()) {
                                val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                                paragraphs.add(
                                    BookParagraph(
                                        index = paragraphs.size,
                                        text = sub,
                                        type = ParagraphType.SUBTITLE,
                                        chapterIndex = chapIdx
                                    )
                                )
                            }
                        }
                        "p", "v" -> if (inBody && !inNotesBody && !inTitle) {
                            val pText = textBuffer.toString().replace(Regex("\\s+"), " ").trim()
                            textBuffer.setLength(0)
                            if (pText.isNotEmpty()) {
                                val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                                val pType = when {
                                    inEpigraph -> ParagraphType.EPIGRAPH
                                    inPoem || tag == "v" -> ParagraphType.POEM
                                    inCite -> ParagraphType.QUOTE
                                    else -> ParagraphType.BODY
                                }
                                paragraphs.add(
                                    BookParagraph(
                                        index = paragraphs.size,
                                        text = pText,
                                        type = pType,
                                        chapterIndex = chapIdx
                                    )
                                )
                            }
                        }
                        "binary" -> {
                            val binId = currentBinaryId
                            val b64 = textBuffer.toString().trim()
                            textBuffer.setLength(0)
                            currentBinaryId = null
                            if (!binId.isNullOrEmpty() && b64.isNotEmpty() &&
                                (binId == coverHrefId || binaries.isEmpty())
                            ) {
                                runCatching {
                                    binaries[binId] = Base64.decode(b64, Base64.DEFAULT)
                                }
                            }
                        }
                    }
                    if (!inTitle && currentTag == tag) {
                        textBuffer.setLength(0)
                    }
                }
            }
            eventType = parser.next()
        }

        if (paragraphs.isEmpty()) {
            throw BookParseException("В книге FB2 «$fallbackTitle» не найден текстовый контент.")
        }

        val finalTitle = bookTitle.ifEmpty { fallbackTitle }
        val finalAuthor = listOf(authorFirstName, authorMiddleName, authorLastName)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "Неизвестный автор" }

        if (chapters.isEmpty()) {
            chapters.add(BookChapter(index = 0, title = finalTitle, startParagraphIndex = 0))
        }

        val coverBytes = coverHrefId?.let { binaries[it] } ?: binaries.values.firstOrNull()

        return ParsedBookContent(
            title = finalTitle,
            author = finalAuthor,
            format = BookFormat.FB2,
            coverBytes = coverBytes,
            chapters = chapters,
            paragraphs = paragraphs,
            encoding = encodingUsed
        )
    }

    // =========================================================================
    // 3. EPUB Parser (META-INF/container.xml -> OPF -> NCX/Nav -> Spine XHTML)
    // =========================================================================

    private fun parseEpub(file: File, fallbackTitle: String): ParsedBookContent {
        ZipFile(file).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: zip.entries().asSequence().firstOrNull {
                    it.name.endsWith("container.xml", ignoreCase = true)
                }
                ?: throw BookParseException("Файл «$fallbackTitle» не является корректным EPUB (отсутствует META-INF/container.xml).")

            val opfPath = parseContainerXmlForOpfPath(zip.readEntryText(containerEntry))
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
                ?: throw BookParseException("В EPUB «$fallbackTitle» не найден пакет метаданных (.opf).")

            val opfEntry = findZipEntry(zip, opfPath)
                ?: throw BookParseException("В EPUB «$fallbackTitle» отсутствует файл $opfPath.")

            val opfDir = opfPath.substringBeforeLast('/', "")
            val opfData = parseOpfPackage(zip.readEntryText(opfEntry))

            // Extract cover image if available
            val coverBytes = opfData.coverHref?.let { relCover ->
                val resolvedCoverPath = resolveRelativePath(opfDir, relCover)
                findZipEntry(zip, resolvedCoverPath)?.let { entry ->
                    runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull()
                }
            }

            // Parse NCX Table of Contents mapping (href -> chapter title)
            val tocTitleMap = mutableMapOf<String, String>()
            opfData.ncxHref?.let { relNcx ->
                val ncxPath = resolveRelativePath(opfDir, relNcx)
                findZipEntry(zip, ncxPath)?.let { entry ->
                    val ncxDir = ncxPath.substringBeforeLast('/', "")
                    parseNcxToc(zip.readEntryText(entry), ncxDir, tocTitleMap)
                }
            }

            val chapters = mutableListOf<BookChapter>()
            val paragraphs = mutableListOf<BookParagraph>()

            val spineHrefs = if (opfData.spineHrefs.isNotEmpty()) {
                opfData.spineHrefs
            } else {
                opfData.manifestHrefs.values.filter {
                    it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true)
                }
            }

            for (relHref in spineHrefs) {
                val cleanHref = relHref.substringBefore('#')
                val fullPath = resolveRelativePath(opfDir, cleanHref)
                val entry = findZipEntry(zip, fullPath) ?: continue
                val htmlContent = zip.readEntryText(entry)

                val ncxChapterTitle = tocTitleMap[fullPath] ?: tocTitleMap[cleanHref]
                parseXhtmlIntoParagraphs(
                    html = htmlContent,
                    defaultChapterTitle = ncxChapterTitle,
                    chapters = chapters,
                    paragraphs = paragraphs
                )
            }

            if (paragraphs.isEmpty()) {
                throw BookParseException("В EPUB «$fallbackTitle» не найдено текстовых глав.")
            }

            val finalTitle = opfData.title.ifEmpty { fallbackTitle }
            val finalAuthor = opfData.author.ifEmpty { "Неизвестный автор" }

            if (chapters.isEmpty()) {
                chapters.add(BookChapter(index = 0, title = finalTitle, startParagraphIndex = 0))
            }

            return ParsedBookContent(
                title = finalTitle,
                author = finalAuthor,
                format = BookFormat.EPUB,
                coverBytes = coverBytes,
                chapters = chapters,
                paragraphs = paragraphs,
                encoding = "UTF-8"
            )
        }
    }

    private fun ZipFile.readEntryText(entry: ZipEntry): String {
        val bytes = getInputStream(entry).use { it.readBytes() }
        return detectCharsetAndDecode(bytes).first
    }

    private fun findZipEntry(zip: ZipFile, path: String): ZipEntry? {
        val normalized = path.removePrefix("/")
        zip.getEntry(normalized)?.let { return it }
        return zip.entries().asSequence().firstOrNull {
            it.name.equals(normalized, ignoreCase = true) ||
                it.name.endsWith("/$normalized", ignoreCase = true)
        }
    }

    private fun resolveRelativePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.substringBefore('#').trim()
        val combined = if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref"
        val parts = mutableListOf<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun parseContainerXmlForOpfPath(xml: String): String? {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xml.reader())
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name?.endsWith("rootfile") == true) {
                    val fullPath = parser.getAttributeValue(null, "full-path")
                    if (!fullPath.isNullOrBlank()) return@runCatching fullPath
                }
                event = parser.next()
            }
            null
        }.getOrNull()
    }

    private data class OpfPackageData(
        val title: String,
        val author: String,
        val coverHref: String?,
        val ncxHref: String?,
        val manifestHrefs: Map<String, String>,
        val spineHrefs: List<String>
    )

    private fun parseOpfPackage(xml: String): OpfPackageData {
        var title = ""
        var author = ""
        var coverMetaId: String? = null
        var coverPropertyHref: String? = null
        var ncxHref: String? = null
        val manifest = mutableMapOf<String, String>()
        val manifestMediaTypes = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())

        var currentText = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentText.setLength(0)
                    val tag = (parser.name ?: "").substringAfter(':')
                    when (tag) {
                        "meta" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val contentAttr = parser.getAttributeValue(null, "content")
                            if (nameAttr.equals("cover", ignoreCase = true) && !contentAttr.isNullOrBlank()) {
                                coverMetaId = contentAttr
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                manifest[id] = href
                                manifestMediaTypes[id] = mediaType
                                if (properties.contains("cover-image") && coverPropertyHref == null) {
                                    coverPropertyHref = href
                                }
                                if (mediaType.contains("dtbncx") || href.endsWith(".ncx", ignoreCase = true)) {
                                    ncxHref = href
                                }
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) {
                                spineIds.add(idref)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    currentText.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    val tag = (parser.name ?: "").substringAfter(':')
                    when (tag) {
                        "title" -> if (title.isEmpty()) title = currentText.toString().trim()
                        "creator" -> if (author.isEmpty()) author = currentText.toString().trim()
                    }
                }
            }
            event = parser.next()
        }

        val resolvedCoverHref = coverPropertyHref
            ?: coverMetaId?.let { manifest[it] }
            ?: manifest.entries.firstOrNull { (id, href) ->
                val mt = manifestMediaTypes[id] ?: ""
                mt.startsWith("image/") && (id.contains("cover", true) || href.contains("cover", true))
            }?.value

        val spineHrefs = spineIds.mapNotNull { manifest[it] }

        return OpfPackageData(
            title = title,
            author = author,
            coverHref = resolvedCoverHref,
            ncxHref = ncxHref,
            manifestHrefs = manifest,
            spineHrefs = spineHrefs
        )
    }

    private fun parseNcxToc(
        ncxXml: String,
        ncxDir: String,
        targetMap: MutableMap<String, String>
    ) {
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ncxXml.reader())

            var currentLabel = ""
            val textBuf = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = (parser.name ?: "").substringAfter(':')
                        if (tag == "text") {
                            textBuf.setLength(0)
                        } else if (tag == "content") {
                            val src = parser.getAttributeValue(null, "src")
                            if (!src.isNullOrBlank() && currentLabel.isNotBlank()) {
                                val cleanSrc = src.substringBefore('#')
                                val full = resolveRelativePath(ncxDir, cleanSrc)
                                targetMap.putIfAbsent(full, currentLabel)
                                targetMap.putIfAbsent(cleanSrc, currentLabel)
                            }
                        }
                    }
                    XmlPullParser.TEXT -> textBuf.append(parser.text ?: "")
                    XmlPullParser.END_TAG -> {
                        val tag = (parser.name ?: "").substringAfter(':')
                        if (tag == "text") {
                            val t = textBuf.toString().replace(Regex("\\s+"), " ").trim()
                            if (t.isNotEmpty()) currentLabel = t
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun parseXhtmlIntoParagraphs(
        html: String,
        defaultChapterTitle: String?,
        chapters: MutableList<BookChapter>,
        paragraphs: MutableList<BookParagraph>
    ) {
        // Strip <head>, <style>, <script> blocks
        val bodyHtml = html
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")

        var chapterRegisteredForFile = false

        fun registerChapter(titleText: String) {
            val clean = titleText.trim()
            if (clean.isEmpty()) return
            val chapIdx = chapters.size
            val startPara = paragraphs.size
            chapters.add(
                BookChapter(
                    index = chapIdx,
                    title = clean,
                    startParagraphIndex = startPara
                )
            )
            paragraphs.add(
                BookParagraph(
                    index = startPara,
                    text = clean,
                    type = ParagraphType.CHAPTER_TITLE,
                    chapterIndex = chapIdx
                )
            )
            chapterRegisteredForFile = true
        }

        // Convert block tags to structured markers before stripping inline tags
        val blockMarked = bodyHtml
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>")) { match ->
                "\n@@HEADING@@" + stripHtmlTags(match.groupValues[1]) + "\n"
            }
            .replace(Regex("(?is)<h[4-6][^>]*>(.*?)</h[4-6]>")) { match ->
                "\n@@SUBTITLE@@" + stripHtmlTags(match.groupValues[1]) + "\n"
            }
            .replace(Regex("(?is)<blockquote[^>]*>(.*?)</blockquote>")) { match ->
                "\n@@QUOTE@@" + stripHtmlTags(match.groupValues[1]) + "\n"
            }
            .replace(Regex("(?is)</(p|div|li|tr|section|article)>"), "\n")

        val plainLines = stripHtmlTags(blockMarked).lines()

        if (!defaultChapterTitle.isNullOrBlank()) {
            // Check if the first heading line already matches defaultChapterTitle
            val firstNonEmpty = plainLines.firstOrNull { it.trim().isNotEmpty() }?.trim() ?: ""
            if (!firstNonEmpty.startsWith("@@HEADING@@")) {
                registerChapter(defaultChapterTitle)
            }
        }

        for (rawLine in plainLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("@@HEADING@@") -> {
                    val headingText = line.removePrefix("@@HEADING@@").trim()
                    if (headingText.isNotEmpty()) {
                        if (!chapterRegisteredForFile) {
                            registerChapter(headingText)
                        } else {
                            val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                            paragraphs.add(
                                BookParagraph(
                                    index = paragraphs.size,
                                    text = headingText,
                                    type = ParagraphType.SUBTITLE,
                                    chapterIndex = chapIdx
                                )
                            )
                        }
                    }
                }
                line.startsWith("@@SUBTITLE@@") -> {
                    val subText = line.removePrefix("@@SUBTITLE@@").trim()
                    if (subText.isNotEmpty()) {
                        val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                        paragraphs.add(
                            BookParagraph(
                                index = paragraphs.size,
                                text = subText,
                                type = ParagraphType.SUBTITLE,
                                chapterIndex = chapIdx
                            )
                        )
                    }
                }
                line.startsWith("@@QUOTE@@") -> {
                    val quoteText = line.removePrefix("@@QUOTE@@").trim()
                    if (quoteText.isNotEmpty()) {
                        val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                        paragraphs.add(
                            BookParagraph(
                                index = paragraphs.size,
                                text = quoteText,
                                type = ParagraphType.QUOTE,
                                chapterIndex = chapIdx
                            )
                        )
                    }
                }
                else -> {
                    val chapIdx = (chapters.size - 1).coerceAtLeast(0)
                    paragraphs.add(
                        BookParagraph(
                            index = paragraphs.size,
                            text = line,
                            type = ParagraphType.BODY,
                            chapterIndex = chapIdx
                        )
                    )
                }
            }
        }
    }

    private fun stripHtmlTags(input: String): String {
        val withoutTags = input.replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(withoutTags)
            .replace(Regex("[ \\t\\x0B\\f]+"), " ")
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&bdquo;", "„")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&hellip;", "…")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 32..0x10FFFF) code.toChar().toString() else ""
            }
    }
}
