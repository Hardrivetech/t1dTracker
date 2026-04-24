# Privacy Policy — t1dTracker

Last updated: 2026-04-23

This privacy policy explains how t1dTracker (the "App") handles your data.

1. Data collected and storage
- All health data (insulin entries, carbohydrate amounts, glucose values, notes) is stored only locally on your device. The App does not upload health data to any external server by default.
- The app stores entries in a local Room database and some non-sensitive settings in EncryptedSharedPreferences or regular SharedPreferences depending on device capabilities.

2. Backups
- When you create a backup from Settings → Export backup, the backup is encrypted locally with a password you provide. Backups use PBKDF2 (HMAC-SHA256) with 400,000 iterations and AES-GCM for confidentiality and integrity.
- Keep your backup password safe. The App cannot recover lost passwords.

3. Telemetry and crash reporting
- Crash reporting and analytics are disabled by default. You may opt in via Settings → Allow telemetry. Only anonymized usage metrics and crash reports will be sent if enabled.
- Telemetry does not include your health data (entries, doses, notes).

4. Biometric and encryption keys
- The app uses the Android Keystore when available to protect encryption keys and the database passphrase. On older devices a wrapped-key fallback may be used if the device supports it and you opt in.

5. Your rights
- You can delete all local data by uninstalling the app or using the provided in-app delete/export feature (if available). To request data deletion support, contact: hardrivetech@proton.me

6. Contact
- For questions about privacy or to request data removal, contact: hardrivetech@proton.me
# Privacy Policy (Draft)

This is a minimal privacy policy stub for t1dTracker. Host this page at a public URL (GitHub Pages, Netlify, etc.) and update the Play Store Data Safety form before publishing.

1. Data collected: app stores only non-identifying analytics if enabled by the user (Crashlytics/Analytics). No personal health data is sent unless the user explicitly chooses to share a backup file.
2. Backups: Encrypted backups are generated locally and protected by a user-provided password. We do not store or transmit backup passwords.
3. Telemetry: Disabled by default. Users must opt-in in Settings to enable Crashlytics/Analytics.
4. Data deletion: Users can delete local data from the app (Settings → Delete Data). Add server-side deletion instructions here if you offer cloud sync.

Contact: hardrivetech@proton.me
