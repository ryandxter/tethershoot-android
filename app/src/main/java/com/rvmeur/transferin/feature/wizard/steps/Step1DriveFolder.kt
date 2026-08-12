package com.rvmeur.transferin.feature.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.domain.session.RemoteFolder
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.AccentGreenSubtle
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.DMSans
import com.rvmeur.transferin.ui.theme.LightBorder
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.SurfaceVariant
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

/**
 * Step 1: Google Drive Auth & Target Folder Card Grid Browser
 */
@Composable
fun Step1DriveFolder(
    isAuthenticated: Boolean,
    onSignInClick: () -> Unit,
    currentPath: String,
    folders: List<RemoteFolder>,
    selectedFolder: RemoteFolder?,
    onFolderSelect: (RemoteFolder) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card A: OAuth Status Card
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "otentikasi google drive".toBrandLowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isAuthenticated) AccentGreenSubtle else AccentBurgundy.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAuthenticated) "connected" else "sign in required",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMono,
                                color = if (isAuthenticated) AccentGreen else AccentBurgundy
                            )
                        )
                    }
                }
            }

            if (!isAuthenticated) {
                Spacer(modifier = Modifier.height(16.dp))
                KineticButton(
                    text = "masuk akun google drive",
                    onClick = onSignInClick,
                    containerColor = TextPrimary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            }
        }

        // Card B: Target Folder Card Grid Browser
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "pilih folder target drive".toBrandLowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = (if (currentPath.isEmpty()) "/root" else currentPath).toBrandLowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMono,
                            color = TextMutedLight
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentPath.isNotEmpty() && currentPath != "/root") {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceVariant, CircleShape)
                                .clickable { onNavigateUp() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Naik 1 folder",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceVariant, CircleShape)
                            .clickable { showCreateDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Buat folder baru",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "tidak ada direktori ditemukan. buat folder baru.".toBrandLowercase(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = DMSans,
                            color = TextMuted
                        )
                    )
                }
            } else {
                // 2-Column Card Grid Layout for Directories
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(folders, key = { it.fullPath }) { folder ->
                        val isSelected = selectedFolder?.fullPath == folder.fullPath

                        DoppelrandCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .semantics {
                                    role = Role.RadioButton
                                    contentDescription = "Folder ${folder.name.toBrandLowercase()}"
                                }
                                .clickable(
                                    onClickLabel = "pilih folder ${folder.name.toBrandLowercase()}"
                                ) { onFolderSelect(folder) },
                            outerShape = RoundedCornerShape(16.dp),
                            innerShape = RoundedCornerShape(12.dp),
                            outerColor = if (isSelected) AccentGreenSubtle else SurfaceVariant,
                            innerColor = CardSurface,
                            borderColor = if (isSelected) AccentGreen else LightBorder,
                            borderWidth = if (isSelected) 2.dp else 1.dp,
                            gapPadding = 4.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) AccentGreen else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = folder.name.toBrandLowercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = DMSans,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Folder AlertDialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "buat folder baru".toBrandLowercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = CormorantGaramond)
                )
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("nama folder".toBrandLowercase()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName)
                            newFolderName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("buat".toBrandLowercase(), color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("batal".toBrandLowercase(), color = TextMuted)
                }
            },
            containerColor = CardSurface
        )
    }
}
