#!/bin/bash
# Grant all necessary Health Connect permissions
adb shell pm grant com.cardio.fitbit android.permission.health.READ_HEART_RATE
adb shell pm grant com.cardio.fitbit android.permission.health.READ_STEPS
adb shell pm grant com.cardio.fitbit android.permission.health.READ_DISTANCE
adb shell pm grant com.cardio.fitbit android.permission.health.READ_SLEEP
adb shell pm grant com.cardio.fitbit android.permission.health.READ_ACTIVE_CALORIES_BURNED
adb shell pm grant com.cardio.fitbit android.permission.health.READ_TOTAL_CALORIES_BURNED
adb shell pm grant com.cardio.fitbit android.permission.health.READ_EXERCISE

echo "✅ All Health Connect permissions granted!"

# Restart app to ensure permissions are picked up
echo "Restarting app..."
adb shell "am force-stop com.cardio.fitbit && am start -n com.cardio.fitbit/.ui.MainActivity"
echo "✅ App restarted."
