package com.novelreader.data.extension

import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.remote.source.NovelSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gestionnaire des sources/extensions.
 * Inspiré du gestionnaire d'extensions de Mihon.
 *
 * Pour le MVP, les sources sont compilées dans l'APK.
 * L'architecture prévoit le chargement dynamique (DexClassLoader)
 * pour les phases futures.
 *
 * Les sources désactivées sont PERSISTÉES (DataStore) et prises en compte
 * par le repository : toute opération réseau sur une source désactivée
 * lève une SourceDisabledException.
 *
 * Note : construit manuellement par le module Hilt (AppModule) — pas de
 * `@Inject constructor` pour éviter la création d'une seconde instance vide.
 *
 * @property sources Liste des sources disponibles (enabled + disabled)
 */
class ExtensionManager(
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sources = MutableStateFlow<List<NovelSource>>(emptyList())
    val sources: StateFlow<List<NovelSource>> = _sources.asStateFlow()

    private val _disabledSourceIds = MutableStateFlow<Set<Long>>(emptySet())
    val disabledSourceIds: StateFlow<Set<Long>> = _disabledSourceIds.asStateFlow()

    init {
        // Restaure les sources désactivées persistées
        scope.launch {
            prefs.disabledSourceIds.collect { ids ->
                _disabledSourceIds.value = ids
            }
        }
    }

    /** Sources actuellement activées. */
    val enabledSources: List<NovelSource>
        get() = _sources.value.filter { isSourceEnabled(it.id) }

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
    }

    /**
     * Active ou désactive une source (persisté en DataStore).
     */
    fun toggleSource(sourceId: Long, enabled: Boolean) {
        scope.launch { prefs.setSourceDisabled(sourceId, !enabled) }
    }

    /** Vrai si la source est activée (par défaut : oui). */
    fun isSourceEnabled(id: Long): Boolean = id !in _disabledSourceIds.value

    /**
     * Récupère une source par son ID.
     */
    fun getSource(id: Long): NovelSource? = _sources.value.find { it.id == id }

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
                isEnabled = isSourceEnabled(source.id)
            )
        }
    }
}
