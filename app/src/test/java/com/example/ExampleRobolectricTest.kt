package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BookFormat
import com.example.data.model.ReaderSettings
import com.example.data.parser.BookParseException
import com.example.data.parser.BookParser
import com.example.data.parser.SampleTestBooksGenerator
import com.example.ui.reader.BookPaginator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Reader", appName)
    }

    @Test
    fun `three sample books EPUB FB2 TXT parse and paginate accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDir = File(context.cacheDir, "test_books")
        val files = SampleTestBooksGenerator.generateThreeSampleBooks(tempDir)
        assertEquals(3, files.size)

        val epubParsed = BookParser.parseFile(files[0])
        assertEquals(BookFormat.EPUB, epubParsed.format)
        assertEquals("Белые ночи", epubParsed.title)
        assertNotNull(epubParsed.coverBytes)
        assertTrue(epubParsed.chapters.size >= 3)
        assertTrue(epubParsed.paragraphs.size >= 10)

        val fb2Parsed = BookParser.parseFile(files[1])
        assertEquals(BookFormat.FB2, fb2Parsed.format)
        assertEquals("Дама с собачкой", fb2Parsed.title)
        assertNotNull(fb2Parsed.coverBytes)
        assertTrue(fb2Parsed.chapters.size >= 4)

        val txtParsed = BookParser.parseFile(files[2])
        assertEquals(BookFormat.TXT, txtParsed.format)
        assertEquals("Windows-1251", txtParsed.encoding)
        assertEquals("Пиковая дама", txtParsed.title)
        assertTrue(txtParsed.chapters.size >= 4)

        // Verify paragraph anchor preservation across font size & orientation changes
        val targetParagraph = 8
        val smallFontPages = BookPaginator.paginate(
            paragraphs = epubParsed.paragraphs,
            chapters = epubParsed.chapters,
            viewportWidthDp = 411f,
            viewportHeightDp = 891f,
            settings = ReaderSettings(fontSizeSp = 16f)
        )
        val largeFontPages = BookPaginator.paginate(
            paragraphs = epubParsed.paragraphs,
            chapters = epubParsed.chapters,
            viewportWidthDp = 891f,
            viewportHeightDp = 411f,
            settings = ReaderSettings(fontSizeSp = 26f)
        )
        val pageSmall = BookPaginator.findPageForParagraph(smallFontPages, targetParagraph)
        val pageLarge = BookPaginator.findPageForParagraph(largeFontPages, targetParagraph)

        assertTrue(targetParagraph in smallFontPages[pageSmall].startParagraphIndex..smallFontPages[pageSmall].endParagraphIndex)
        assertTrue(targetParagraph in largeFontPages[pageLarge].startParagraphIndex..largeFontPages[pageLarge].endParagraphIndex)
    }

    @Test(expected = BookParseException::class)
    fun `corrupted epub throws friendly BookParseException without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val broken = File(context.cacheDir, "broken.epub")
        broken.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0xFF.toByte()))
        BookParser.parseFile(broken)
    }
}
