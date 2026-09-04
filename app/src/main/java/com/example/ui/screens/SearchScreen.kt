package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedVideo
import com.example.ui.TubeVaultViewModel
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted

@Composable
fun SearchScreen(
    viewModel: TubeVaultViewModel,
    videos: List<DownloadedVideo>,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.librarySearchQuery.collectAsState()

    val filteredVideos = if (searchQuery.isBlank()) {
        videos
    } else {
        videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeOledDark)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Recherche",
            style = MaterialTheme.typography.headlineMedium,
            color = TubeTextLight,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Rechercher parmi vos téléchargements",
            style = MaterialTheme.typography.bodyMedium,
            color = TubeTextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_text_input"),
            placeholder = { Text("Titre de la vidéo...", color = TubeTextMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TubeAccent
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Effacer la recherche",
                            tint = TubeTextMuted
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TubeAccent,
                unfocusedBorderColor = TubeBorder,
                focusedTextColor = TubeTextLight,
                unfocusedTextColor = TubeTextLight,
                focusedContainerColor = TubeSurfaceVariant,
                unfocusedContainerColor = TubeSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TubeTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "Aucune vidéo dans la bibliothèque" else "Aucun résultat pour « $searchQuery »",
                        color = TubeTextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredVideos, key = { it.id }) { video ->
                    DownloadedVideoListCard(
                        video = video,
                        onPlay = { viewModel.openPlayer(video) },
                        onDelete = { viewModel.deleteDownloadedVideo(video) }
                    )
                }
            }
        }
    }
}
