# Database Encryption Migration & Rollback Plan

This document describes the recommended migration and rollback strategy for moving from a plaintext Room DB to an encrypted SQLCipher-backed DB in t1dTracker.

Key implementation notes:
- The app creates timestamped backups of existing DB files in `context.filesDir/db_migration_backups/` before performing any swap.
- Backups are named like `t1d_db-YYYYMMDD...\.bak`, `t1d_db-shm.<ts>.bak`, `t1d_db-wal.<ts>.bak`.

Migration flow (what the app does):
1. Prompt user to export an encrypted backup (recommended). The Settings UI offers an "Export now" action.
2. App exports current data to a password-protected backup file (PBKDF2 + AES-GCM).
3. App builds a temporary encrypted DB using a generated passphrase, imports rows, and validates counts.
4. Before replacing files, app copies original DB files to `db_migration_backups/` with a timestamp suffix.
5. App atomically replaces DB files (database file + -shm, -wal), clears in-memory singleton, and signals success.

Rollback plan:
- If migration fails or user wants to revert, the app exposes a restore helper that copies files from `db_migration_backups/` back into place and resets the DB singleton.
- Users should be instructed to restart the app after a restore.

Recovery recommendations:
- Always keep at least one external encrypted backup (via Export) before migrating.
- Do not delete backup files; the app keeps copies under `db_migration_backups/` in internal storage.
- If you lose both the backup password and the device, data cannot be recovered.

Retention and cleanup:
- Consider adding automatic cleanup: retain last N backups or delete backups older than X days. This is not enforced by current code.

Manual recovery steps (for advanced users):
1. Find backups in the app internal storage: `/data/data/<package>/files/db_migration_backups/` (or use the app's backing UI to restore).
2. Restore via the app's Settings -> Restore pre-migration backup.

Security notes:
- Backup files are encrypted with a user-supplied password using PBKDF2 + AES-GCM (300k iterations recommended).
- Never hard-code or store backup passwords in plaintext.
