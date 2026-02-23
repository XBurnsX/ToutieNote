package com.toutieserver.toutienote

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Tests UI des flows critiques de l'écran Notes.
 * Nécessite un serveur backend actif pour les tests complets.
 */
class NotesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andShowsNotesScreen() {
        composeTestRule.waitForIdle()
        // L'écran Notes affiche "NOTES" dans la barre ou le contenu principal
        composeTestRule.onNodeWithText("NOTES", useUnmergedTree = true).assertExists()
    }

    @Test
    fun notesScreen_hasContent() {
        composeTestRule.waitForIdle()
        // Vérifier qu'un élément de l'écran Notes est présent
        val hasContent = try {
            composeTestRule.onNodeWithText("Aucune note", useUnmergedTree = true).assertExists()
            true
        } catch (_: Exception) {
            try {
                composeTestRule.onNodeWithText("Créer une note", useUnmergedTree = true).assertExists()
                true
            } catch (_: Exception) {
                try {
                    composeTestRule.onNodeWithText("Rechercher", useUnmergedTree = true).assertExists()
                    true
                } catch (_: Exception) { false }
            }
        }
        assert(hasContent) { "Écran Notes doit afficher du contenu" }
    }
}
