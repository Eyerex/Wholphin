package com.github.damontecres.wholphin.ui.jellyeye

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Jellyeye: the holiday seasons, worked out from the date (same rules as the web version).
 */
data class JellyeyeSeason(
    val key: String,
    val name: String,
    val target: LocalDate,
    val targetName: String,
    val colorA: Color,
    val colorB: Color,
    val tags: List<String>,
    val searchTerms: List<String>,
    val genres: List<String>,
    val rowTitle: String,
    val rowWhy: String,
) {
    fun daysLeft(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, target).coerceAtLeast(0)

    companion object {
        private fun easterSunday(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate.of(year, month, day)
        }

        fun detect(today: LocalDate = LocalDate.now()): JellyeyeSeason? {
            val y = today.year
            val month = today.monthValue
            val day = today.dayOfMonth
            fun d(m: Int, dd: Int, yy: Int = y) = LocalDate.of(yy, m, dd)
            fun between(from: LocalDate, to: LocalDate) = !today.isBefore(from) && today.isBefore(to)
            val easter = easterSunday(y)

            val key =
                when {
                    !today.isBefore(d(11, 15)) || month == 1 && day <= 6 -> "christmas"
                    between(d(11, 1), d(11, 6)) -> "bonfire"
                    month == 10 -> "halloween"
                    between(d(2, 1), d(2, 15)) -> "valentines"
                    between(easter.minusDays(14), easter.plusDays(2)) -> "easter"
                    between(d(7, 20), d(9, 4)) -> "summer"
                    else -> null
                } ?: return null

            return when (key) {
                "christmas" -> {
                    val (target, name) =
                        when {
                            month == 1 -> d(1, 6) to "Twelfth Night"
                            month == 12 && day > 25 -> d(1, 1, y + 1) to "New Year"
                            else -> d(12, 25) to "Christmas"
                        }
                    JellyeyeSeason(
                        key, "Christmas", target, name, Color(0xFFB3282D), Color(0xFF1F5A3A),
                        tags = listOf("Christmas", "christmas", "Xmas", "xmas"),
                        searchTerms = listOf("Christmas", "Xmas", "Santa"),
                        genres = emptyList(),
                        rowTitle = "Christmas in your library",
                        rowWhy = "Found by Christmas tags, keywords and titles",
                    )
                }
                "halloween" ->
                    JellyeyeSeason(
                        key, "Halloween", d(10, 31), "Halloween", Color(0xFFD35400), Color(0xFF2C1A3D),
                        tags = listOf("Halloween", "halloween"), searchTerms = listOf("Halloween"), genres = listOf("Horror"),
                        rowTitle = "Spooky season", rowWhy = "Halloween and horror from your library",
                    )
                "bonfire" ->
                    JellyeyeSeason(
                        key, "Bonfire Night", d(11, 5), "Bonfire Night", Color(0xFFE67E22), Color(0xFF1A1A2E),
                        tags = listOf("fireworks", "gunpowder plot"), searchTerms = listOf("Guy Fawkes", "Fireworks"), genres = emptyList(),
                        rowTitle = "Remember, remember", rowWhy = "Gunpowder, plots and things that go bang",
                    )
                "valentines" ->
                    JellyeyeSeason(
                        key, "Valentine's", d(2, 14), "Valentine's Day", Color(0xFFC2185B), Color(0xFF3A1426),
                        tags = listOf("valentine"), searchTerms = listOf("Valentine"), genres = listOf("Romance"),
                        rowTitle = "Date night", rowWhy = "Romance from your library",
                    )
                "easter" ->
                    JellyeyeSeason(
                        key, "Easter", easter, "Easter Sunday", Color(0xFF7CB342), Color(0xFF2E3B1F),
                        tags = listOf("easter"), searchTerms = listOf("Easter"), genres = listOf("Family"),
                        rowTitle = "Easter weekend", rowWhy = "Family films for the long weekend",
                    )
                else ->
                    JellyeyeSeason(
                        key, "Summer holidays", d(9, 4), "the end of the holidays", Color(0xFFF39C12), Color(0xFF14506B),
                        tags = listOf("summer"), searchTerms = emptyList(), genres = listOf("Adventure", "Family"),
                        rowTitle = "Summer blockbusters", rowWhy = "Big adventures for long evenings",
                    )
            }
        }
    }
}
