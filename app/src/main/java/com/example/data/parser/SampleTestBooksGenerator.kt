package com.example.data.parser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates three authentic offline test books (EPUB, FB2, and TXT in Windows-1251)
 * on local storage so the user can immediately verify all acceptance criteria on Pixel 9.
 */
object SampleTestBooksGenerator {

    fun generateThreeSampleBooks(targetBooksDir: File): List<File> {
        if (!targetBooksDir.exists()) targetBooksDir.mkdirs()

        val epubFile = File(targetBooksDir, "belye_nochi_dostoevsky.epub")
        createSampleEpub(epubFile)

        val fb2File = File(targetBooksDir, "chekhov_dama_s_sobachkoy.fb2")
        createSampleFb2(fb2File)

        val txtFile = File(targetBooksDir, "pushkin_pikovaya_dama_cp1251.txt")
        createSampleTxtWindows1251(txtFile)

        return listOf(epubFile, fb2File, txtFile)
    }

    private fun createCoverPngBytes(
        title: String,
        author: String,
        formatBadge: String,
        topColor: Int,
        bottomColor: Int,
        accentColor: Int
    ): ByteArray {
        val width = 360
        val height = 520
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                topColor, bottomColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = accentColor
            alpha = 140
        }
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 16f, 16f, framePaint)

        // Format badge pill
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor
            alpha = 50
        }
        canvas.drawRoundRect(RectF(44f, 46f, 136f, 82f), 18f, 18f, badgePaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(formatBadge, 62f, 71f, badgeTextPaint)

        // Author text
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE6DEC8.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(author, 44f, 150f, authorPaint)

        // Title lines
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFBF2.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val words = title.split(" ")
        var line = ""
        var y = 220f
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (titlePaint.measureText(test) > width - 88f) {
                canvas.drawText(line, 44f, y, titlePaint)
                line = word
                y += 46f
            } else {
                line = test
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 44f, y, titlePaint)
        }

        // Subtle ornament at bottom
        val ornamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 180
            strokeWidth = 2f
        }
        canvas.drawLine(44f, height - 68f, width - 44f, height - 68f, ornamentPaint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // 1. Authentic EPUB file (Zip with container.xml, content.opf, toc.ncx, XHTML chapters)
    // -------------------------------------------------------------------------
    private fun createSampleEpub(file: File) {
        val coverBytes = createCoverPngBytes(
            title = "Белые ночи",
            author = "Ф. М. Достоевский",
            formatBadge = "EPUB",
            topColor = 0xFF1A2639.toInt(),
            bottomColor = 0xFF0D131F.toInt(),
            accentColor = 0xFFE6B566.toInt()
        )

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()

        val contentOpf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>Белые ночи</dc:title>
                    <dc:creator>Фёдор Достоевский</dc:creator>
                    <dc:language>ru</dc:language>
                    <meta name="cover" content="cover-image"/>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="cover-image" href="cover.png" media-type="image/png"/>
                    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch3" href="chapter3.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                    <itemref idref="ch3"/>
                </spine>
            </package>
        """.trimIndent()

        val tocNcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <navMap>
                    <navPoint id="navPoint-1" playOrder="1">
                        <navLabel><text>Ночь первая</text></navLabel>
                        <content src="chapter1.xhtml"/>
                    </navPoint>
                    <navPoint id="navPoint-2" playOrder="2">
                        <navLabel><text>Ночь вторая</text></navLabel>
                        <content src="chapter2.xhtml"/>
                    </navPoint>
                    <navPoint id="navPoint-3" playOrder="3">
                        <navLabel><text>Утро</text></navLabel>
                        <content src="chapter3.xhtml"/>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()

        val ch1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Ночь первая</title></head>
            <body>
                <h1>Ночь первая</h1>
                <blockquote>…Иль был он создан для того, чтобы побыть хотя мгновенье в соседстве сердца твоего?.. — И. С. Тургенев</blockquote>
                <p>Была чудная ночь, такая ночь, которая разве только и может быть тогда, когда мы молоды, любезный читатель. Небо было такое звёздное, такое светлое небо, что, взглянув на него, невольно нужно было спросить себя: неужели же могут жить под таким небом разные сердитые и капризные люди? Это тоже молодой вопрос, любезный читатель, очень молодой, но пошли его вам Господь чаще на душу!</p>
                <p>Говоря о капризных и разных сердитых господах, я не мог не припомнить и своего благонравного поведения во весь этот день. С самого утра меня стала мучить какая-то удивительная тоска. Мне вдруг показалось, что меня, одинокого, все покидают и что все от меня отступаются.</p>
                <p>Оно, конечно, всякий вправе спросить: кто же эти все? потому что вот уже восемь лет, как я живу в Петербурге, и почти ни одного знакомства не умел завести. Но к чему мне знакомства? Мне и без того знаком весь Петербург; вот почему мне и показалось, что меня все покидают, когда весь Петербург поднялся и вдруг уехал на дачу.</p>
                <p>Мне страшно стало оставаться одному, и целые три дня я бродил по городу в глубокой тоске, решительно не понимая, что со мной делается. Ходил ли я по Невскому, ходил ли в сад или бродил по набережной — ни одного из тех лиц, с которыми я привык встречаться в том же месте, в известный час круглый год.</p>
                <p>Они, конечно, не знают меня, но я-то их знаю. Я коротко их знаю; я почти изучил их физиономии — и любуюсь на них, когда они веселы, и хандрю, когда они затуманятся. Я почти свел дружбу с одним старичком, которого встречаю каждый Божий день, в известный час, на Фонтанке.</p>
                <p>Даже и дома себе места неходят! Восемь лет я живу в одном углу и даже привык к своей комнате; а тут вдруг входишь и спрашиваешь себя: неужели всё так и останется по-старому, когда вокруг такая прозрачная, невесомая петербургская ночь?</p>
            </body>
            </html>
        """.trimIndent()

        val ch2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Ночь вторая</title></head>
            <body>
                <h1>Ночь вторая</h1>
                <p>— Ну вот, вы и дожили! — сказала мне Настенька со смехом, подавая обе руки, когда мы встретились у набережной ровно в десять часов вечера. — А я уж здесь с полчаса сижу и смотрю на воду.</p>
                <p>— Вы не поверите, Настенька, как я ждал этого часа! Целый день мне казалось, что вся вчерашняя встреча приснилась мне в каком-то золотом сне. Я боялся, что вы не придёте, что пойдёт дождь, что случится что-нибудь необыкновенное.</p>
                <p>— Ничего не случилось, и вот мы сидим на нашей скамейке. Слушайте же мою историю, только чур — не перебивать! А если станете перебивать, я совсем замолчу и уйду домой.</p>
                <p>— Я буду молчать, как рыба в Неве! Только говорите, Настенька. Мне кажется, будто я знаю вас уже целую вечность, хотя вчера впервые услышал ваш голос у чугунных перил канала.</p>
                <p>Она улыбнулась тихо и задумчиво, поправила косынку на плечах и начала свой рассказ о старенькой бабушке, о булавке, которой было приколото её платье, и о тихой жизни в маленьком мезонине под самой крышей.</p>
                <p>Белая ночь стояла над городом неподвижно; фонари не зажигались, а в тёмном зеркале канала отражались жёлтые фасады домов и тонкий шпиль вдалеке, словно вырезанный из светлого серебра.</p>
            </body>
            </html>
        """.trimIndent()

        val ch3 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Утро</title></head>
            <body>
                <h1>Утро</h1>
                <p>Мои ночи кончились утром. День был ненастный. Шёл дождь и грустно стучал в мои стёкла; в комнате было темно, на дворе пасмурно. У меня болела голова и кружилась; лихорадка кралась по членам моим.</p>
                <p>— Письмо вам, батюшка; почтальон принёс, — сказала Матрёна, наклоняясь ко мне. Я вскочил со стула, схватил конверт и сломал печать дрожащими пальцами.</p>
                <p>«О, простите, простите меня! Я на коленях умоляю вас простить меня! Я обманула и вас, и себя. Это был сон, призрак… Но я никогда не забуду той светлой минуты, когда вы открыли мне своё братское, великодушное сердце».</p>
                <p>Долго я читал эти строки, пока письмо не выпало из моих рук. Луч солнца вдруг прорезал дождевую тучу и снова спрятался; но в душе моей не осталось ни капли обиды.</p>
                <p>Да будет благословенно твоё ясное небо, Настенька! Да будет благословенна твоя милая улыбка! Боже мой! Целая минута блаженства! Да разве этого мало хоть бы и на всю жизнь человеческую?..</p>
            </body>
            </html>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun writeEntry(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            writeEntry("mimetype", "application/epub+zip".toByteArray(Charsets.UTF_8))
            writeEntry("META-INF/container.xml", containerXml.toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/content.opf", contentOpf.toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/toc.ncx", tocNcx.toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/cover.png", coverBytes)
            writeEntry("OEBPS/chapter1.xhtml", ch1.toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/chapter2.xhtml", ch2.toByteArray(Charsets.UTF_8))
            writeEntry("OEBPS/chapter3.xhtml", ch3.toByteArray(Charsets.UTF_8))
        }
    }

    // -------------------------------------------------------------------------
    // 2. Authentic FB2 file with embedded Base64 cover and multiple sections
    // -------------------------------------------------------------------------
    private fun createSampleFb2(file: File) {
        val coverBytes = createCoverPngBytes(
            title = "Дама с собачкой",
            author = "А. П. Чехов",
            formatBadge = "FB2",
            topColor = 0xFF2B1D19.toInt(),
            bottomColor = 0xFF170F0C.toInt(),
            accentColor = 0xFFD98A5C.toInt()
        )
        val coverBase64 = Base64.encodeToString(coverBytes, Base64.NO_WRAP)

        val fb2Content = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
                <description>
                    <title-info>
                        <genre>prose_classic</genre>
                        <author>
                            <first-name>Антон</first-name>
                            <middle-name>Павлович</middle-name>
                            <last-name>Чехов</last-name>
                        </author>
                        <book-title>Дама с собачкой</book-title>
                        <coverpage>
                            <image l:href="#cover.png"/>
                        </coverpage>
                        <lang>ru</lang>
                    </title-info>
                </description>
                <body>
                    <section>
                        <title><p>Глава I. На набережной в Ялте</p></title>
                        <p>Говорили, что на набережной появилось новое лицо: дама с собачкой. Дмитрий Дмитрич Гуров, проживший в Ялте уже две недели и привыкший тут, тоже стал интересоваться новыми лицами. Сидя в павильоне у Верне, он видел, как по набережной прошла молодая дама, невысокого роста блондинка, в берете; за нею бежал белый шпиц.</p>
                        <p>И потом он встречал её в городском саду и на сквере, по нескольку раз в день. Она гуляла одна, всё в том же берете, с белым шпицем; никто не знал, кто она, и называли её просто так: дама с собачкой.</p>
                        <p>«Если она здесь без мужа и без знакомых, — соображал Гуров, — то было бы не лишнее познакомиться с ней». Ему не было ещё сорока, но у него была уже дочь двенадцати лет и два сына-гимназиста.</p>
                        <p>Однажды под вечер он обедал в саду, а дама в берете подошла неторопливой походкой, чтобы занять соседний стол. Выражение её лица, походка, платье, причёска говорили ему, что она из порядочного общества, замужем, в Ялте в первый раз и одна, что ей скучно здесь.</p>
                        <p>Он ласково поманил к себе шпица и, когда собачка подошла, погрозил ей пальцем. Шпиц заворчал. Гуров опять погрозил. Дама взглянула на него и тотчас же опустила глаза.</p>
                        <p>— Он не кусается, — сказала она и покраснела.</p>
                        <p>— Можно дать ему кость? — и когда она утвердительно кивнула головой, он спросил приветливо: — Вы давно изволите быть в Ялте?</p>
                        <p>— Дней пять.</p>
                        <p>— А я уже дотягиваю вторую неделю. Время идёт быстро, и между тем здесь такая скука! — сказала она, не глядя на него.</p>
                    </section>
                    <section>
                        <title><p>Глава II. Ореанда и морской прибой</p></title>
                        <p>Прошло больше недели со дня знакомства. Был праздничный день. В комнатах было душно, а на улицах вихрем носилась пыль, срывало шляпы. Весь день хотелось пить, и Гуров часто заходил в павильон и предлагал Анне Сергеевне то воды с сиропом, то мороженого.</p>
                        <p>Вечером, когда немного утихло, они пошли на мол, чтобы посмотреть, как придёт пароход. На пристани было много гуляющих; собрались встречать кого-то, держали букеты. И тут отчётливо бросались в глаза две особенности нарядной ялтинской толпы: пожилые дамы были одеты, как молодые, и было много генералов.</p>
                        <p>В Ореанде сидели на скамье, недалеко от церкви, смотрели вниз на море и молчали. Ялта была едва видна сквозь утренний туман, на вершинах гор неподвижно стояли белые облака. Листва не шевелилась на деревьях, кричали цикады, и однообразный, глухой шум моря, доносившийся снизу, говорил о покое, о вечном сне, какой ожидает нас.</p>
                        <p>Так шумело внизу, когда ещё тут не было ни Ялты, ни Ореанды, сейчас шумит и будет шуметь так же равнодушно и глухо, когда нас не будет. И в этом постоянстве, в полном равнодушии к жизни и смерти каждого из нас кроется, быть может, залог нашего вечного спасения, непрерывного движения жизни на земле.</p>
                    </section>
                    <section>
                        <title><p>Глава III. Возвращение в Москву</p></title>
                        <p>Дома в Москве уже всё было по-зимнему, топили печи, и по утрам, когда дети собирались в гимназию и пили чай, было темно, и няня ненадолго зажигала огонь. Уже начались морозы. Когда идёт первый снег, в первый день катанья на санях, приятно видеть белую землю, белые крыши, дышится мягко, славно, и в это время вспоминаются юные годы.</p>
                        <p>У старых лип и берёз, белых от инея, добродушное выражение, они ближе к сердцу, чем кипарисы и пальмы, и вблизи них уже не хочется думать о горах и море. Гуров был москвич, вернулся он в Москву в хороший, морозный день, и когда надел шубу и тёплые перчатки и прошёлся по Петровке, и когда в субботу вечером услышал звон колоколов, то недавняя поездка и места, в которых он был, утеряли для него всякую прелесть.</p>
                        <p>Но прошло больше месяца, наступила глубокая зима, а в памяти всё было ясно, точно расстался он с Анной Сергеевной только вчера. И воспоминания разгорались всё сильнее. Доносились ли в вечерней тишине в его кабинет голоса детей, приготовлявших уроки, слышал ли он романс или орган в ресторане, или завывала в камине метель, как вдруг воскресало в памяти всё: и то, что было на молу, и раннее утро с туманом на горах, и пароход из Феодосии, и поцелуи.</p>
                    </section>
                    <section>
                        <title><p>Глава IV. Самое сложное и трудное</p></title>
                        <p>И только теперь, когда у него голова стала седой, он полюбил по-настоящему, по-человечески — первый раз в жизни. Анна Сергеевна и он любили друг друга, как очень близкие, родные люди, как муж и жена, как нежные друзья; им казалось, что сама судьба предназначила их друг для друга.</p>
                        <p>Потом они долго советовались, говорили о том, как избавить себя от необходимости прятаться, обманывать, жить в разных городах, не видеться подолгу. Как освободиться от этих невыносимых пут?</p>
                        <p>— Как? Как? — спрашивал он, хватая себя за голову. — Как?</p>
                        <p>И казалось, что ещё немного — и решение будет найдено, и тогда начнётся новая, прекрасная жизнь; и обоим было ясно, что до конца ещё далеко-далеко и что самое сложное и трудное только ещё начинается.</p>
                    </section>
                </body>
                <binary id="cover.png" content-type="image/png">$coverBase64</binary>
            </FictionBook>
        """.trimIndent()

        file.writeBytes(fb2Content.toByteArray(Charsets.UTF_8))
    }

    // -------------------------------------------------------------------------
    // 3. Authentic TXT file encoded in Windows-1251 (CP1251) to verify auto-detection
    // -------------------------------------------------------------------------
    private fun createSampleTxtWindows1251(file: File) {
        val text = """
            Название: Пиковая дама
            Автор: Александр Пушкин

            Глава I

            Однажды играли в карты у конногвардейца Нарумова. Долгая зимняя ночь прошла незаметно; сели ужинать в пятом часу утра. Те, которые остались в выигрыше, ели с большим аппетитом; прочие, в рассеянности, сидели перед пустыми своими приборами. Но шампанское явилось, разговор оживился, и все приняли в нём участие.

            — Что ты сделал, Сурин? — спросил хозяин.
            — Проиграл, по обыкновению. Надобно признаться, что я несчастлив: играю мирандолем, никогда не горячусь, ничем меня с толку не собьёшь, а всё проигрываю!
            — И ты ни разу не соблазнился? ни разу не поставил на руте?.. Твёрдость твоя для меня удивительна.

            — А каков Германн! — сказал один из гостей, указывая на молодого инженера: — отроду не брал он карты в руки, отроду не загнул ни одного пароли, а до пяти часов сидит с нами и смотрит на нашу игру!

            — Игра занимает меня сильно, — сказал Германн, — но я не в состоянии жертвовать необходимым в надежде приобрести излишнее.
            — Германн немец: он расчётлив, вот и всё! — заметил Томский. — А если кто для меня непонятен, так это моя бабушка, графиня Анна Федотовна.

            Глава II

            Старая графиня сидела в своей уборной перед зеркалом. Три девушки окружали её. Одна держала банку румян, другая коробку со шпильками, третья высокий чепец с лентами огненного цвета. Графиня не имела ни малейшего притязания на красоту, давно увядшую, но сохраняла все привычки своей молодости, строго следовала модам семидесятых годов и одевалась так же долго, так же старательно, как и шестьдесят лет тому назад.

            У окошка сидела барышня, её воспитанница, за пяльцами.
            — Здравствуйте, grand'maman, — сказал вошедший молодой офицер. — Bonjour, mademoiselle Lise. Бабушка, я к вам с просьбою.
            — Что такое, Поль?
            — Позвольте вам представить одного из моих приятелей и привезти его к вам в пятницу на бал.

            Глава III

            Лизавета Ивановна сидела в своей комнате, ещё в бальном своём наряде, погружённая в глубокие размышления. Приехав домой, она спешила отослать заспанную девку, нехотя предлагавшую ей свою услугу, — сказала, что разденется сама, и с трепетом вошла к себе, надеясь найти там Германна и желая не найти его. С первого взгляда она удостоверилась в его отсутствии и благодарила судьбу за препятствие, помешавшее их свиданию.

            Она села, не раздеваясь, и стала припоминать все обстоятельства, в такое короткое время и так далеко её заведшие. Не прошло трёх недель с той поры, как она в первый раз увидела в окошко молодого человека, — и уже она была с ним в переписке, и он успел вытребовать от неё ночное свидание!

            Глава IV

            Тройка, семёрка, туз — скоро заслонили в воображении Германна образ мёртвой старухи. Тройка, семёрка, туз — не выходили из его головы и шевелились на его губах. Увидев молодую девушку, он говорил: «Как она стройна!.. Настоящая тройка червонная». У него спрашивали: «Который час?» — он отвечал: «Без пяти минут семёрка». Всякий пузастый мужчина напоминал ему туза.

            В следующую ночь он поставил свою первую карту. Чекалинский стасовал. Германн отметил тройку, покрыл её кипою банковых билетов и выиграл ровно сорок семь тысяч рублей.
        """.trimIndent()

        val cp1251 = Charset.forName("windows-1251")
        file.writeBytes(text.toByteArray(cp1251))
    }
}
