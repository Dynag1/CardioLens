# Google Play Console Health Connect Justification Guide (CardioLens)

This guide contains **copy-pasteable English rationales** and step-by-step instructions to successfully appeal and resolve the Google Play Store rejection for CardioLens.

---

## Part 1: Play Console Declaration Form Settings

When filling out the Health Connect declaration in the Play Console, ensure you configure the following settings:

### 1. Core App Feature
*   **Select Core Category**: Health Dashboard / Data Visualization & Analysis.
*   **Core Feature Description (Copy & Paste)**:
    > CardioLens is a local-first health dashboard that aggregates, visualizes, and analyzes heart and fitness metrics. The app provides users with a comprehensive view of their cardiovascular health by displaying 24/7 interactive heart rate charts, calculating daily resting heart rate (RHR), tracking sleep stage patterns, monitoring heart rate variability (HRV) recovery trends, and correlation of active calories and steps to physical exercises.

### 2. Permissions to Select (ONLY these 6)
Ensure that you **deselect/uncheck** `Distance`, `Oxygen Saturation (SpO2)`, and `Total Calories Burned`. Only select the following:
*   `Active Calories Burned` (Read)
*   `Exercise` (Read)
*   `Heart Rate` (Read)
*   `Heart Rate Variability (HRV)` (Read)
*   `Sleep` (Read)
*   `Steps` (Read)

---

## Part 2: Copy-Pasteable Rationales (English)

Use these precise, user-benefit-focused rationales for each requested permission:

### 1. Heart Rate (`READ_HEART_RATE`)
*   **User-Facing Feature**: Continuous 24/7 Heart Rate Charts & Resting Heart Rate (RHR) analysis.
*   **Rationale (Copy & Paste)**:
    > CardioLens reads heart rate data to generate interactive daily and weekly charts of the user's pulse. It uses this data to calculate the user's scientific resting heart rate (RHR) during resting periods, which is the primary metric displayed on the dashboard to help users track their general cardiovascular fitness and trends.

### 2. Heart Rate Variability (`READ_HEART_RATE_VARIABILITY`)
*   **User-Facing Feature**: HRV Recovery Trends.
*   **Rationale (Copy & Paste)**:
    > The app reads Heart Rate Variability (HRV) data to populate the HRV Trends screen. This screen displays a 30-day historical analysis of the user's RMSSD values, allowing the user to track their physical recovery, stress levels, and autonomic nervous system balance over time.

### 3. Sleep (`READ_SLEEP`)
*   **User-Facing Feature**: Sleep Quality & Sleep Stages Analysis.
*   **Rationale (Copy & Paste)**:
    > CardioLens reads sleep sessions (including light, deep, and REM sleep cycles) to visualize detailed sleep architecture charts on the dashboard. This allows users to understand their sleep quality and correlates sleep metrics with overnight heart rate recovery.

### 4. Steps (`READ_STEPS`)
*   **User-Facing Feature**: Daily Activity & Steps Tracking.
*   **Rationale (Copy & Paste)**:
    > The app reads step count data to show a daily steps summary card and charts on the main dashboard. This tracks the user's active movement and is used as a base parameter in the health correlation algorithms to separate resting periods from active periods.

### 5. Active Calories Burned (`READ_ACTIVE_CALORIES_BURNED`)
*   **User-Facing Feature**: Exercise Energy Expenditure.
*   **Rationale (Copy & Paste)**:
    > CardioLens reads active calories burned to display energy expenditure on the dashboard and link caloric burn with specific exercise sessions. This helps users monitor the energy cost of their physical activity separate from basal metabolic rate.

### 6. Exercise (`READ_EXERCISE`)
*   **User-Facing Feature**: Workout Heart Rate Response.
*   **Rationale (Copy & Paste)**:
    > The app reads exercise sessions to show a list of workouts on the dashboard. Selecting a workout displays a dedicated screen that plots the user's heart rate zones specifically during that exercise, enabling users to analyze cardiac response and recovery.

---

## Part 3: Demo Video Requirements (CRITICAL)

Google Play reviewers **require** a video showing the exact flow of the application.
Prepare a screen recording showing:
1.  **Onboarding/First launch**: Navigate to the **Health Connect configuration screen** (`HealthConnectPermissionsScreen`). This screen must show the user disclosure explaining exactly why permissions are requested (already implemented in the code!).
2.  **Consent**: Click "Autoriser l'Accès", which will open the system Health Connect consent dialog. Accept all 6 permissions.
3.  **Visualization (The key step)**:
    *   Show the main dashboard loading data.
    *   Point out/click on the **Heart Rate chart**.
    *   Point out/click on the **Sleep card** showing sleep stages.
    *   Point out/click on the **Workouts/Exercises** list showing a workout's heart rate zones.
    *   Navigate to the **Trends** screen and show the **HRV chart** loaded.
4.  **Proof of Local Storage**: Show the backup settings or a brief note emphasizing that data remains local-only.

---

## Part 4: Data Safety Declaration in Play Console

Update the Data Safety section of your store listing to match:
*   **Data Collection**: Yes, the app collects/reads data from Health Connect.
*   **Data Sharing**: **No**, data is never shared with third parties.
*   **Data Transmission**: Data is stored locally and is NOT sent off the device (except for optional manual backup files created by the user).
*   **Data Deletion**: Users can delete their data by clearing app storage, disconnecting the service, or using the in-app logout options.
