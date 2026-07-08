package com.novelreader.data.extension

/**
 * Métadonnées d'une source/extension.
 * Permet d'afficher la liste des sources disponibles et leur état.
 */
data class ExtensionInfo(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val lang: String = "fr",
    val iconUrl: String? = null,
    val version: Int = 1,
    val supportsLatest: Boolean = true,
    val isInstalled: Boolean = true,
    val isEnabled: Boolean = true
)
