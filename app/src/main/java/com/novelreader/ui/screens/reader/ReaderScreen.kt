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
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator

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

    // Réinitialisé à chaque changement de chapitre (clé = URL courante)
    var scrollRestored by rememberSaveable(uiState.currentChapterUrl) { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val pos = listState.firstVisibleItemIndex * 10000 + listState.firstVisibleItemScrollOffset
        viewModel.saveScrollPosition(pos)
    }

    // Restaure la position de scroll sauvegardée à l'ouverture du chapitre
    LaunchedEffect(uiState.chapterContent, scrollRestored) {
        val content = uiState.chapterContent ?: return@LaunchedEffect
        if (!scrollRestored && !uiState.isLoading) {
            if (uiState.initialScrollPosition > 0) {
                val index = (uiState.initialScrollPosition / 10000).coerceIn(0, content.paragraphs.size + 1)
                val offset = uiState.initialScrollPosition % 10000
                listState.scrollToItem(index, offset)
            }
            scrollRestored = true
        }
    }

    val handleBack: () -> Unit = {
        viewModel.persistScrollPosition() // scope du ViewModel : survit à la sortie de l'écran
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(readerColors.background)) {
        when {
            uiState.isLoading -> LoadingIndicator(message = "Chargement…", modifier = Modifier.fillMaxSize().background(readerColors.background))
            uiState.error != null -> ErrorView(message = uiState.error ?: "Erreur", onRetry = { viewModel.loadChapter(uiState.currentChapterUrl) }, modifier = Modifier.background(readerColors.background))
            uiState.chapterContent != null -> {
                val content = uiState.chapterContent!!

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = uiState.settings.horizontalPaddingDp.dp, end = uiState.settings.horizontalPaddingDp.dp,
                        top = 16.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(uiState.settings.verticalParagraphSpacingDp.dp),
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = size.width
                            when {
                                offset.x < w * 0.25f && uiState.hasPrevChapter -> viewModel.goToPrevChapter()
                                offset.x > w * 0.75f && uiState.hasNextChapter -> viewModel.goToNextChapter()
                                else -> controlsVisible = if (controlsVisible == 1) 0 else 1
                            }
                        }
                    }
                ) {
                    item(key = "title") {
                        Text(content.chapterTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (uiState.settings.fontSizeSp + 6).sp,
                                lineHeight = (uiState.settings.fontSizeSp * uiState.settings.lineHeightMultiplier).sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = readerColors.text,
                            modifier = Modifier.padding(bottom = 16.dp))
                    }

                    itemsIndexed(content.paragraphs, key = { i, _ -> "p$i" }) { _, p ->
                        HtmlParagraph(p.htmlContent, uiState.settings.fontSizeSp, uiState.settings.lineHeightMultiplier, uiState.settings.fontFamily, readerColors.text)
                    }

                    item(key = "nav") {
                        Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = readerColors.text.copy(alpha = 0.15f))
                            Text("— Fin —", style = MaterialTheme.typography.bodyMedium, color = readerColors.text.copy(alpha = 0.4f))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (uiState.hasPrevChapter) {
                                    Button(onClick = viewModel::goToPrevChapter, colors = ButtonDefaults.buttonColors(containerColor = readerColors.accent), shape = RoundedCornerShape(14.dp)) {
                                        Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp)); Text("Précédent")
                                    }
                                }
                                if (uiState.hasNextChapter) {
                                    Button(onClick = viewModel::goToNextChapter, colors = ButtonDefaults.buttonColors(containerColor = readerColors.accent), shape = RoundedCornerShape(14.dp)) {
                                        Text("Suivant"); Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Overlay bar
                if (controlsVisible == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(readerColors.background.copy(alpha = 0.93f)).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = readerColors.text) }
                        Spacer(Modifier.weight(1f))
                        Text(content.chapterTitle, style = MaterialTheme.typography.titleSmall, color = readerColors.text, maxLines = 1, modifier = Modifier.weight(3f))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = viewModel::toggleSettings) { Icon(Icons.Default.FormatSize, "Réglages", tint = readerColors.text) }
                    }
                }
            }
        }

        if (uiState.showSettings) {
            ModalBottomSheet(
                onDismissRequest = viewModel::hideSettings,
                sheetState = sheetState,
                containerColor = readerColors.background.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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

@Composable
private fun HtmlParagraph(html: String, fontSizeSp: Int, lineHeightMultiplier: Float, fontFamily: ReaderFont, textColor: Color, modifier: Modifier = Modifier) {
    val fontAndroid = when (fontFamily) { ReaderFont.SERIF -> android.graphics.Typeface.SERIF; ReaderFont.SANS_SERIF -> android.graphics.Typeface.SANS_SERIF; ReaderFont.MONOSPACE -> android.graphics.Typeface.MONOSPACE; else -> null }
    val lineSpacingPx = with(LocalContext.current.resources.displayMetrics) { fontSizeSp * (lineHeightMultiplier - 1.0f) * density }
    AndroidView(
        factory = { ctx -> TextView(ctx).apply {
            setTextColor(textColor.toArgb()); setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat())
            setLineSpacing(lineSpacingPx, 1.0f); if (fontAndroid != null) setTypeface(fontAndroid); maxLines = Int.MAX_VALUE
        }},
        update = { tv -> tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT); tv.setTextColor(textColor.toArgb()); tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp.toFloat()); tv.setLineSpacing(lineSpacingPx, 1.0f); if (fontAndroid != null) tv.setTypeface(fontAndroid) },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ReaderSettingsPanel(settings: ReaderSettings, onFontSizeChange: (Int) -> Unit, onFontChange: (ReaderFont) -> Unit, onThemeChange: (ReaderTheme) -> Unit, onLineHeightChange: (Float) -> Unit, onPaddingChange: (Int) -> Unit, onPaginationToggle: () -> Unit, readerColors: ReaderColors) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = readerColors.text, modifier = Modifier.padding(bottom = 24.dp))
        Rs("Taille", settings.fontSizeSp.toFloat(), 12f..32f, 19, "${settings.fontSizeSp}sp", { onFontSizeChange(it.toInt()) }, readerColors)
        Spacer(Modifier.height(8.dp))
        Rs("Interligne", settings.lineHeightMultiplier, 1.2f..2.5f, 12, "%.1f".format(settings.lineHeightMultiplier), onLineHeightChange, readerColors)
        Spacer(Modifier.height(8.dp))
        Rs("Marges", settings.horizontalPaddingDp.toFloat(), 12f..40f, 13, "${settings.horizontalPaddingDp}dp", { onPaddingChange(it.toInt()) }, readerColors)
        Spacer(Modifier.height(20.dp))
        Text("Police", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = readerColors.text, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderFont.entries.forEach { f ->
                val sel = settings.fontFamily == f
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) readerColors.accent.copy(alpha = 0.2f) else Color.Transparent).clickable { onFontChange(f) }, contentAlignment = Alignment.Center) {
                    Text(f.displayName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) readerColors.accent else readerColors.text.copy(alpha = 0.6f)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Thème", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = readerColors.text, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { t ->
                val sel = settings.readerTheme == t
                val tc = ReaderColors.fromTheme(t)
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) tc.accent.copy(alpha = 0.2f) else tc.background.copy(alpha = 0.5f)).clickable { onThemeChange(t) }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(tc.background))
                        Text(t.displayName, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, color = if (sel) tc.accent else readerColors.text.copy(alpha = 0.6f)))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { onPaginationToggle() }, Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Pagination", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = readerColors.text)
            Text(if (settings.paginationMode) "Activée" else "Continue", style = MaterialTheme.typography.bodySmall, color = readerColors.text.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Rs(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, display: String, onChange: (Float) -> Unit, rc: ReaderColors) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(label, style = MaterialTheme.typography.bodySmall, color = rc.text.copy(alpha = 0.6f)); Text(display, style = MaterialTheme.typography.bodySmall, color = rc.text) }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, colors = SliderDefaults.colors(thumbColor = rc.accent, activeTrackColor = rc.accent, inactiveTrackColor = rc.text.copy(alpha = 0.1f)))
    }
}
