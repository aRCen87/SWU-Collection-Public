package fr.swucollection.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ListPaletteTest {
    @Test fun listLabelsRemainReadableOnBothDarkSurfaces() {
        val labels = listOf(ListPalette.Text, ListPalette.Secondary, ListPalette.Gold, ListPalette.Danger)
        val backgrounds = listOf(ListPalette.Background, ListPalette.Raised)
        for (label in labels) for (background in backgrounds) {
            assertTrue("Insufficient list text contrast", contrast(label, background) >= 4.5f)
        }
    }

    @Test fun createButtonHasReadableDarkTextOnGold() {
        assertTrue(contrast(ListPalette.Background, ListPalette.Gold) >= 4.5f)
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
