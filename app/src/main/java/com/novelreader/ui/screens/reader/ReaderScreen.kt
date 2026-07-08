package com.novelreader.ui.screens.reader

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import kotlinx.coroutines.launch

/**
 * Écran de lecture — plein écran avec contrôles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val readerColors = ReaderColors.fromTheme(uiState.settings.readerTheme)
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var controlsVisible by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    // Sauvegarder la position de scroll à chaque défilement
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val scrollPosition = listState.firstVisibleItemIndex * 10000 +
            listState.firstVisibleItemScrollOffset
        viewModel.saveScrollPosition(scrollPosition)
    }

    // Reset scroll au début d'un nouveau chapitre
    LaunchedEffect(uiState.currentChapterUrl) {
        if (!uiState.isLoading && uiState.chapterContent != null) {
            listState.scrollToItem(0)
        }
    }

    // Handler back avec persistance du scroll
    val handleBack: () -> Unit = {
        scope.launch {
            viewModel.persistScrollPosition()
        }
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerColors.background)
    ) {
        when {
            uiState.isLoading -> {
                LoadingIndicator(
                    message = "Chargement du chapitre…",
                    modifier = Modifier
                        .background(readerColors.background)
                        .fillMaxSize()
                )
            }

            uiState.error != null -> {
                ErrorView(
                    message = uiState.error ?: "Erreur",
                    onRetry = { viewModel.loadChapter(uiState.currentChapterUrl) },
                    modifier = Modifier.background(readerColors.background)
                )
            }

            uiState.chapterContent != null -> {
                val content = uiState.chapterContent!!

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = uiState.settings.horizontalPaddingDp.dp,
                        end = uiState.settings.horizontalPaddingDp.dp,
                        top = 16.dp,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        uiState.settings.verticalParagraphSpacingDp.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val width = size.width
                                when {
                                    offset.x < width * 0.25f && uiState.hasPrevChapter ->
                                        viewModel.goToPrevChapter()
                                    offset.x > width * 0.75f && uiState.hasNextChapter ->
                                        viewModel.goToNextChapter()
                                    else ->
                                        controlsVisible = if (controlsVisible == 1) 0 else 1
                                }
                            }
                        }
                ) {
                    item(key = "chapter_title") {
                        Text(
                            text = content.chapterTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (uiState.settings.fontSizeSp + 4).sp,
                                lineHeight = (uiState.settings.fontSizeSp * uiState.settings.lineHeightMultiplier).sp
                            ),
                            color = readerColors.text,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    itemsIndexed(
                        items = content.paragraphs,
                        key = { index, _ -> "para_$index" }
                    ) { _, paragraph ->
                        HtmlParagraph(
                            html = paragraph.htmlContent,
                            fontSizeSp = uiState.settings.fontSizeSp,
                            lineHeightMultiplier = uiState.settings.lineHeightMultiplier,
                            fontFamily = uiState.settings.fontFamily,
                            textColor = readerColors.text
                        )
                    }

                    item(key = "chapter_nav") {
                        ChapterEndNav(
                            hasPrev = uiState.hasPrevChapter,
                            hasNext = uiState.hasNextChapter,
                            onPrev = viewModel::goToPrevChapter,
                            onNext = viewModel::goToNextChapter,
                            readerColors = readerColors,
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                }

                if (controlsVisible == 1) {
                    Column {
                        ReaderTopBar(
                            title = content.chapterTitle,
                            readerColors = readerColors,
                            onBack = handleBack,
                            onSettings = viewModel::toggleSettings
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (uiState.showSettings) {
            ModalBottomSheet(
                onDismissRequest = viewModel::hideSettings,
                sheetState = sheetState,
                containerColor = readerColors.background.copy(alpha = 0.98f)
            ) {
                ReaderSettingsPanel(
                    settings = uiState.settings,
                    onFontSizeChange = viewModel::updateFontSize,
                    onFontChange = viewModel::updateFont,
                    onThemeChange = viewModel::updateTheme,
                    onLineHeightChange = viewModel::updateLineHeight,
                    onPaddingChange = viewModel::updatePadding,
                    onPaginationToggle = viewModel::togglePaginationMode,
                    readerColors = readerColors
                )
            }
        }
    }
}

// =============================================================================
// Sous-composants
// =============================================================================

@Composable
private fun ReaderTopBar(
    title: String,
    readerColors: ReaderColors,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(readerColors.background.copy(alpha = 0.95f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = readerColors.text
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = readerColors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Default.FormatSize,
                contentDescription = "Réglages",
                tint = readerColors.text
            )
        }
    }
}

@Composable
private fun HtmlParagraph(
    html: String,
    fontSizeSp: Int,
    lineHeightMultiplier: Float,
    fontFamily: ReaderFont,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val fontFamilyAndroid = when (fontFamily) {
        ReaderFont.DEFAULT -> null
        ReaderFont.SERIF -> android.graphics.Typeface.SERIF
        ReaderFont.SANS_SERIF -> android.graphics.Typeface.SANS_SERIF
        ReaderFont.MONOSPACE -> android.graphics.Typeface.MONOSPACE
    }
    val lineSpacingExtraPx = with(LocalContext.current.resources.displayMetrics) {
        fontSizeSp * (lineHeightMultiplier - 1.0f) * density
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
                setLineSpacing(lineSpacingExtraPx, 1.0f)
                if (fontFamilyAndroid != null) setTypeface(fontFamilyAndroid)
                maxLines = Int.MAX_VALUE
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
            textView.setLineSpacing(lineSpacingExtraPx, 1.0f)
            if (fontFamilyAndroid != null) textView.setTypeface(fontFamilyAndroid)
            textView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ChapterEndNav(
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    readerColors: ReaderColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = readerColors.text.copy(alpha = 0.2f))
        Text(
            text = "— Fin du chapitre —",
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.text.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPrev) {
                Button(
                    onClick = onPrev,
                    colors = ButtonDefaults.buttonColors(containerColor = readerColors.accent)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Précédent")
                }
            }
            if (hasNext) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = readerColors.accent)
                ) {
                    Text("Suivant")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onFontSizeChange: (Int) -> Unit,
    onFontChange: (ReaderFont) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onPaddingChange: (Int) -> Unit,
    onPaginationToggle: () -> Unit = {},
    readerColors: ReaderColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("Réglages du lecteur", style = MaterialTheme.typography.headlineSmall, color = readerColors.text, modifier = Modifier.padding(bottom = 20.dp))
        SettingsSlider("Taille de police", settings.fontSizeSp.toFloat(), 12f..32f, 19, "${settings.fontSizeSp}sp", { onFontSizeChange(it.toInt()) }, readerColors)
        Spacer(Modifier.height(12.dp))
        SettingsSlider("Interligne", settings.lineHeightMultiplier, 1.2f..2.5f, 12, "%.1f".format(settings.lineHeightMultiplier), onLineHeightChange, readerColors)
        Spacer(Modifier.height(12.dp))
        SettingsSlider("Marges", settings.horizontalPaddingDp.toFloat(), 12f..40f, 13, "${settings.horizontalPaddingDp}dp", { onPaddingChange(it.toInt()) }, readerColors)
        Spacer(Modifier.height(16.dp))
        Text("Police", style = MaterialTheme.typography.titleSmall, color = readerColors.text, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderFont.entries.forEach { font ->
                TextButton(
                    onClick = { onFontChange(font) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (settings.fontFamily == font) readerColors.accent else readerColors.text.copy(alpha = 0.6f)
                    )
                ) { Text(font.displayName, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Thème", style = MaterialTheme.typography.titleSmall, color = readerColors.text, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val tc = ReaderColors.fromTheme(theme)
                TextButton(
                    onClick = { onThemeChange(theme) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (settings.readerTheme == theme) readerColors.accent else readerColors.text.copy(alpha = 0.6f)
                    )
                ) {
                    Box(Modifier.size(16.dp).background(tc.background, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(theme.displayName, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clickable { onPaginationToggle() },
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("Mode pagination", style = MaterialTheme.typography.titleSmall, color = readerColors.text)
            Text(
                text = if (settings.paginationMode) "Activé (page par page)" else "Défilement continu",
                style = MaterialTheme.typography.bodySmall,
                color = readerColors.text.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSlider(
    label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int,
    displayValue: String, onValueChange: (Float) -> Unit, readerColors: ReaderColors
) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = readerColors.text)
            Text(displayValue, style = MaterialTheme.typography.bodySmall, color = readerColors.text.copy(alpha = 0.6f))
        }
        Slider(
            value = value, onValueChange = onValueChange,
            valueRange = valueRange, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = readerColors.accent,
                activeTrackColor = readerColors.accent,
                inactiveTrackColor = readerColors.text.copy(alpha = 0.2f)
            )
        )
    }
}
