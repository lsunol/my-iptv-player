package dev.lluissunol.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerFilterTest {
    private fun entries(vararg names: String) =
        names.mapIndexed { i, n -> PickerEntry.of("id$i", n, i) }

    @Test
    fun blankQueryReturnsSameInstance() {
        val all = entries("ES - FUTBOL", "ESPAÑA - Drama")
        assertSame(all, filterPicker(all, ""))
        assertSame(all, filterPicker(all, "   "))
        assertSame(all, filterPicker(all, "--"))
    }

    @Test
    fun foldsAccentsAndCase() {
        val all = entries("ESPAÑA - Drama", "FR - Cinéma", "UK - News")
        assertEquals(listOf("ESPAÑA - Drama"), filterPicker(all, "espana").map { it.label })
        assertEquals(listOf("FR - Cinéma"), filterPicker(all, "CINEMA").map { it.label })
    }

    @Test
    fun punctuationInsensitive() {
        val all = entries("ES - FUTBOL", "ES - CINE")
        assertEquals(listOf("ES - FUTBOL"), filterPicker(all, "es-futbol").map { it.label })
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(filterPicker(entries("A", "B"), "zzz").isEmpty())
    }

    @Test
    fun keepsOrder() {
        val all = entries("B es", "A es", "C no", "D es")
        assertEquals(listOf("B es", "A es", "D es"), filterPicker(all, "es").map { it.label })
    }

    @Test
    fun moreThanTwelveEntriesAllReturned() {
        val all = (1..300).map { PickerEntry.of("g$it", "Grupo $it", it) }
        assertEquals(300, filterPicker(all, "").size)
        assertEquals(300, filterPicker(all, "grupo").size)
        assertEquals("Grupo 300", filterPicker(all, "300").single().label)
    }
}
