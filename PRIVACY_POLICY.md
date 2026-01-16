# Privacy Policy for CardioLens

**Last updated: January 16, 2026**

This Privacy Policy describes how CardioLens ("we", "us", or "our") collects, uses, and shares your information when you use our mobile application (the "App").

## 1. Information We Collect

### 1.1 Health and Fitness Data
CardioLens accesses and reads health data from third-party services you authorize, specifically **Fitbit**, **Google Fit**, and **Health Connect**. This data includes:
*   **Heart Rate Data**: Resting heart rate, beats per minute (BPM) during activities.
*   **Activity Data**: Step count, distance traveled, calories burned, and active minutes.
*   **Sleep Data**: Sleep duration, sleep stages (light, deep, REM), and sleep scores.

### 1.2 Device and App Usage Data
*   **Logs**: We may collect standard log information about how you use the App (e.g. timestamps of sync events) for debugging and improvement purposes.
*   **Device Permissions**: The App requests specific permissions to function:
    *   `INTERNET`: To communicate with Fitbit APIs and Google services.
    *   `FOREGROUND_SERVICE`: To ensure data synchronization can complete reliably in the background.
    *   `RECEIVE_BOOT_COMPLETED`: To restart synchronization schedules after your device reboots.

## 2. How We Use Your Information

We use the information we collect for the following purposes:
*   **Visualization**: To display your health trends, daily statistics, and historical data within the App's dashboard and charts.
*   **Analysis**: To calculate derived metrics such as day/night resting heart rate comparisons.
*   **Synchronization**: To keep your local view up-to-date with your health provider (Fitbit/Google).

**We do NOT sell your personal data.** We do not use your health data for advertising purposes.

## 3. Data Storage and Sharing

### 3.1 Local Storage
Your health data is stored locally on your device in a secure database. This allows you to view your history without a constant internet connection.

### 3.2 Third-Party Sharing
*   **Providers**: The App communicates directly with the APIs of the providers you link (e.g., Fitbit). Your credentials and tokens are stored securely on your device using encrypted storage.
*   **No External Servers**: We do not upload your health data to any centralized server owned by us. Your data remains on your device and with the health providers you have chosen.

## 4. Health Connect
If you choose to use Health Connect:
*   The App reads data from Health Connect based on the permissions you grant.
*   The use of information received from Health Connect will adhere to the [Health Connect Permissions Policy](https://support.google.com/googleplay/android-developer/answer/12293504), including the Limited Use constraints.

## 5. Security
We take reasonable measures to protect your information.
*   **Encryption**: OAuth tokens (used to access your Fitbit/Google data) are stored using Android's `EncryptedSharedPreferences`.
*   **Device Security**: We rely on the security features of your device to protect the local database.

## 6. Your Rights
You allow the App to access your data by logging in. You can revoke this access effectively by:
*   **Logging Out**: Using the "Logout" button in the App's menu.
*   **Revoking Permissions**: Going to your device settings (or Google Account settings for connected apps) and removing CardioLens's access.

## 7. Changes to This Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on this page and updating the "Last updated" date.

## 8. Contact Us
If you have any questions about this Privacy Policy, please contact us via our GitHub repository or support channel.
