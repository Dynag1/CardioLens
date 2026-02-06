package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cardio.fitbit.data.models.MinuteData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.data.models.SleepData
import com.cardio.fitbit.data.models.SleepLevel
import com.cardio.fitbit.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    date: Date,
    viewModel: SleepViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(date) {
        viewModel.setDate(date)
    }

    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val sleepDebtMinutes by viewModel.sleepDebtMinutes.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { viewModel.goToPreviousDay() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Or appropriate back icon
                                contentDescription = "Jour précédent"
                            )
                        }
                        
                        Text(
                            text = DateUtils.formatForDisplay(selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(onClick = { viewModel.goToNextDay() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Use inverted back or arrow forward if available. Let's check imports
                                contentDescription = "Jour suivant",
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        val pullRefreshState = rememberPullToRefreshState()
        
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.refresh()
            }
        }
        
        LaunchedEffect(uiState) {
            if (uiState !is SleepUiState.Loading) {
                pullRefreshState.endRefresh()
            }
        }

        // Logic to accumulate drag to decide swipe
        var offsetX by remember { mutableStateOf(0f) }
        val minSwipeOffset = 150f // Threshold

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > minSwipeOffset) {
                                viewModel.goToPreviousDay()
                            } else if (offsetX < -minSwipeOffset) {
                                viewModel.goToNextDay()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                }
        ) {
             when (val currentUiState = uiState) {
                 is SleepUiState.Loading -> {
                     CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                 }
                 is SleepUiState.Error -> {
                     Text(
                         text = "Erreur: ${currentUiState.message}",
                         color = MaterialTheme.colorScheme.error,
                         modifier = Modifier.align(Alignment.Center)
                     )
                 }
                 is SleepUiState.Success -> {
                     val sleepData = currentUiState.data
                     if (sleepData == null) {
                         Text(
                             text = "Aucune donnée de sommeil pour cette date.",
                             modifier = Modifier.align(Alignment.Center)
                         )
                     } else {
                         SleepContent(sleepData, currentUiState.heartRateData, sleepDebtMinutes)
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
fun SleepContent(data: SleepData, heartRateData: List<MinuteData>?, sleepDebtMinutes: Int?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Sleep Debt Card (Last 7 Days)
        if (sleepDebtMinutes != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                 Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Bilan Sommeil (7j)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Différence cumulée vs Objectif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val debtHours = kotlin.math.abs(sleepDebtMinutes) / 60
                    val debtMins = kotlin.math.abs(sleepDebtMinutes) % 60
                    
                    val (color, text, icon) = when {
                        sleepDebtMinutes > 120 -> Triple(MaterialTheme.colorScheme.error, "-${debtHours}h ${debtMins}m", Icons.Default.Warning) // > 2h debt
                        sleepDebtMinutes > 0 -> Triple(Color(0xFFFF9800), "-${debtHours}h ${debtMins}m", Icons.Default.Info) // Some debt
                        else -> Triple(Color(0xFF4CAF50), "+${debtHours}h ${debtMins}m", Icons.Default.CheckCircle) // Surplus or met
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(icon, contentDescription = null, tint = color)
                    }
                }
            }
        }
        
        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Résumé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                val totalMin = data.minutesAsleep + data.minutesAwake
                if (totalMin > 0) {
                    // Pie Chart
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SleepPhasesPieChart(
                            stages = data.stages,
                            minutesAwake = data.minutesAwake,
                            totalMinutes = totalMin,
                            modifier = Modifier.size(180.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Helper for summary
                        @Composable
                        fun StatItem(label: String, minutes: Int, color: Color) {
                            val percent = (minutes.toFloat() / totalMin * 100).toInt()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${minutes}m", // Duration
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Text(
                                    text = "$percent%", // Percentage
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        StatItem("Éveillé", data.minutesAwake, Color(0xFFFFEB3B))
                        StatItem("REM", data.stages?.rem ?: 0, Color(0xFF00BCD4))
                        StatItem("Léger", data.stages?.light ?: 0, Color(0xFF2196F3))
                        StatItem("Profond", data.stages?.deep ?: 0, Color(0xFF3F51B5))
                        
                        // Average Heart Rate
                        val validHr = heartRateData?.filter { it.heartRate > 0 }
                        if (!validHr.isNullOrEmpty()) {
                            val avgHr = validHr.map { it.heartRate }.average().toInt()
                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$avgHr",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red
                                )
                                Text(
                                    text = "bpm",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                                )
                                Text(
                                    text = "FC Moy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hypnogram Chart (Timeline with Bars)
        Card(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Chronologie du Sommeil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                HypnogramChart(
                    levels = data.levels, 
                    startTime = data.startTime, 
                    endTime = data.endTime,
                    heartRateData = heartRateData,
                    modifier = Modifier.fillMaxSize() // Use available space in card
                )
            }
        }
    }
}

@Composable
fun HypnogramChart(
    levels: List<SleepLevel>,
    startTime: Date,
    endTime: Date,
    heartRateData: List<MinuteData>?,
    modifier: Modifier = Modifier
) {
    if (levels.isEmpty()) return

    val onSurface = MaterialTheme.colorScheme.onSurface
    var selectedTime by remember { mutableStateOf<Long?>(null) }
    
    // Define Colors
    val stageColors = mapOf(
            "wake" to Color(0xFFFFEB3B), // Yellow
            "rem" to Color(0xFF00BCD4), // Cyan
            "light" to Color(0xFF2196F3), // Blue
            "deep" to Color(0xFF3F51B5), // Indigo
            "unknown" to Color.Gray
    )
    
    // Reduced ratios to avoid filling screen and make bars smaller
    val stageOrder = mapOf(
        "deep" to 0.15f,
        "rem" to 0.30f,
        "light" to 0.50f,
        "wake" to 0.70f,
        "unknown" to 0f
    )


    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        // Calculate time from x
                        val totalDuration = endTime.time - startTime.time
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedTime = startTime.time + (progress * totalDuration).toLong()
                        tryAwaitRelease()
                        selectedTime = null // Clear on release
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val totalDuration = endTime.time - startTime.time
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        selectedTime = startTime.time + (progress * totalDuration).toLong()
                    },
                    onDragEnd = {
                        selectedTime = null
                    },
                    onDragCancel = {
                        selectedTime = null
                    },
                    onHorizontalDrag = { change, _ ->
                        val totalDuration = endTime.time - startTime.time
                        val progress = (change.position.x / size.width).coerceIn(0f, 1f)
                        selectedTime = startTime.time + (progress * totalDuration).toLong()
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val totalDuration = endTime.time - startTime.time
        
        // 1. Draw Bars FIRST (so they are behind labels)
        levels.forEach { level ->
            val relativeStart = level.dateTime.time - startTime.time
            
            val xStart = (relativeStart.toFloat() / totalDuration) * width
            val widthBar = ((level.seconds * 1000).toFloat() / totalDuration) * width
            
            val ratio = stageOrder[level.level] ?: 0f
            val barHeight = height * ratio
            val yTop = height - barHeight
            
            val color = stageColors[level.level] ?: stageColors["unknown"]!!

            drawRect(
                color = color,
                topLeft = Offset(xStart, yTop),
                size = Size(widthBar, barHeight)
            )
        }
        
        // 1.5 Draw Heart Rate Line
        if (heartRateData != null && heartRateData.isNotEmpty()) {
            val validHr = heartRateData.filter { it.heartRate > 0 }
            if (validHr.isNotEmpty()) {
                val minHr = (validHr.minOf { it.heartRate } - 5).coerceAtLeast(0)
                val maxHr = validHr.maxOf { it.heartRate } + 5
                val hrRange = maxHr - minHr
                
                val hrPath = Path()
                var firstPoint = true
                
                validHr.forEach { point ->
                    val pointTime = DateUtils.parseFitbitTimeOrDateTime(point.time, startTime)
                    if (pointTime != null) {
                        // Fix day crossover if needed
                        var timeMillis = pointTime.time
                        // If point time is significantly before start time (e.g., > 12h diff), add a day.
                        // Or simplistic check: if time < startTime and we expect it to be within duration
                        // Better: rely on DateUtils robust parsing or if only Time is provided, handle wraparound
                        // The DateUtils.parseFitbitTimeOrDateTime uses refDate=startTime. 
                        // If startTime=23:00 and point="00:15", using startTime as ref creates 00:15 THAT day (past).
                        // We need to check if result < startTime, add 24h
                        if (timeMillis < startTime.time) {
                             timeMillis += 24 * 60 * 60 * 1000
                        }
                        
                        val relative = timeMillis - startTime.time
                         
                        if (relative in 0..totalDuration) {
                             val x = (relative.toFloat() / totalDuration) * width
                             val normalizedHr = (point.heartRate - minHr).toFloat() / hrRange
                             // Inverted Y: 0 is top. Max HR should be near top (y=0?), Min HR near bottom?
                             // Usually HR overlay is separate scale. Let's verify requirement.
                             // Assuming overlay on top. 
                             // Let's ensure it doesn't obscure bars too much.
                             // Maybe map minHr to height (bottom) and maxHr to 0 (top)?
                             // Or align with some axis. Without axis, just scaling to view height is standard.
                             val y = height - (normalizedHr * height)
                             
                             if (firstPoint) {
                                  hrPath.moveTo(x, y)
                                  firstPoint = false
                             } else {
                                  hrPath.lineTo(x, y)
                             }
                        }
                    }
                }
                
                drawPath(
                    path = hrPath,
                    color = Color.Red.copy(alpha = 0.5f),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                 // Draw Min/Max HR Labels on Right
            }
        }
        
        // 2. Draw Y-Axis Guide Lines and Labels ON TOP
        stageOrder.forEach { (stage, ratio) ->
            if (stage != "unknown") {
                val y = height * (1 - ratio) // Convert ratio to Y coord
                
                // Draw faint guide line
                drawLine(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
                
                 val label = when(stage) {
                   "wake" -> "Éveil"
                   "rem" -> "REM"
                   "light" -> "Léger"
                   "deep" -> "Profond"
                   else -> ""
               }
               
               // Draw labels on Left
               drawContext.canvas.nativeCanvas.drawText(
                   label,
                   10f, 
                   y - 10f, 
                   android.graphics.Paint().apply {
                        this.color = onSurface.hashCode()
                        textSize = 28f
                        isFakeBoldText = true 
                        textAlign = android.graphics.Paint.Align.LEFT
                   }
               )
            }
        }
        
        // 3. Draw Touch Highlight
        selectedTime?.let { time ->
            val relativeTime = time - startTime.time
            val x = (relativeTime.toFloat() / totalDuration) * width
            
            // Vertical Line
            drawLine(
                color = onSurface.copy(alpha = 0.8f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            
            // Find stage at this time
            val stageAtTime = levels.find { level ->
                val start = level.dateTime.time
                val end = start + (level.seconds * 1000)
                time in start until end
            }
            

            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val text = stageAtTime?.let { level ->
                val start = level.dateTime
                val end = Date(level.dateTime.time + level.seconds * 1000)
                
                val label = when(level.level) {
                   "wake" -> "Éveil"
                   "rem" -> "REM"
                   "light" -> "Léger"
                   "deep" -> "Profond"
                   else -> ""
                }
                
                "${timeFormat.format(start)} - ${timeFormat.format(end)} : $label"
            } ?: "--"
            
            // Draw Tooltip Box
            val textPaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 32f
                isFakeBoldText = true 
                textAlign = android.graphics.Paint.Align.CENTER
            }
            
            val textWidth = textPaint.measureText(text)
            val boxWidth = textWidth + 40f
            val boxHeight = 60f
            
            // Clamp tooltip to screen
            var boxLeft = x - boxWidth / 2
            if (boxLeft < 0) boxLeft = 0f
            if (boxLeft + boxWidth > width) boxLeft = width - boxWidth
            
            val boxTop = 20f
            
            drawRoundRect(
                color = Color(0xFF424242), // Dark Grey Background
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
            
            // Draw a small border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            
            drawContext.canvas.nativeCanvas.drawText(
                text,
                boxLeft + boxWidth / 2,
                boxTop + 40f, // Center vertically roughly
                textPaint
            )
        }


        // Draw Time Axis (Bottom)
        val paint = android.graphics.Paint().apply {
            this.color = onSurface.hashCode()
            textSize = 28f
             textAlign = android.graphics.Paint.Align.CENTER
        }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Draw start time
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(
             timeFormat.format(startTime),
             0f,
             height, // At very bottom? Might overlap bars.
             paint
        )
         // Draw end time
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(
             timeFormat.format(endTime),
             width,
             height,
             paint
        )
        
        // Draw mid time
        val midTime = startTime.time + totalDuration / 2
        paint.textAlign = android.graphics.Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(
             timeFormat.format(Date(midTime)),
             width / 2,
             height,
             paint
        )
    }
}

@Composable
fun SleepPhasesPieChart(
    stages: com.cardio.fitbit.data.models.SleepStages?,
    minutesAwake: Int,
    totalMinutes: Int, // Kept for signature compatibility but we'll re-sum for the chart
    modifier: Modifier = Modifier
) {
    val wakeColor = Color(0xFFFFEB3B)
    val remColor = Color(0xFF00BCD4)
    val lightColor = Color(0xFF2196F3)
    val deepColor = Color(0xFF3F51B5)

    val wakeMinutes = minutesAwake
    val remMinutes = stages?.rem ?: 0
    val lightMinutes = stages?.light ?: 0
    val deepMinutes = stages?.deep ?: 0

    val slices = listOf(
        Triple(wakeMinutes, wakeColor, "wake"),
        Triple(remMinutes, remColor, "rem"),
        Triple(lightMinutes, lightColor, "light"),
        Triple(deepMinutes, deepColor, "deep")
    ).filter { it.first > 0 }
    
    // Normalize total to avoid gaps if totalMinutes doesn't match sum
    val chartTotal = slices.sumOf { it.first }.toFloat()
    if (chartTotal == 0f) return

    Canvas(modifier = modifier) {
        val width = size.width
        val radius = width / 2f
        val center = Offset(width / 2f, size.height / 2f)
        
        var startAngle = -90f // Start from top

        slices.forEach { (minutes, color, _) ->
            val sweepAngle = (minutes / chartTotal) * 360f
            
            // Draw slice
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                size = size
            )
            
            // Draw separator line
            drawArc(
                color = Color.White,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Draw Percentage
            val percentage = (minutes / chartTotal * 100).toInt()
            if (percentage > 4) { // Only show if significant enough space
                val angleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                val labelRadius = radius * 0.65f // Position at 65% of radius
                
                val x = center.x + (labelRadius * Math.cos(angleRad)).toFloat()
                val y = center.y + (labelRadius * Math.sin(angleRad)).toFloat()
                
                val textColor = if (color == deepColor || color == lightColor) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                
                drawContext.canvas.nativeCanvas.drawText(
                    "$percentage%",
                    x,
                    y + 10f, // minor vertical adjustment for centering
                    android.graphics.Paint().apply {
                        this.color = textColor
                        textSize = 32f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        setShadowLayer(5f, 0f, 0f, android.graphics.Color.GRAY) // Drop shadow for legibility
                    }
                )
            }

            startAngle += sweepAngle
        }
    }
}
