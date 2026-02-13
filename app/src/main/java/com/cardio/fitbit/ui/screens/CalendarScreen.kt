package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: TrendsViewModel = hiltViewModel(),
    onNavigateToDashboard: (Date) -> Unit,
    onNavigateBack: () -> Unit,
    openDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        viewModel.loadTrends(60)
    }

    // Reload trends when month changes (fetch 30-40 days around month)
    // For now, TrendsViewModel fetches 7/15/30 days from TODAY back. 
    // To support arbitrary months, we'd need to update ViewModel. 
    // For MVP, we stick to "Last 30 Days" view or assume TrendsViewModel has data if within range.
    // IMPROVEMENT: Trigger a load for the specific month.
    
    // Retain data across Loading states to prevent flickering
    var cachedPoints by remember { mutableStateOf<List<TrendPoint>>(emptyList()) }
    
    LaunchedEffect(uiState) {
        if (uiState is TrendsUiState.Success) {
            cachedPoints = (uiState as TrendsUiState.Success).data
        }
    }

    val displayedPoints = (uiState as? TrendsUiState.Success)?.data ?: cachedPoints

    val dataMap = displayedPoints.associateBy { 
        DateUtils.formatForApi(it.date) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendrier Santé") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Month Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth.add(Calendar.MONTH, -1)
                    currentMonth = currentMonth.clone() as Calendar
                    // Load more data if needed?
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Précédent")
                }
                
                Text(
                    text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time).capitalize(Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    currentMonth.add(Calendar.MONTH, 1)
                    currentMonth = currentMonth.clone() as Calendar
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Suivant")
                }
            }
            
            Spacer(Modifier.height(16.dp))

            // Days Grid
            val days = getDaysInMonth(currentMonth)
            val weekDays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
            
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekDays.forEach { 
                    Text(it, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.weight(1f)
            ) {
                // Empty slots for start offset
                items(days.first) {
                    Box(Modifier.aspectRatio(1f))
                }
                
                // Days
                items(days.second) { day ->
                    val date = day.date
                    val dateStr = DateUtils.formatForApi(date)
                    val point = dataMap[dateStr]
                    
                    val moodColor = point?.moodRating?.let { mood ->
                        when(mood) {
                            5, 4 -> Color(0xFFA5D6A7) // Green 200
                            3 -> Color(0xFFFFF59D) // Yellow 200
                            else -> Color(0xFFEF9A9A) // Red 200
                        }
                    }

                    val backgroundColor = moodColor ?: if (DateUtils.isSameDay(date, Date())) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    val contentColor = if (moodColor != null) Color.Black else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(backgroundColor)
                            .then(if (DateUtils.isSameDay(date, Date()) && moodColor != null) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .clickable { onNavigateToDashboard(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = LocalTextStyle.current.copy(
                                    fontSize = 12.sp, 
                                    fontWeight = if(point != null) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor
                                )
                            )
                            
                            if (point != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), 
                                    modifier = Modifier.height(12.dp)
                                ) {
                                    // Removed Mood Dot (redundant with background)
                                    
                                    // Symptom Dot
                                    if (!point.symptoms.isNullOrEmpty()) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFD32F2F))) // Darker Red for visibility
                                    }

                                    // Workout Dot with Intensity
                                    if ((point.workoutDurationMinutes ?: 0) > 0) {
                                        // Show intensity with color coding if available
                                        point.workoutMaxIntensity?.let { intensity ->
                                            val intensityColor = when {
                                                intensity >= 4 -> Color(0xFFEF4444) // Red for high
                                                intensity >= 3 -> Color(0xFFF59E0B) // Orange for moderate
                                                else -> Color(0xFF10B981) // Green for light
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                repeat(intensity.coerceIn(1, 5)) {
                                                    Box(Modifier.size(3.dp).clip(CircleShape).background(intensityColor))
                                                }
                                            }
                                        } ?: run {
                                            // Fallback to simple workout dot if no intensity
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF1976D2)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Legend
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Légende :", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mood
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFA5D6A7))) // Green 200
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFF59D))) // Yellow 200
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF9A9A))) // Red 200
                        }
                        Text("Humeur (Fond)", style = MaterialTheme.typography.labelSmall)
                    }

                    // Symptoms
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFD32F2F)))
                        Text("Symptômes", style = MaterialTheme.typography.labelSmall)
                    }

                    // Workouts Intensity
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981))) // Green - Light
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B))) // Orange - Moderate
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444))) // Red - High
                        }
                        Text("Intensité", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

data class CalendarDay(val dayOfMonth: Int, val date: Date)

fun getDaysInMonth(calendar: Calendar): Pair<Int, List<CalendarDay>> {
    val cal = calendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2...
    // Adjust for Monday start (Mon=1, ..., Sun=7)
    val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
    
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val days = (1..daysInMonth).map {
        cal.set(Calendar.DAY_OF_MONTH, it)
        CalendarDay(it, cal.time)
    }
    
    return Pair(Math.max(0, offset), days)
}

