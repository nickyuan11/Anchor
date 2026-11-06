package com.nickyuan.anchor.ui.snapshots

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickyuan.anchor.util.ShareUtils

/** 页面快照列表：查看、分享、删除已抓取的快照 */
@Composable
fun SnapshotsScreen(onBack: () -> Unit, viewModel: SnapshotsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()

    SnapshotsContent(
        items = items,
        onBack = onBack,
        onShare = { ShareUtils.shareFile(context, it.entry.file, "application/zip", "分享快照") },
        onDelete = viewModel::delete,
        loadThumbnail = viewModel::loadThumbnail,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotsContent(
    items: List<SnapshotItem>,
    onBack: () -> Unit,
    onShare: (SnapshotItem) -> Unit,
    onDelete: (SnapshotItem) -> Unit,
    loadThumbnail: suspend (SnapshotItem) -> Bitmap?,
) {
    var pendingDelete by remember { mutableStateOf<SnapshotItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("页面快照") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有快照。\n在主页打开「显示快照按钮」，遇到没跳过的广告时点一下 📷。",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.entry.file.name }) { item ->
                    SnapshotRow(
                        item = item,
                        onShare = { onShare(item) },
                        onDelete = { pendingDelete = item },
                        loadThumbnail = loadThumbnail,
                    )
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个快照？") },
            text = { Text("${item.appName} · ${item.time}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(item)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SnapshotRow(
    item: SnapshotItem,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    loadThumbnail: suspend (SnapshotItem) -> Bitmap?,
) {
    // produceState：在后台异步加载缩略图，加载好之前显示占位色块
    val thumbnail by produceState<Bitmap?>(initialValue = null, item) { value = loadThumbnail(item) }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbModifier = Modifier
                .size(width = 54.dp, height = 96.dp)
                .clip(RoundedCornerShape(6.dp))
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "截图",
                    contentScale = ContentScale.Crop,
                    modifier = thumbModifier,
                )
            } else {
                Box(thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.appName, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text("${item.time} · ${item.size}", style = MaterialTheme.typography.bodySmall)
                Text(
                    item.entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onShare) { Text("分享") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}
