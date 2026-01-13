package com.cardio.fitbit.ui.components;

import android.graphics.Color;
import android.view.ViewGroup;
import androidx.compose.foundation.layout.*;
import androidx.compose.material3.*;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import com.cardio.fitbit.R;
import com.cardio.fitbit.data.models.Activity;
import com.cardio.fitbit.data.models.MinuteData;
import com.cardio.fitbit.utils.DateUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import java.util.*;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000(\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0002\u001a&\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u00052\u0006\u0010\u0007\u001a\u00020\bH\u0007\u001a\u001e\u0010\t\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u0007\u001a\u0018\u0010\u000b\u001a\u00020\u00012\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\rH\u0007\u00a8\u0006\u000f"}, d2 = {"ActivityDetailCard", "", "activity", "Lcom/cardio/fitbit/data/models/Activity;", "allMinuteData", "", "Lcom/cardio/fitbit/data/models/MinuteData;", "selectedDate", "Ljava/util/Date;", "ActivityHeartRateChart", "activityMinutes", "StatItem", "label", "", "value", "app_debug"})
public final class ActivityDetailCardKt {
    
    @androidx.compose.runtime.Composable()
    public static final void ActivityDetailCard(@org.jetbrains.annotations.NotNull()
    com.cardio.fitbit.data.models.Activity activity, @org.jetbrains.annotations.NotNull()
    java.util.List<com.cardio.fitbit.data.models.MinuteData> allMinuteData, @org.jetbrains.annotations.NotNull()
    java.util.Date selectedDate) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void StatItem(@org.jetbrains.annotations.NotNull()
    java.lang.String label, @org.jetbrains.annotations.NotNull()
    java.lang.String value) {
    }
    
    @androidx.compose.runtime.Composable()
    public static final void ActivityHeartRateChart(@org.jetbrains.annotations.NotNull()
    com.cardio.fitbit.data.models.Activity activity, @org.jetbrains.annotations.NotNull()
    java.util.List<com.cardio.fitbit.data.models.MinuteData> activityMinutes) {
    }
}