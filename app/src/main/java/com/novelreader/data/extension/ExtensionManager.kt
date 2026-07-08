package com.novelreader.data.extension

import com.novelreader.data.remote.source.NovelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire des sources/extensions.
 * Inspiré du gestionnaire d'extensions de Mihon.
 *
 * Pour le MVP, les sources sont compilées dans l'APK.
 * L'architecture prévoit le chargement dynamique (DexClassLoader)
 * pour les phases futures.
 *
 * @property sources Liste des sources disponibles (enabled + disabled)
 * @property enabledSources Liste des sources activées
 */
@Singleton
class ExtensionManager @Inject constructor() {

    private val _sources = MutableStateFlow<List<NovelSource>>(emptyList())
    val sources: StateFlow<List<NovelSource>> = _sources.asStateFlow()

    private val _enabledSourceIds = MutableStateFlow<Set<Long>>(emptySet())
    val enabledSourceIds: StateFlow<Set<Long>> = _enabledSourceIds.asStateFlow()

    val enabledSources: List<NovelSource>
        get() = _sources.value.filter { it.id in _enabledSourceIds.value }

    /**
     * Enregistre une source (appelé au démarrage par DI).
     */
    fun registerSource(source: NovelSource) {
        _sources.update { current ->
            val updated = current.toMutableList()
            val index = updated.indexOfFirst { it.id == source.id }
            if (index >= 0) updated[index] = source else updated.add(source)
            updated
        }
        _enabledSourceIds.update { it + source.id }
    }

    /**
     * Active ou désactive une source.
     */
    fun toggleSource(sourceId: Long, enabled: Boolean) {
        _enabledSourceIds.update {
            if (enabled) it + sourceId else it - sourceId
        }
    }

    /**
     * Récupère une source par son ID.
     */
    fun getSource(id: Long): NovelSource? = _sources.value.find { it.id == id }

    /**
     * Récupère une source par son nom.
     */
    fun getSourceByName(name: String): NovelSource? = _sources.value.find { it.name == name }

    /**
     * Liste des informations d'extensions pour l'UI.
     */
    fun getExtensionInfos(): List<ExtensionInfo> {
        return _sources.value.map { source ->
            ExtensionInfo(
                id = source.id,
                name = source.name,
                baseUrl = source.baseUrl,
                lang = source.lang,
                iconUrl = source.iconUrl,
                version = source.version,
                supportsLatest = source.supportsLatest,
                isInstalled = true,
                isEnabled = source.id in _enabledSourceIds.value
            )
        }
    }

    /**
     * Désinstalle une source (supprime de la liste).
     */
    fun uninstallSource(sourceId: Long) {
        _sources.update { it.filter { s -> s.id != sourceId } }
        _enabledSourceIds.update { it - sourceId }
    }
}
