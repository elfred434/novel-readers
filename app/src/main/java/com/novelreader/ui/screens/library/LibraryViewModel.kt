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
    val newCategoryName: String = ""
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

    fun selectCategory(categoryId: Long?) { _uiState.update { it.copy(selectedCategoryId = categoryId) } }

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

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            categoryDao.deleteCategoryById(id)
            if (_uiState.value.selectedCategoryId == id) _uiState.update { it.copy(selectedCategoryId = null) }
        }
    }

    val hasUnread: (NovelEntity) -> Boolean = { it.unreadChapterCount > 0 }
    val unreadCount: (NovelEntity) -> Int = { it.unreadChapterCount }
}
