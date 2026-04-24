# Play Store Data Safety — Draft

This document is a companion to the Play Store Data Safety form and lists the app's data practices for reviewers and release preparation.

Summary
- App name: t1dTracker
- Primary function: local, on-device tracking of Type 1 diabetes events (insulin, carbs, glucose) and simple insulin dose calculations.

Data Collected
- Health & fitness: User-provided entries (insulin doses, carbohydrate amounts, glucose readings) — Collected and stored locally. Not uploaded by default.
- Device or other identifiers: Crash/telemetry identifiers only when telemetry is enabled by the user and consent is given (PII redaction applied).
- Diagnostics: App crash reports (optional, user opt-in) — includes stack traces with PII redaction.

Data Uses
- App functionality: All health data is used locally to provide history, analytics, and dosing calculations.
- Diagnostics & analytics: Crash and usage statistics only if user consents; PII is redacted and data is never linked to a known user account.

Data Sharing
- The app does not share health data with third parties by default.
- Telemetry (if enabled): may be sent to a telemetry provider for crash analytics (see `PRIVACY_POLICY.md`) — the user must opt-in.

Storage & Protection
- Storage location: App private internal storage (Room DB + backups in `files/db_migration_backups/`).
- Encryption: Backups exported by the user are encrypted with a password-derived key (PBKDF2 + AES-GCM). The app supports optional SQLCipher-encrypted DB when device keystore is available.
- Retention: Data retained until user deletes it; retention guidelines documented in `DATA_RETENTION.md`.

User Controls
- Export/import: Users can export encrypted backups and import them manually.
- Telemetry: opt-in toggle in Settings.
- Delete: Provide an in-app delete flow that removes DB and internal backups; users are warned about irrecoverable deletion if they lack an external encrypted backup.

Contacts
- For privacy, data-removal, or questions: hardrivetech@proton.me

Notes for the Play Console
- When filling the Data Safety form, mark the primary data type as "Health & Fitness — user-provided" and indicate that data is stored on device and not shared, except for optional crash telemetry with opt-in.
