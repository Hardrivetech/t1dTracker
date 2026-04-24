# Data Retention & Export/Delete User Flows

This document outlines recommended retention, export, and deletion behaviors for t1dTracker.

Retention
- All health data (insulin entries, carbs, glucose) is stored locally on the device in the app's private storage.
- The app does not upload health data by default. If telemetry is enabled, no health data is sent.
- Consider a configurable retention policy in the future (e.g., retain last N years or X entries).

Export
- Users can create encrypted backups using a password they provide. Backups are encrypted with PBKDF2-derived keys and AES-GCM.
- The app will not store or transmit the backup password; users are responsible for safekeeping it.
- Encourage users to keep at least one external encrypted copy (cloud, drive, or a password manager record pointing to the backup password).

Delete
- Provide a clear in-app flow for users to delete local data and backups. Deletion should:
  - Remove all Room DB files and backup copies inside the app's internal `files/db_migration_backups/` folder.
  - Offer a confirmation warning that deletion is irreversible unless the user has an external encrypted backup.

Regulatory / Legal
- Document retention and deletion options in your privacy policy and in the app Settings where users perform exports/deletes.
- Keep an audit log (locally) of user-initiated exports/deletes if required by policy; if kept, do not store unencrypted sensitive metadata.

User Guidance
- Before migration or destructive actions, prompt users to create an encrypted backup and provide clear recovery steps.
- Add guidance text in Settings explaining backup password recommendations and the irrecoverable nature of lost passwords.
