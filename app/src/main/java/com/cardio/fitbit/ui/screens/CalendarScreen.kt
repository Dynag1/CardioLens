package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.background
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
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }

    // Reload trends when month changes (fetch 30-40 days around month)
    // For now, TrendsViewModel fetches 7/15/30 days from TODAY back. 
    // To support arbitrary months, we'd need to update ViewModel. 
    // For MVP, we stick to "Last 30 Days" view or assume TrendsViewModel has data if within range.
    // IMPROVEMENT: Trigger a load for the specific month.
    
    // We will use the existing "Success" state data.
    val dataMap = (uiState as? TrendsUiState.Success)?.data?.associateBy { 
        DateUtils.formatForApi(it.date) 
    } ?: emptyMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendrier Santé") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                modifier = Modifier.fillMaxSize()
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
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (DateUtils.isSameDay(date, Date())) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onNavigateToDashboard(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = if(point != null) FontWeight.Bold else FontWeight.Normal)
                            )
                            
                            if (point != null) {
                                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.height(12.dp)) {
                                    // Mood Dot
                                    point.moodRating?.let { mood ->
                                        val color = when(mood) {
                                            5, 4 -> Color.Green
                                            3 -> Color.Yellow
                                            else -> Color.Red
                                        }
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    
                                    // Symptom Dot
                                    if (!point.symptoms.isNullOrEmpty()) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE57373))) // Red for symptoms
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

