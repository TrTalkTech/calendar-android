package com.example.calendar.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.R
import com.example.calendar.data.DocumentKinds
import com.example.calendar.data.OfficePage
import com.example.calendar.data.OfficePreview
import com.example.calendar.data.SpreadsheetPreview
import com.example.calendar.ui.theme.MacColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileViewerDialog(
    file: File,
    fileName: String,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MacColors.windowBackground,
        ) {
            FileViewerPage(
                file = file,
                fileName = fileName,
                onBack = onBack,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

@Composable
fun FileViewerPage(
    file: File,
    fileName: String,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MacColors.segmentBackground,
            ) {
                Text(
                    text = "‹",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MacColors.primaryLabel,
                )
            }
            Text(
                text = fileName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MacColors.primaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                onClick = onOpenExternal,
                shape = CircleShape,
                color = MacColors.segmentBackground,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.attachment_open_external),
                    tint = MacColors.primaryLabel,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp),
                )
            }
        }
        when {
            DocumentKinds.isPdf(fileName) -> PdfView(file, Modifier.weight(1f).fillMaxWidth())
            DocumentKinds.isSpreadsheet(fileName) -> SpreadsheetDocumentView(file, Modifier.weight(1f).fillMaxWidth())
            DocumentKinds.isOffice(fileName) -> OfficeDocumentView(file, Modifier.weight(1f).fillMaxWidth())
            DocumentKinds.isImage(fileName) -> ImageFileView(file, Modifier.weight(1f).fillMaxWidth())
            DocumentKinds.isText(fileName) -> TextFileView(file, Modifier.weight(1f).fillMaxWidth())
            else -> {
                Text(
                    text = stringResource(R.string.file_viewer_unsupported),
                    color = MacColors.secondaryLabel,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SpreadsheetDocumentView(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = file.absolutePath + ":" + file.length()
    var rows by remember(key) { mutableStateOf<List<List<String>>>(emptyList()) }
    var pages by remember(key) { mutableStateOf<List<OfficePage>>(emptyList()) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        loading = true
        error = null
        val parsed = withContext(Dispatchers.IO) {
            runCatching { SpreadsheetPreview.parse(file) }.getOrDefault(emptyList())
        }
        if (parsed.isNotEmpty()) {
            rows = parsed
            loading = false
            return@LaunchedEffect
        }
        val rendered = withContext(Dispatchers.IO) {
            runCatching { OfficePreview.render(context, file) }
        }
        loading = false
        rendered.onSuccess { pages = it.pages }
            .onFailure { error = it.message }
    }
    when {
        loading -> LoadingLabel(modifier)
        rows.isNotEmpty() -> SpreadsheetTable(rows, modifier)
        error != null || pages.isEmpty() -> {
            Text(
                text = stringResource(R.string.file_viewer_office_failed),
                color = MacColors.secondaryLabel,
                modifier = modifier.padding(20.dp),
            )
        }
        else -> OfficeWebView(pages, modifier)
    }
}

@Composable
private fun SpreadsheetTable(rows: List<List<String>>, modifier: Modifier = Modifier) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Column(
        modifier = modifier
            .horizontalScroll(horizontal)
            .verticalScroll(vertical)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = MacColors.separator)
            }
            Row {
                row.forEach { cell ->
                    Text(
                        text = cell.ifBlank { " " },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) MacColors.primaryLabel else MacColors.secondaryLabel,
                        modifier = Modifier
                            .widthIn(min = 88.dp, max = 220.dp)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficeDocumentView(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = file.absolutePath + ":" + file.length()
    var pages by remember(key) { mutableStateOf<List<OfficePage>>(emptyList()) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { OfficePreview.render(context, file) }
        }
        loading = false
        result.onSuccess { pages = it.pages }
            .onFailure { error = it.message }
    }
    when {
        loading -> LoadingLabel(modifier)
        error != null || pages.isEmpty() -> {
            Text(
                text = stringResource(R.string.file_viewer_office_failed),
                color = MacColors.secondaryLabel,
                modifier = modifier.padding(20.dp),
            )
        }
        else -> OfficeWebView(pages, modifier)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficeWebView(pages: List<OfficePage>, modifier: Modifier = Modifier) {
    if (pages.isEmpty()) return
    var index by remember(pages) { mutableIntStateOf(0) }
    val page = pages[index.coerceIn(0, pages.lastIndex)]
    Column(modifier) {
        if (pages.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (index > 0) index -= 1 },
                    enabled = index > 0,
                ) {
                    Text(stringResource(R.string.file_viewer_prev), color = MacColors.accentBlue)
                }
                Text(
                    text = stringResource(R.string.file_viewer_page, index + 1, pages.size),
                    modifier = Modifier.weight(1f),
                    color = MacColors.secondaryLabel,
                )
                TextButton(
                    onClick = { if (index < pages.lastIndex) index += 1 },
                    enabled = index < pages.lastIndex,
                ) {
                    Text(stringResource(R.string.file_viewer_next), color = MacColors.accentBlue)
                }
            }
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url ?: return true
                            if (url.scheme == "file" || url.scheme == "about" || url.scheme == "data") return false
                            if (url.scheme == "http" || url.scheme == "https") {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                            }
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                val url = File(page.path).toURI().toString()
                if (webView.tag != url) {
                    webView.tag = url
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun PdfView(file: File, modifier: Modifier = Modifier) {
    val key = file.absolutePath + ":" + file.length()
    var pages by remember(key) {
        mutableStateOf(if (PdfPageCache.key == key) PdfPageCache.pages else emptyList())
    }
    LaunchedEffect(key) {
        if (PdfPageCache.key == key && PdfPageCache.pages.isNotEmpty()) {
            pages = PdfPageCache.pages
            return@LaunchedEffect
        }
        val rendered = mutableListOf<Bitmap>()
        withContext(Dispatchers.IO) {
            renderPdf(file) { bitmap ->
                rendered += bitmap
                val snapshot = rendered.toList()
                withContext(Dispatchers.Main) {
                    PdfPageCache.key = key
                    PdfPageCache.pages = snapshot
                    pages = snapshot
                }
            }
        }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        if (pages.isEmpty()) {
            Text(
                text = stringResource(R.string.file_viewer_opening),
                color = MacColors.secondaryLabel,
                modifier = Modifier.padding(20.dp),
            )
        }
        pages.forEach { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
private fun ImageFileView(file: File, modifier: Modifier = Modifier) {
    val bitmap = remember(file.absolutePath, file.length()) {
        BitmapFactory.decodeFile(file.absolutePath)
    }
    if (bitmap == null) {
        Text(
            text = stringResource(R.string.file_viewer_office_failed),
            color = MacColors.secondaryLabel,
            modifier = modifier.padding(20.dp),
        )
        return
    }
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
private fun TextFileView(file: File, modifier: Modifier = Modifier) {
    val text = remember(file.absolutePath, file.length()) {
        runCatching { file.readText().take(200_000) }.getOrDefault("")
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = text.ifBlank { stringResource(R.string.file_viewer_office_failed) },
            color = MacColors.primaryLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoadingLabel(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MacColors.accentBlue)
            Text(
                text = stringResource(R.string.file_viewer_opening),
                color = MacColors.secondaryLabel,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private object PdfPageCache {
    var key: String? = null
    var pages: List<Bitmap> = emptyList()
}

private suspend fun renderPdf(file: File, onPage: suspend (Bitmap) -> Unit) {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        val count = minOf(renderer.pageCount, 30)
        for (index in 0 until count) {
            val bitmap = renderer.openPage(index).use { page ->
                val width = 1080
                val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
            onPage(bitmap)
        }
    }
}
