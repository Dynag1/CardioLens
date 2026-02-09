package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.ui.components.ActivityDetailCard
import com.cardio.fitbit.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WorkoutsScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val intradayCache by viewModel.intradayCache.collectAsState()
    
    // Pagination detection
    val listState = rememberLazyListState()
    val isScrollToEnd by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }
    
    LaunchedEffect(isScrollToEnd) {
        if (isScrollToEnd) {
            viewModel.loadNextPage()
        }
    }
    
    // Export Event Handling
    val exportFile by viewModel.exportEvent.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(exportFile) {
        exportFile?.let { file ->
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Partager le rapport"))
            viewModel.clearExportEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entraînements") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().zIndex(1f))
            }
            when (val state = uiState) {
                is WorkoutsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is WorkoutsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Erreur : ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is WorkoutsUiState.Success -> {
                    val weeklySummaries by viewModel.weeklySummaries.collectAsState()
                    
                    if (state.activities.isEmpty()) {
                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aucun entraînement trouvé.")
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Weekly Summary Pager
                            item {
                                if (weeklySummaries.isNotEmpty()) {
                                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { weeklySummaries.size })
                                    
                                    Column {
                                        androidx.compose.foundation.pager.HorizontalPager(
                                            state = pagerState,
                                            contentPadding = PaddingValues(horizontal = 0.dp),
                                            pageSpacing = 16.dp
                                        ) { page ->
                                            val context = androidx.compose.ui.platform.LocalContext.current
                                            com.cardio.fitbit.ui.components.WeeklyWorkoutSummaryCard(summary = weeklySummaries[page], onExportClick = { 
                                                viewModel.exportPdf(context, it)
                                            })
                                        }
                                        
                                        // Simple Pager Indicator
                                        Row(
                                            Modifier
                                                .height(20.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            repeat(weeklySummaries.size) { iteration ->
                                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                Box(
                                                    modifier = Modifier
                                                        .padding(2.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(color)
                                                        .size(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(state.activities) { item ->
                                val dateStr = DateUtils.formatForApi(item.date)
                                val minuteData = intradayCache[dateStr] ?: emptyList()
                                
                                Column {
                                    // Date Header if first item of day? Or always show date?
                                    // Let's show date above each card for clarity in a long list
                                    Text(
                                        text = DateUtils.formatForDisplay(item.fullDateOfActivity),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                    )
                                    
                                    ActivityDetailCard(
                                        activity = item.activity,
                                        allMinuteData = minuteData,
                                        selectedDate = item.date,
                                        dateOfBirth = null // TODO: Pass DOB if needed, or get from VM
                                    )
                                }
                            }
                            
                            // Loading indicator at bottom?
                             item {
                                if (state.activities.isNotEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
