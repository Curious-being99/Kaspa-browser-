package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookmarkEntity
import com.example.data.HistoryEntity
import com.example.ui.theme.*
import com.example.viewmodel.AppTab
import com.example.viewmodel.DecentralViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: DecentralViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0: History, 1: Bookmarks
    val history by viewModel.history.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Tab Header
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = ElectricCyan
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("History", modifier = Modifier.padding(16.dp), color = if (selectedTab == 0) ElectricCyan else TextMuted)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Bookmarks", modifier = Modifier.padding(16.dp), color = if (selectedTab == 1) ElectricCyan else TextMuted)
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> HistoryList(history, viewModel)
                1 -> BookmarkList(bookmarks, viewModel)
            }
        }
    }
}

@Composable
fun HistoryList(history: List<HistoryEntity>, viewModel: DecentralViewModel) {
    if (history.isEmpty()) {
        EmptyState("No history yet", Icons.Default.History)
    } else {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", color = TextMuted, fontSize = 12.sp)
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text("Clear All", color = Color.Red.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { item ->
                    LibraryItem(
                        title = item.title,
                        subtitle = item.url,
                        timestamp = item.timestamp,
                        onClick = { viewModel.openUrlInBrowser(item.url) },
                        onDelete = { viewModel.deleteHistoryItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkList(bookmarks: List<BookmarkEntity>, viewModel: DecentralViewModel) {
    if (bookmarks.isEmpty()) {
        EmptyState("No bookmarks saved", Icons.Default.Bookmark)
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(bookmarks) { item ->
                LibraryItem(
                    title = item.title,
                    subtitle = item.url,
                    timestamp = item.timestamp,
                    onClick = { viewModel.openUrlInBrowser(item.url) },
                    onDelete = { viewModel.toggleBookmark(item.url, item.title) }
                )
            }
        }
    }
}

@Composable
fun LibraryItem(title: String, subtitle: String, timestamp: Long, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = remember(timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title.ifBlank { "Untitled" }, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, color = TextMuted.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextMuted.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text, color = TextMuted, fontSize = 14.sp)
    }
}
