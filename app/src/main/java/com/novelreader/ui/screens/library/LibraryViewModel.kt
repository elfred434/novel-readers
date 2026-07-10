package com.novelreader.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.dao.CategoryDao
import com.novelreader.data.local.entity.CategoryEntity
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val allNovels: List<NovelEntity> = emptyList(),
    val novels: List<NovelEntity> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.GRID,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val continueReading: ChapterEntity? = null,
    val showNewCategoryDialog: Boolean = false,
    val newCategoryName: String = "",
    val showNovelCategoryDialog: Boolean = false,
    val selectedNovelSlug: String = "",
    val selectedNovelTitle: String = "",
    val selectedNovelCategoryIds: Set<Long> = emptySet(),
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

    init {
        viewModelScope.launch {
            repository.getAllLibraryNovels().collect { novels ->
                _uiState.update { it.copy(allNovels = novels) }
                applyFilter()
            }
        }
        viewModelScope.launch {
            categoryDao.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
                applyFilter()
            }
        }
        viewModelScope.launch {
            repository.getRecentHistory().collect { history ->
                _uiState.update { it.copy(continueReading = history.firstOrNull()) }
            }
        }
    }

    private fun applyFilter() {
        val state = _uiState.value
        val catId = state.selectedCategoryId
        val all = state.allNovels

        if (catId == null) {
            _uiState.update { it.copy(novels = all, isLoading = false) }
        } else {
            viewModelScope.launch {
                try {
                    val novelsInCat = categoryDao.getNovelsInCategoryOnce(catId)
                    _uiState.update { it.copy(novels = novelsInCat, isLoading = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(novels = all, isLoading = false) }
                }
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID) }
    }

    fun removeFromLibrary(slug: String) {
        viewModelScope.launch { repository.removeNovelFromLibrary(slug) }
    }

    // ===== Catégories =====

    fun selectCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        applyFilter()
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

    fun showDeleteCategory(id: Long) { _uiState.update { it.copy(deleteCategoryId = id) } }
    fun hideDeleteCategory() { _uiState.update { it.copy(deleteCategoryId = null) } }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
            if (_uiState.value.selectedCategoryId == id) {
                _uiState.update { it.copy(selectedCategoryId = null, deleteCategoryId = null) }
                applyFilter()
            } else {
                _uiState.update { it.copy(deleteCategoryId = null) }
            }
        }
    }

    // ===== Assignation novel ↔ catégorie =====

    fun showNovelCategoryDialog(novel: NovelEntity) {
        viewModelScope.launch {
            val catIds = categoryDao.getCategoryIdsForNovel(novel.slug).toSet()
            _uiState.update {
                it.copy(
                    showNovelCategoryDialog = true,
                    selectedNovelSlug = novel.slug,
                    selectedNovelTitle = novel.title,
                    selectedNovelCategoryIds = catIds
                )
            }
        }
    }

    fun hideNovelCategoryDialog() {
        _uiState.update { it.copy(showNovelCategoryDialog = false) }
    }

    fun toggleNovelCategory(categoryId: Long) {
        val current = _uiState.value.selectedNovelCategoryIds
        _uiState.update {
            if (categoryId in current) {
                it.copy(selectedNovelCategoryIds = current - categoryId)
            } else {
                it.copy(selectedNovelCategoryIds = current + categoryId)
            }
        }
    }

    fun saveNovelCategories() {
        val slug = _uiState.value.selectedNovelSlug
        val catIds = _uiState.value.selectedNovelCategoryIds.toList()
        viewModelScope.launch {
            categoryDao.setCategoriesForNovel(slug, catIds)
            _uiState.update { it.copy(showNovelCategoryDialog = false) }
            applyFilter()
        }
    }
}
