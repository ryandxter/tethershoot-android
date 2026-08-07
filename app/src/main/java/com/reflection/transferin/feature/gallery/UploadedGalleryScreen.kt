package com.reflection.transferin.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.reflection.transferin.domain.model.UploadStatus
import com.reflection.transferin.ui.theme.AccentGold
import com.reflection.transferin.ui.theme.ErrorRedSoft
import com.reflection.transferin.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UploadedGalleryScreen(
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GalleryTopBar(
                isSelectionMode = uiState.isSelectionMode,
                selectedCount = uiState.selectedItems.size,
                onBack = {
                    if (uiState.isSelectionMode) viewModel.clearSelection() else onNavigateBack()
                },
                onSelectAll = { viewModel.selectAll() },
                onDelete = { viewModel.deleteSelectedItems() }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            FilterChipsRow(
                currentFilter = uiState.filterMode,
                onFilterSelected = { viewModel.setFilterMode(it) },
                itemCounts = mapOf(
                    FilterMode.ALL to uiState.uploadedItems.size,
                    FilterMode.PHOTOS to uiState.uploadedItems.count { !it.isVideo },
                    FilterMode.VIDEOS to uiState.uploadedItems.count { it.isVideo },
                    FilterMode.UPLOADED to uiState.uploadedItems.count { it.status == UploadStatus.SUCCESS },
                    FilterMode.FAILED to uiState.uploadedItems.count { it.status == UploadStatus.FAILED }
                )
            )

            GalleryStatsBar(
                totalItems = uiState.uploadedItems.size,
                currentSort = uiState.sortMode,
                onSortClick = { showSortMenu = true },
                showSortMenu = showSortMenu,
                onDismissSortMenu = { showSortMenu = false },
                onSortSelected = { viewModel.setSortMode(it) }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGold)
                    }
                }

                uiState.uploadedItems.isEmpty() -> {
                    EmptyGalleryState(filterMode = uiState.filterMode)
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.uploadedItems,
                            key = { it.id }
                        ) { item ->
                            GalleryGridItem(
                                item = item,
                                isSelected = item.id in uiState.selectedItems,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleItemSelection(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Tutup", color = Color.White)
                    }
                },
                containerColor = ErrorRedSoft
            ) {
                Text(error, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            if (isSelectionMode) {
                Text("$selectedCount dipilih", fontWeight = FontWeight.SemiBold)
            } else {
                Column {
                    Text("Galeri Upload", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        "File yang telah diupload",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                    contentDescription = if (isSelectionMode) "Batal" else "Kembali"
                )
            }
        },
        actions = {
            if (isSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, "Pilih Semua")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Hapus", tint = ErrorRedSoft)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun FilterChipsRow(
    currentFilter: FilterMode,
    onFilterSelected: (FilterMode) -> Unit,
    itemCounts: Map<FilterMode, Int>
) {
    val filters = listOf(
        FilterMode.ALL,
        FilterMode.PHOTOS,
        FilterMode.VIDEOS,
        FilterMode.UPLOADED,
        FilterMode.FAILED
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = filter == currentFilter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text("${getFilterDisplayName(filter)} (${itemCounts[filter] ?: 0})")
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentGold,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun GalleryStatsBar(
    totalItems: Int,
    currentSort: SortMode,
    onSortClick: () -> Unit,
    showSortMenu: Boolean,
    onDismissSortMenu: () -> Unit,
    onSortSelected: (SortMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$totalItems file",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Box {
            TextButton(onClick = onSortClick) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(getSortDisplayName(currentSort), style = MaterialTheme.typography.bodySmall)
            }

            DropdownMenu(expanded = showSortMenu, onDismissRequest = onDismissSortMenu) {
                SortMode.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(getSortDisplayName(sort)) },
                        onClick = {
                            onDismissSortMenu()
                            onSortSelected(sort)
                        },
                        leadingIcon = if (sort == currentSort) {
                            { Icon(Icons.Default.Check, null) }
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryGridItem(
    item: GalleryItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 0.95f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(item.filePath).takeIf { it.exists() })
                .crossfade(true)
                .build(),
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (item.status == UploadStatus.FAILED) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ErrorRedSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = "Gagal",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentGold else Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Terpilih",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                )
                .padding(6.dp)
        ) {
            Column {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.fileSizeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryState(filterMode: FilterMode) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = when (filterMode) {
                    FilterMode.PHOTOS -> Icons.Default.Image
                    FilterMode.VIDEOS -> Icons.Default.VideoLibrary
                    FilterMode.UPLOADED -> Icons.Default.CloudDone
                    FilterMode.FAILED -> Icons.Default.Error
                    else -> Icons.Default.PhotoLibrary
                },
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = TextSecondary.copy(alpha = 0.5f)
            )
            Text(
                text = when (filterMode) {
                    FilterMode.PHOTOS -> "Tidak ada foto"
                    FilterMode.VIDEOS -> "Tidak ada video"
                    FilterMode.UPLOADED -> "Belum ada file terupload"
                    FilterMode.FAILED -> "Tidak ada file gagal"
                    else -> "Galeri kosong"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

private fun getFilterDisplayName(filter: FilterMode): String = when (filter) {
    FilterMode.ALL -> "Semua"
    FilterMode.PHOTOS -> "Foto"
    FilterMode.VIDEOS -> "Video"
    FilterMode.UPLOADED -> "Berhasil"
    FilterMode.FAILED -> "Gagal"
}

private fun getSortDisplayName(sort: SortMode): String = when (sort) {
    SortMode.NEWEST_FIRST -> "Terbaru"
    SortMode.OLDEST_FIRST -> "Terlama"
    SortMode.NAME_ASC -> "Nama A-Z"
    SortMode.NAME_DESC -> "Nama Z-A"
    SortMode.SIZE_DESC -> "Ukuran"
}
