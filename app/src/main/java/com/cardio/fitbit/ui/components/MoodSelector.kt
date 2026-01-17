package com.cardio.fitbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun MoodSelector(
    currentRating: Int?, // 1-5 or null
    onRatingSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Comment allez-vous ?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MoodItem("😫", 1, currentRating, onRatingSelected)
                MoodItem("😞", 2, currentRating, onRatingSelected)
                MoodItem("😐", 3, currentRating, onRatingSelected)
                MoodItem("🙂", 4, currentRating, onRatingSelected)
                MoodItem("😀", 5, currentRating, onRatingSelected)
            }
        }
    }
}

@Composable
fun MoodItem(
    emoji: String,
    rating: Int,
    currentRating: Int?,
    onSelect: (Int) -> Unit
) {
    val isSelected = currentRating == rating
    val scale = if (isSelected) 1.2f else 1.0f
    val alpha = if (currentRating != null && !isSelected) 0.4f else 1.0f

    Text(
        text = emoji,
        fontSize = 15.sp,
        modifier = Modifier
            .clickable { onSelect(rating) }
            .padding(8.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            )
    )
}
