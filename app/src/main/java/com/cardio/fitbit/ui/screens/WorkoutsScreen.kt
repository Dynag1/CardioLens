package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.ui.components.ActivityDetailCard
import com.cardio.fitbit.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutsScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutsViewModel = hiltViewModel(),
    openDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val intradayCache by viewModel.intradayCache.collectAsState()
    val dateOfBirth by viewModel.dateOfBirth.collectAsState()
    
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
    
    val vibrantExportFile by viewModel.vibrantExportEvent.collectAsState()
    
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
            context.startActivity(android.content.Intent.createChooser(intent, "Partager le rapport (PDF)"))
            viewModel.clearExportEvent()
        }
    }

    LaunchedEffect(vibrantExportFile) {
        vibrantExportFile?.let { file ->
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Partage Social (Vibrant)"))
            viewModel.clearVibrantExportEvent()
        }
    }

    // State for expanded weeks
    val expandedWeeks = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entraînements") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { paddingValues ->
        val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.loadWorkouts()
            }
        }
        
        LaunchedEffect(uiState) {
            if (uiState !is WorkoutsUiState.Loading) {
                pullRefreshState.endRefresh()
            }
        }
        
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
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
                    val selectedType by viewModel.selectedActivityType.collectAsState()
                    val groupedActivities by viewModel.groupedActivities.collectAsState()
                    
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
                            // Monthly Statistics Card - EN HAUT
                            item {
                                val stats by viewModel.monthlyStats.collectAsState()
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Ce mois-ci",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            
                                            val monthlySummaries by viewModel.monthlySummaries.collectAsState()
                                            Row {
                                                if (monthlySummaries.isNotEmpty()) {
                                                    val recentSummary = monthlySummaries.first()
                                                    
                                                    TextButton(
                                                        onClick = { viewModel.exportMonthlyVibrantSummary(context, recentSummary) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Partager", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                    
                                                    TextButton(
                                                        onClick = { viewModel.exportMonthlyPdf(context, recentSummary) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Rapport PDF", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                }
                                            }
                                        }
                                        
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        "${stats.totalActivities}",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "Activités",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    val totalMinutes = stats.avgDuration / (1000 * 60)
                                                    val hours = totalMinutes / 60
                                                    val minutes = totalMinutes % 60
                                                    Text(
                                                        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "Durée moy.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        if (stats.avgIntensity > 0) String.format("%.1f", stats.avgIntensity) else "-",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "Intensité",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        if (stats.avgSpeed > 0) String.format("%.1f", stats.avgSpeed) else "-",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "Vitesse",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                                
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        "${stats.totalCalories}",
                                                        style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        "Calories",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                            
                            
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
                                            com.cardio.fitbit.ui.components.WeeklyWorkoutSummaryCard(
                                                summary = weeklySummaries[page], 
                                                onExportClick = { 
                                                    viewModel.exportPdf(context, it)
                                                },
                                                onVibrantShareClick = {
                                                    viewModel.exportVibrantSummary(context, it)
                                                }
                                            )
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
                            
                            // Filter Chips
                            item {
                                val activityTypes by viewModel.availableActivityTypes.collectAsState()
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    activityTypes.forEach { type ->
                                        FilterChip(
                                            selected = selectedType == type,
                                            onClick = { viewModel.setActivityTypeFilter(type) },
                                            label = { Text(type) },
                                            leadingIcon = if (selectedType == type) {
                                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                            
                            // Sort Dropdown
                            item {
                                val sortOrder by viewModel.sortOrder.collectAsState()
                                var expanded by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Trier par:", style = MaterialTheme.typography.labelMedium)
                                    
                                    Box {
                                        OutlinedButton(
                                            onClick = { expanded = true }
                                        ) {
                                            Text(
                                                when (sortOrder) {
                                                    WorkoutsViewModel.SortOrder.RECENT -> "Plus récent"
                                                    WorkoutsViewModel.SortOrder.DURATION -> "Durée"
                                                    WorkoutsViewModel.SortOrder.INTENSITY -> "Intensité"
                                                }
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Plus récent") },
                                                onClick = {
                                                    viewModel.setSortOrder(WorkoutsViewModel.SortOrder.RECENT)
                                                    expanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Durée") },
                                                onClick = {
                                                    viewModel.setSortOrder(WorkoutsViewModel.SortOrder.DURATION)
                                                    expanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Intensité") },
                                                onClick = {
                                                    viewModel.setSortOrder(WorkoutsViewModel.SortOrder.INTENSITY)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }


                            // Grouped Activities by Week

                            groupedActivities.forEach { weekGroup ->
                                val groupKey = weekGroup.year * 100 + weekGroup.weekNumber
                                val isExpanded = expandedWeeks[groupKey] ?: false
                                
                                // Week Header
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedWeeks[groupKey] = !isExpanded }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    weekGroup.weekLabel,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Réduire" else "Développer",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                
                                // Activities in this week (only if expanded)
                                items(if (isExpanded) weekGroup.activities else emptyList()) { item ->
                                    val dateStr = DateUtils.formatForApi(item.date)
                                    val minuteData = intradayCache[dateStr] ?: emptyList()
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    
                                    Column {
                                        // Date Header with Activity Icon and Actions
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = getActivityIcon(item.activity.activityName),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = DateUtils.formatForDisplay(item.fullDateOfActivity),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            // Action buttons
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // Share button
                                                IconButton(
                                                    onClick = {
                                                        val shareText = viewModel.getActivityShareText(item.activity, item.fullDateOfActivity)
                                                        val sendIntent = android.content.Intent().apply {
                                                            action = android.content.Intent.ACTION_SEND
                                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                            type = "text/plain"
                                                        }
                                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Partager l'activité"))
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Share,
                                                        contentDescription = "Partager",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                
                                                // Delete button
                                                var showDeleteDialog by remember { mutableStateOf(false) }
                                                IconButton(
                                                    onClick = { showDeleteDialog = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Supprimer",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                
                                                // Confirmation dialog
                                                if (showDeleteDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showDeleteDialog = false },
                                                        title = { Text("Supprimer l'activité ?") },
                                                        text = { Text("Êtes-vous sûr de vouloir supprimer cette activité ? Cette action est irréversible.") },
                                                        confirmButton = {
                                                            TextButton(
                                                                onClick = {
                                                                    viewModel.deleteActivity(item.activity.activityId)
                                                                    showDeleteDialog = false
                                                                }
                                                            ) {
                                                                Text("Supprimer", color = MaterialTheme.colorScheme.error)
                                                            }
                                                        },
                                                        dismissButton = {
                                                            TextButton(onClick = { showDeleteDialog = false }) {
                                                                Text("Annuler")
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        
                                        ActivityDetailCard(
                                            activity = item.activity,
                                            allMinuteData = minuteData,
                                            selectedDate = item.date,
                                            dateOfBirth = dateOfBirth,
                                            onIntensityChange = { activityId, intensity ->
                                                viewModel.saveWorkoutIntensity(activityId, intensity)
                                            }
                                        )
                                    }
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
            
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun getActivityIcon(activityName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        activityName.contains("run", ignoreCase = true) -> Icons.Default.DirectionsRun
        activityName.contains("bike", ignoreCase = true) || activityName.contains("cycling", ignoreCase = true) -> Icons.Default.DirectionsBike
        activityName.contains("walk", ignoreCase = true) -> Icons.Default.DirectionsWalk
        activityName.contains("swim", ignoreCase = true) -> Icons.Default.Pool
        activityName.contains("workout", ignoreCase = true) || activityName.contains("weights", ignoreCase = true) -> Icons.Default.FitnessCenter
        else -> Icons.Default.SportsScore
    }
}
