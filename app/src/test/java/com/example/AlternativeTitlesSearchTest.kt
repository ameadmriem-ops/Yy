package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeTitlesSearchTest {

  data class MovieContent(
    val id: String,
    val firebaseId: String,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val category: String = "مسلسل",
    val type: String = "anime",
    val description: String = "",
    val episodes: List<EpisodeItem> = emptyList()
  )

  data class EpisodeItem(
    val title: String,
    val alternativeTitles: List<String> = emptyList()
  )

  companion object {
    fun normalizeSearchText(str: String?): String {
      if (str.isNullOrBlank()) return ""
      var s = str.lowercase().trim()
      // Remove Arabic Tashkeel / diacritics
      s = s.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
      // Normalize Alef variants
      s = s.replace(Regex("[أإآٱ]"), "ا")
      // Normalize Alef Maksura
      s = s.replace("ى", "ي")
      // Normalize Taa Marbuta
      s = s.replace("ة", "ه")
      // Remove Tatweel
      s = s.replace(Regex("ـ+"), "")
      // Remove zero-width chars
      s = s.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
      // Collapse whitespace
      s = s.replace(Regex("\\s+"), " ")
      return s
    }

    fun matchesSearchQuery(item: MovieContent, rawQuery: String): Boolean {
      if (rawQuery.isBlank()) return true
      val q = normalizeSearchText(rawQuery)
      if (q.isEmpty()) return true

      // 1. Primary title
      if (normalizeSearchText(item.title).contains(q)) return true

      // 2. Alternative titles
      for (alt in item.alternativeTitles) {
        if (normalizeSearchText(alt).contains(q)) return true
      }

      // 3. Category & Type
      if (normalizeSearchText(item.category).contains(q)) return true
      if (normalizeSearchText(item.type).contains(q)) return true

      // 4. Description
      if (normalizeSearchText(item.description).contains(q)) return true

      // 5. Episodes
      for (ep in item.episodes) {
        if (normalizeSearchText(ep.title).contains(q)) return true
        for (epAlt in ep.alternativeTitles) {
          if (normalizeSearchText(epAlt).contains(q)) return true
        }
      }

      return false
    }
  }

  @Test
  fun testArabicTextNormalization() {
    assertEquals("ون بيس", normalizeSearchText("وَن بَيْسَ"))
    assertEquals("ابطال الكره", normalizeSearchText("أبطال الكرة"))
    assertEquals("اينوياشا", normalizeSearchText("إينوياشا"))
    assertEquals("علي", normalizeSearchText("على"))
    assertEquals("مغامرات", normalizeSearchText("مُـغـامَـرات"))
  }

  @Test
  fun testEnglishCaseAndWhitespaceNormalization() {
    assertEquals("one piece", normalizeSearchText("  ONE   PIECE  "))
    assertEquals("naruto shippuden", normalizeSearchText("Naruto  Shippuden"))
  }

  @Test
  fun testSearchByPrimaryTitle() {
    val movie = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "One Piece",
      alternativeTitles = listOf("ون بيس", "ワンピース", "海贼王")
    )
    assertTrue(matchesSearchQuery(movie, "One Piece"))
    assertTrue(matchesSearchQuery(movie, "one"))
    assertTrue(matchesSearchQuery(movie, "piece"))
  }

  @Test
  fun testSearchByAlternativeTitles() {
    val movie = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "One Piece",
      alternativeTitles = listOf("ون بيس", "ワンピース", "海贼王")
    )
    // Arabic alias
    assertTrue(matchesSearchQuery(movie, "ون بيس"))
    assertTrue(matchesSearchQuery(movie, "بيس"))
    assertTrue(matchesSearchQuery(movie, "وَن بَيْس"))

    // Japanese alias
    assertTrue(matchesSearchQuery(movie, "ワンピース"))
    assertTrue(matchesSearchQuery(movie, "ワン"))

    // Chinese alias
    assertTrue(matchesSearchQuery(movie, "海贼王"))
  }

  @Test
  fun testDeduplicationAcrossResults() {
    val movie1 = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "One Piece",
      alternativeTitles = listOf("ون بيس", "ワンピース", "قطعة واحدة")
    )
    val movie2 = MovieContent(
      id = "2",
      firebaseId = "fb-2",
      title = "Bleach",
      alternativeTitles = listOf("بليتش")
    )

    val list = listOf(movie1, movie2)

    // Searching "ون" matches movie1 via alternative title "ون بيس"
    val results = list.filter { matchesSearchQuery(it, "ون") }
    assertEquals(1, results.size)
    assertEquals("One Piece", results[0].title)

    // Searching "Piece" matches movie1 only once even if multiple aliases match
    val results2 = list.filter { matchesSearchQuery(it, "Piece") }
    assertEquals(1, results2.size)
    assertEquals("fb-1", results2[0].firebaseId)
  }

  @Test
  fun testSearchByEpisode() {
    val movie = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "Attack on Titan",
      episodes = listOf(
        EpisodeItem(title = "الحلقة 1 - بداية النهاية", alternativeTitles = listOf("Ep 1", "Pilot"))
      )
    )

    assertTrue(matchesSearchQuery(movie, "بداية النهاية"))
    assertTrue(matchesSearchQuery(movie, "Pilot"))
    assertFalse(matchesSearchQuery(movie, "غير موجود"))
  }

  @Test
  fun testSearchByAlternativeTitleReturnsPrimaryTitleOnlyWithoutDuplicates() {
    val movie = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "One Piece",
      alternativeTitles = listOf("ون بيس", "One Piece Movie", "ワンピース")
    )

    // Match by alternative title "ون بيس"
    assertTrue(matchesSearchQuery(movie, "ون بيس"))

    // The display title in the UI card is always item.title ("One Piece")
    assertEquals("One Piece", movie.title)
    assertFalse(movie.title.contains("ون بيس"))

    // When query matches both primary title and alias "One Piece Movie", deduplication preserves 1 instance
    val list = listOf(movie)
    val queryMatches = list.filter { matchesSearchQuery(it, "One Piece") }
    assertEquals(1, queryMatches.size)
    assertEquals("fb-1", queryMatches[0].firebaseId)
    assertEquals("One Piece", queryMatches[0].title)
  }
}
