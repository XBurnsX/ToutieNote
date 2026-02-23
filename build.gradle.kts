// Configuration racine : uniquement déclaration des plugins (apply false).
// La configuration Android et les dépendances sont dans le module :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
