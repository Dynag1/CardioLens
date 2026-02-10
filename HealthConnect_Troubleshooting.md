# Health Connect & Synchronization

## 1. Important: Health Connect is Local
**Health Connect does NOT sync data between devices.**
- Data stored in Health Connect on your **Phone A** stays on Phone A.
- It is not a cloud service like Google Fit was.
- Installing CardioLens on **Phone B** will show an empty dashboard initially, because Phone B's Health Connect is empty.

## 2. How to "Sync" Between Devices?

### Option A: Sync via Source App (Recommended for New Data)
If you use a wearable (Galaxy Watch, Pixel Watch, Fitbit, Oura...) associated with a cloud account:
1.  Install the **official app** of your wearable (Samsung Health, Fitbit, etc.) on **Phone B**.
2.  Log in to that app on **Phone B**. It should pull your history from its own cloud.
3.  Go to that app's settings and **Enable sync with Health Connect** on Phone B.
4.  CardioLens on Phone B will then see this data.

### Option B: Transfer CardioLens History (Backup & Restore)
If you want to transfer your CardioLens settings, mood history, and symptoms to another device:
1.  **On Old Device:**
    - Go to **Settings** -> **Backup & Restore**.
    - Connect **Google Drive**.
    - Click **"Backup to Drive Now"**.
2.  **On New Device:**
    - Install CardioLens.
    - Go to **Settings** -> **Backup & Restore**.
    - Connect **Google Drive**.
    - Click **"Restore from Drive"** and select the latest file.

## Troubleshooting "Only Steps Visible"
If you see Steps but no Heart Rate on the new device:
- The **Phone** itself is counting steps (it acts as a pedometer).
- **Heart Rate** comes *only* from a wearable. 
- **Solution:** Ensure your wearable app is installed on the new phone and writing to Health Connect.
