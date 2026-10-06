package app.tabunugoku.koepocket

import app.tabunugoku.koepocket.data.VoiceItem
import app.tabunugoku.koepocket.ui.filterByGender
import org.junit.Assert.assertEquals
import org.junit.Test

class GenderFilterTest {
    private fun item(id: Long, gender: String) = VoiceItem(id, "t$id", "a", gender, "1分", 0, 0, "")
    private val items = listOf(item(1, "female"), item(2, "male"), item(3, "couple"), item(4, ""))

    @Test fun emptyMeansNoFilter() = assertEquals(items, filterByGender(items, emptySet()))

    @Test fun singleGender() = assertEquals(listOf(2L), filterByGender(items, setOf("male")).map { it.id })

    @Test fun multipleGenders() =
        assertEquals(listOf(1L, 3L), filterByGender(items, setOf("female", "couple")).map { it.id })
}
