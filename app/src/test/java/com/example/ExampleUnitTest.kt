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
}

