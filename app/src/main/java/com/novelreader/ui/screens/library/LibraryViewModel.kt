package com.novelreader.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.dao.CategoryDao
import com.novelreader.data.local.entity.CategoryEntity
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val novels: List<NovelEntity> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val continueReading: ChapterEntity? = null,
    val showNewCategoryDialog: Boolean = false,
    val newCategoryName: String = "",
    val deleteCategoryId: Long? = null
)

enum class ViewMode { GRID, LIST }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var novelsFlow: Flow<List<NovelEntity>> = repository.getAllLibraryNovels()

    init {
        // Observe all novels by default
        viewModelScope.launch {
            novelsFlow.collect { novels ->
                _uiState.update { it.copy(novels = novels, isLoading = false) }
            }
        }
        viewModelScope.launch {
            categoryDao.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
        viewModelScope.launch {
            repository.getRecentHistory().collect { history ->
                _uiState.update { it.copy(continueReading = history.firstOrNull()) }
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }
    }

    fun removeFromLibrary(slug: String) {
        viewModelScope.launch { repository.removeNovelFromLibrary(slug) }
    }

    // === Catégories ===

    fun selectCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        // Switch the observed flow based on selected category
        viewModelScope.launch {
            if (categoryId == null) {
                novelsFlow = repository.getAllLibraryNovels()
            } else {
                novelsFlow = categoryDao.getNovelsInCategory(categoryId)
            }
            // Re-collect
            novelsFlow.collect { novels ->
                _uiState.update { it.copy(novels = novels, isLoading = false) }
            }
        }
    }

    fun showNewCategoryDialog() { _uiState.update { it.copy(showNewCategoryDialog = true, newCategoryName = "") } }
    fun hideNewCategoryDialog() { _uiState.update { it.copy(showNewCategoryDialog = false) } }
    fun onNewCategoryNameChange(name: String) { _uiState.update { it.copy(newCategoryName = name) } }

    fun createCategory() {
        val name = _uiState.value.newCategoryName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryDao.insertCategory(CategoryEntity(name = name))
            _uiState.update { it.copy(showNewCategoryDialog = false, selectedCategoryId = null) }
        }
    }

    fun confirmDeleteCategory() { _uiState.update { it.copy(deleteCategoryId = null) } }
    fun showDeleteCategory(id: Long) { _uiState.update { it.copy(deleteCategoryId = id) } }
    fun hideDeleteCategory() { _uiState.update { it.copy(deleteCategoryId = null) } }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
            if (_uiState.value.selectedCategoryId == id) {
                _uiState.update { it.copy(selectedCategoryId = null, deleteCategoryId = null) }
            } else {
                _uiState.update { it.copy(deleteCategoryId = null) }
            }
        }
    }

    fun assignNovelToCategory(novelSlug: String, categoryId: Long) {
        viewModelScope.launch {
            categoryDao.addNovelToCategory(
                com.novelreader.data.local.entity.NovelCategoryCrossRef(
                    novelSlug = novelSlug,
                    categoryId = categoryId
                )
            )
        }
    }

    fun removeNovelFromCategory(novelSlug: String, categoryId: Long) {
        viewModelScope.launch {
            categoryDao.removeNovelFromCategory(novelSlug, categoryId)
        }
    }
}
