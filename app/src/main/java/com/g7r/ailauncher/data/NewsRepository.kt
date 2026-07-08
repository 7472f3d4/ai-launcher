package com.g7r.ailauncher.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val pubDate: String
)

/**
 * Fetches live headlines from Google News (Japan top stories RSS) and parses them with
 * XmlPullParser. Titles arrive as "Headline - Source"; we split off the source for display.
 */
object NewsRepository {

    private const val FEED_URL = "https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja"

    fun fetchHeadlines(limit: Int = 8): List<NewsItem> = runCatching {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AILauncher")
        }
        connection.inputStream.use { stream ->
            parse(stream, limit)
        }
    }.getOrDefault(emptyList())

    private fun parse(stream: java.io.InputStream, limit: Int): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val items = ArrayList<NewsItem>()
        var inItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var current = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    current = parser.name
                    if (current.equals("item", ignoreCase = true)) {
                        inItem = true
                        title = ""; link = ""; pubDate = ""
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (current.lowercase()) {
                            "title" -> title += text
                            "link" -> link += text
                            "pubdate" -> pubDate += text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        inItem = false
                        val (headline, source) = splitSource(title)
                        if (headline.isNotBlank()) {
                            items.add(NewsItem(headline, source, link, pubDate))
                        }
                    }
                    current = ""
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun splitSource(raw: String): Pair<String, String> {
        val idx = raw.lastIndexOf(" - ")
        return if (idx > 0) {
            raw.substring(0, idx).trim() to raw.substring(idx + 3).trim()
        } else {
            raw.trim() to ""
        }
    }
}
