package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class AppNavigationTest {
    @Test fun extensionDetailReturnsToListThenHomeBeforeExit() {
        val nav = AppNavigation()
        nav.navigateTo("Extensions")
        nav.navigateTo("Extensions", "ASH")
        assertTrue(nav.back())
        assertEquals(AppDestination("Extensions"), nav.current)
        assertTrue(nav.back())
        assertEquals(AppDestination(), nav.current)
        assertFalse(nav.back())
    }
    @Test fun settingsAndManualAddReturnToTheirActualOrigin() {
        val nav = AppNavigation()
        nav.navigateTo("Collection")
        nav.navigateTo("Ajouter")
        nav.back()
        assertEquals("Collection", nav.current.section)
        nav.navigateTo("Paramètres")
        nav.navigateTo("À propos")
        nav.back()
        assertEquals("Paramètres", nav.current.section)
        nav.back()
        assertEquals("Collection", nav.current.section)
    }
    @Test fun searchResultsAreRestoredAfterOpeningAnExtension() {
        val nav = AppNavigation()
        nav.navigateTo("Recherche", catalogSearch = "cad")
        nav.updateCatalogSearch("Cad Bane")
        nav.navigateTo("Recherche")
        assertEquals("Cad Bane", nav.current.catalogSearch)
        nav.navigateTo("Extensions", "ASH", catalogSearch = "011")
        nav.back()
        assertEquals(AppDestination("Recherche", catalogSearch = "Cad Bane"), nav.current)
        nav.back()
        assertEquals("Accueil", nav.current.section)
    }
    @Test fun explicitHomeClearsHistoryAndRepeatedNavigationDoesNotTrapBack() {
        val nav = AppNavigation()
        nav.navigateTo("Scanner")
        nav.navigateTo("Scanner")
        nav.back()
        assertFalse(nav.canGoBack)
        nav.navigateTo("Collection")
        nav.navigateTo("Ajouter")
        nav.navigateTo("Accueil")
        assertFalse(nav.back())
    }
    @Test fun extensionsUpLinkDoesNotReturnToTheSameDetail() {
        val nav = AppNavigation()
        nav.navigateTo("Extensions")
        nav.navigateTo("Extensions", "HMW")
        nav.upToExtensions()
        nav.back()
        assertEquals("Accueil", nav.current.section)
        nav.navigateTo("Statistiques")
        nav.navigateTo("Extensions", "ASH")
        nav.upToExtensions()
        assertNull(nav.current.setCode)
        nav.back()
        assertEquals("Statistiques", nav.current.section)
    }
    @Test fun listsUpLinkDoesNotCreateAnAlternatingDetailListLoop() {
        val nav = AppNavigation()
        nav.navigateTo("Mes listes")
        nav.navigateTo("Mes listes", "deck-1")
        nav.upToLists()
        assertEquals(AppDestination("Mes listes"), nav.current)
        nav.back()
        assertEquals(AppDestination(), nav.current)
    }
    @Test fun completingAnExtensionKeepsItsContextAndReturnsToTheFilteredCatalogue() {
        val nav = AppNavigation()
        nav.navigateTo("Extensions")
        nav.navigateTo("Extensions", "ASH", "Cad Bane")
        nav.navigateTo("Compléter mon set", "ASH")
        assertEquals("ASH", nav.current.setCode)
        assertEquals("", nav.current.catalogSearch)
        nav.back()
        assertEquals(AppDestination("Extensions", "ASH", "Cad Bane"), nav.current)
        nav.back()
        assertEquals(AppDestination("Extensions"), nav.current)
    }
    @Test fun settingsSubpagesReturnToSettingsThenTheOriginalScreen() {
        listOf("Apparence", "Mises à jour", "Sauvegardes").forEach { destination ->
            val nav = AppNavigation()
            nav.navigateTo("Mes listes", "deck-1")
            nav.navigateTo("Paramètres")
            nav.navigateTo(destination)
            assertNull(nav.current.setCode)
            nav.back()
            assertEquals(AppDestination("Paramètres"), nav.current)
            nav.back()
            assertEquals(AppDestination("Mes listes", "deck-1"), nav.current)
        }
    }
}

