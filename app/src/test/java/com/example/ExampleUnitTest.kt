package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testParseYouTubeFeed() {
    val sampleXml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
        <entry>
          <id>yt:video:RUcLIMTsSo8</id>
          <yt:videoId>RUcLIMTsSo8</yt:videoId>
          <title>Toccata animation in the Making</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=RUcLIMTsSo8"/>
          <author><name>Kaspa</name></author>
          <published>2026-07-07T18:19:12+00:00</published>
          <media:group>
            <media:title>Toccata animation in the Making</media:title>
            <media:thumbnail url="https://i3.ytimg.com/vi/RUcLIMTsSo8/hqdefault.jpg" width="480" height="360"/>
            <media:description>The Making of the Toccata Launch Animation</media:description>
          </media:group>
        </entry>
      </feed>
    """.trimIndent()

    val items = parseRssXml(sampleXml, "YouTube")
    assertEquals(1, items.size)
    val item = items[0]
    assertEquals("Toccata animation in the Making", item.title)
    assertEquals("https://www.youtube.com/watch?v=RUcLIMTsSo8", item.url)
    assertEquals("RUcLIMTsSo8", item.videoId)
    assertEquals("YouTube", item.category)
    assertEquals("Kaspa", item.author)
  }

  @Test
  fun testRssItemUsesDirectArticleLinkAndFiltersUnrelatedArticles() {
    val sampleXml = """
      <rss version="2.0">
        <channel>
          <title>Cointelegraph.com News</title>
          <atom:link href="https://cointelegraph.com/rss/tag/kaspa" rel="self" type="application/rss+xml"/>
          <item>
            <title>Kaspa developers publish a BlockDAG update</title>
            <link>https://cointelegraph.com/news/kaspa-blockdag-update</link>
            <description>New details about Kaspa development.</description>
            <pubDate>Sun, 25 Oct 2026 18:48:34 +0000</pubDate>
          </item>
          <item>
            <title>Cardano price drops as treasury proposal changes</title>
            <link>https://cointelegraph.com/news/cardano-treasury-proposal</link>
            <description>Cardano market news only.</description>
            <pubDate>Sun, 25 Oct 2026 18:00:00 +0000</pubDate>
          </item>
        </channel>
      </rss>
    """.trimIndent()

    val items = parseRssXml(sampleXml, "News")
    assertEquals(1, items.size)
    assertEquals("Kaspa developers publish a BlockDAG update", items.single().title)
    assertEquals("https://cointelegraph.com/news/kaspa-blockdag-update", items.single().url)
    assertFalse(items.single().url.contains("/rss/"))
  }

  @Test
  fun testFeedEndpointIsNotAValidArticleDestination() {
    val feed = KaspaNewsItem(
      title = "Kaspa market update",
      desc = "Kaspa news",
      url = "https://www.crypto-news-flash.com/tag/kaspa/feed/",
      category = "News",
      timestamp = "Recently"
    )
    assertFalse(isRelevantKaspaNews(feed))
  }
}
