package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreClassificationTest {

  data class MovieContent(
    val id: String,
    val firebaseId: String,
    val title: String,
    val genres: List<String> = emptyList(),
    val category: String = "مسلسل",
    val type: String = "anime"
  )

  companion object {
    val ALL_GENRES = listOf(
      "أكشن", "مغامرات", "كوميدي", "دراما", "خيال", "خيال علمي",
      "رعب", "غموض", "رومانسي", "رياضي", "شريحة من الحياة", "خارق للطبيعة",
      "تشويق", "تاريخي", "موسيقي", "عسكري", "طبخ", "مدرسي", "ألعاب",
      "فنون قتالية", "إيسكاي (عالم مختلف)", "نفسي", "بوليسي", "سايبربانك",
      "ميكا", "شونين", "شوجو", "سينين", "جوسي", "أطفال"
    )

    fun normalizeSearchText(str: String?): String {
      if (str.isNullOrBlank()) return ""
      var s = str.lowercase().trim()
      s = s.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
      s = s.replace(Regex("[أإآٱ]"), "ا")
      s = s.replace("ى", "ي")
      s = s.replace("ة", "ه")
      s = s.replace(Regex("ـ+"), "")
      s = s.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
      s = s.replace(Regex("\\s+"), " ")
      return s
    }

    fun itemMatchesGenre(item: MovieContent, targetGenre: String): Boolean {
      val targetNorm = normalizeSearchText(targetGenre)
      if (targetNorm.isEmpty()) return false

      // 1. Direct match in item.genres
      for (g in item.genres) {
        val gNorm = normalizeSearchText(g)
        if (gNorm == targetNorm || gNorm.contains(targetNorm) || targetNorm.contains(gNorm)) {
          return true
        }
      }

      // 2. Backward compatibility with legacy category or type
      val catNorm = normalizeSearchText(item.category)
      if (catNorm == targetNorm || catNorm.contains(targetNorm)) return true
      if (targetNorm == "رياضي" && (item.type == "sports" || item.category.contains("رياض"))) return true
      if (targetNorm == "اطفال" && (item.type == "kids" || item.category.contains("طفل"))) return true

      return false
    }

    fun itemMatchesAllGenres(item: MovieContent, selectedGenres: List<String>): Boolean {
      if (selectedGenres.isEmpty()) return true
      return selectedGenres.all { itemMatchesGenre(item, it) }
    }
  }

  @Test
  fun testAllThirtyGenresAvailable() {
    assertEquals(30, ALL_GENRES.size)
    assertTrue(ALL_GENRES.contains("أكشن"))
    assertTrue(ALL_GENRES.contains("رياضي"))
    assertTrue(ALL_GENRES.contains("شونين"))
    assertTrue(ALL_GENRES.contains("إيسكاي (عالم مختلف)"))
    assertTrue(ALL_GENRES.contains("سايبربانك"))
    assertTrue(ALL_GENRES.contains("ميكا"))
  }

  @Test
  fun testSingleGenreMatching() {
    val haikyuu = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "Haikyuu!!",
      genres = listOf("رياضي", "كوميدي", "مدرسي", "شونين")
    )

    assertTrue(itemMatchesGenre(haikyuu, "رياضي"))
    assertTrue(itemMatchesGenre(haikyuu, "كوميدي"))
    assertTrue(itemMatchesGenre(haikyuu, "مدرسي"))
    assertTrue(itemMatchesGenre(haikyuu, "شونين"))
    assertFalse(itemMatchesGenre(haikyuu, "رعب"))
    assertFalse(itemMatchesGenre(haikyuu, "ميكا"))
  }

  @Test
  fun testMultiGenreFiltering() {
    val jjk = MovieContent(
      id = "1",
      firebaseId = "fb-1",
      title = "Jujutsu Kaisen",
      genres = listOf("أكشن", "خارق للطبيعة", "شونين", "خيال")
    )
    val aot = MovieContent(
      id = "2",
      firebaseId = "fb-2",
      title = "Attack on Titan",
      genres = listOf("أكشن", "دراما", "عسكري", "غموض", "شونين")
    )
    val kaguya = MovieContent(
      id = "3",
      firebaseId = "fb-3",
      title = "Kaguya-sama",
      genres = listOf("كوميدي", "رومانسي", "مدرسي")
    )

    val list = listOf(jjk, aot, kaguya)

    // Filter by single genre: "أكشن"
    val actionOnly = list.filter { itemMatchesAllGenres(it, listOf("أكشن")) }
    assertEquals(2, actionOnly.size)
    assertTrue(actionOnly.any { it.title == "Jujutsu Kaisen" })
    assertTrue(actionOnly.any { it.title == "Attack on Titan" })

    // Filter by multiple genres: "أكشن" AND "خارق للطبيعة"
    val actionSupernatural = list.filter { itemMatchesAllGenres(it, listOf("أكشن", "خارق للطبيعة")) }
    assertEquals(1, actionSupernatural.size)
    assertEquals("Jujutsu Kaisen", actionSupernatural[0].title)

    // Filter by multiple genres: "كوميدي" AND "رومانسي"
    val romCom = list.filter { itemMatchesAllGenres(it, listOf("كوميدي", "رومانسي")) }
    assertEquals(1, romCom.size)
    assertEquals("Kaguya-sama", romCom[0].title)

    // Filter by nonexistent combination
    val none = list.filter { itemMatchesAllGenres(it, listOf("رعب", "طبخ")) }
    assertTrue(none.isEmpty())
  }

  @Test
  fun testBackwardCompatibilityWithOlderItems() {
    // Old item without genres array, but with category = "رياضي"
    val legacySports = MovieContent(
      id = "legacy-1",
      firebaseId = "fb-legacy-1",
      title = "كابتن ماجد",
      genres = emptyList(),
      category = "رياضي"
    )

    assertTrue(itemMatchesGenre(legacySports, "رياضي"))
    assertFalse(itemMatchesGenre(legacySports, "رعب"))

    // Old item with no genres and generic category
    val legacyGeneric = MovieContent(
      id = "legacy-2",
      firebaseId = "fb-legacy-2",
      title = "أنمي قديم",
      genres = emptyList(),
      category = "مسلسل"
    )

    // Should not crash and safely return false for specific genre
    assertFalse(itemMatchesGenre(legacyGeneric, "أكشن"))
    // Empty genre selection matches all items
    assertTrue(itemMatchesAllGenres(legacyGeneric, emptyList()))
  }
}
