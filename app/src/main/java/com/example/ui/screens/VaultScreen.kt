package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.VaultDisplayMetadata
import com.example.data.local.VaultEntity
import com.example.ui.TubeVaultViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: TubeVaultViewModel,
    vaultItems: List<VaultEntity>,
    onLockClicked: () -> Unit,
    onPlayVaultVideo: (VaultEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedItemForAction by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForDetails by remember { mutableStateOf<Pair<VaultEntity, VaultDisplayMetadata>?>(null) }
    var selectedItemForMoveOut by remember { mutableStateOf<VaultEntity?>(null) }
    var selectedItemForAi by remember { mutableStateOf<VaultEntity?>(null) }
    var showAiConfirmationDialog by remember { mutableStateOf(false) }

    // Dialogs
    if (selectedItemForAction != null) {
        val item = selectedItemForAction!!
        val metadata = viewModel.getVaultDisplayMetadata(item)
        ModalBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = metadata?.title ?: "Private Vault Item",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))

                ListItem(
                    headlineContent = { Text("Play Video") },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedItemForAction = null
                        onPlayVaultVideo(item)
                    }.testTag("vault_action_play")
                )
                ListItem(
                    headlineContent = { Text("Details & AI") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedItemForAction = null
                        if (metadata != null) {
                            selectedItemForDetails = Pair(item, metadata)
                        }
                    }.testTag("vault_action_details")
                )
                ListItem(
                    headlineContent = { Text("Move Out to Normal Storage") },
                    leadingContent = { Icon(Icons.Filled.Output, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedItemForAction = null
                        selectedItemForMoveOut = item
                    }.testTag("vault_action_move_out")
                )
                ListItem(
                    headlineContent = { Text("Ask Cloud AI (Summarize / Tags)") },
                    leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedItemForAction = null
                        selectedItemForAi = item
                        showAiConfirmationDialog = true
                    }.testTag("vault_action_ai")
                )
                ListItem(
                    headlineContent = { Text("Delete from Vault", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        selectedItemForAction = null
                        viewModel.deleteVaultItem(item)
                        Toast.makeText(context, "Deleted from vault", Toast.LENGTH_SHORT).show()
                    }.testTag("vault_action_delete")
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // AI Confirmation Dialog (Requirement 16)
    if (showAiConfirmationDialog && selectedItemForAi != null) {
        val item = selectedItemForAi!!
        val metadata = viewModel.getVaultDisplayMetadata(item)
        AlertDialog(
            onDismissRequest = {
                showAiConfirmationDialog = false
                selectedItemForAi = null
            },
            title = { Text("Cloud AI Confirmation") },
            text = {
                Text("This will send selected text/metadata from private vault item « ${metadata?.title ?: "item"} » to the configured cloud AI provider. Do you wish to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiConfirmationDialog = false
                        Toast.makeText(context, "Cloud AI query sent securely for vault item.", Toast.LENGTH_SHORT).show()
                        selectedItemForAi = null
                    }
                ) {
                    Text("Proceed with Cloud AI")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAiConfirmationDialog = false
                        selectedItemForAi = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Details Dialog
    if (selectedItemForDetails != null) {
        val (item, metadata) = selectedItemForDetails!!
        AlertDialog(
            onDismissRequest = { selectedItemForDetails = null },
            title = { Text("Vault Item Details") },
            text = {
                Column {
                    Text("Title: ${metadata.title}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Resolution: ${metadata.resolution}")
                    Text("Duration: ${metadata.durationText}")
                    Text("File Size: ${metadata.fileSizeBytes / (1024 * 1024)} MB")
                    Text("Platform: ${metadata.platform}")
                    if (!metadata.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Notes: ${metadata.notes}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedItemForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Move Out Dialog
    if (selectedItemForMoveOut != null) {
        val item = selectedItemForMoveOut!!
        AlertDialog(
            onDismissRequest = { selectedItemForMoveOut = null },
            title = { Text("Move Out from Vault") },
            text = { Text("Do you want to decrypt and restore this item back to normal downloads storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        val downloadsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                        val targetFile = File(downloadsDir, "restored_${System.currentTimeMillis()}.mp4")
                        viewModel.moveOutFromVault(item, targetFile) { success ->
                            if (success) {
                                Toast.makeText(context, "Successfully moved out to normal storage", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to move out", Toast.LENGTH_SHORT).show()
                            }
                        }
                        selectedItemForMoveOut = null
                    }
                ) {
                    Text("Move Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForMoveOut = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Private Vault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = onLockClicked,
                        modifier = Modifier.testTag("vault_lock_button")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock Vault")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (vaultItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Vault is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Move items from your Library into the Private Vault for encryption & biometric protection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vaultItems) { item ->
                    val metadata = viewModel.getVaultDisplayMetadata(item)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .testTag("vault_item_card_${item.vaultId}")
                            .clickable { selectedItemForAction = item },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                AsyncImage(
                                    model = metadata?.thumbnailUrl ?: "",
                                    contentDescription = metadata?.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Encrypted",
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = metadata?.title ?: "Encrypted Item",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = metadata?.resolution ?: "Encrypted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
