# Encryption Audit & Recommendations

Date: 2026-04-23

This document records the on-device encryption audit for t1dTracker, summarizes findings, and lists recommended remediation and migration steps.

## Summary

- Current approach: keystore-backed AES-GCM (`AES/GCM/NoPadding`) for prefs/backups and SQLCipher for Room when keystore usable.
- Backup KDF: PBKDF2WithHmacSHA256, iterations = 400_000 (default after 2026-04-23). Backup format header: `T1D1` (includes iterations and salt).
- Keystore alias: `t1d_t1dtracker_key_v1`. Rotation helper `EncryptionUtil.rotateKey()` exists.
- Pre-M fallback: transient AES key and optional RSA wrap key under alias `t1d_t1dtracker_key_v1_wrap` (no raw-key persistence).

## Findings

- Key generation in `EncryptionUtil.createSecretKey()` uses recommended AES/GCM 256-bit and attempts StrongBox when available. `setRandomizedEncryptionRequired(true)` is used when available which prevents supplying caller IVs (good).
- Encryption/decryption now zeroes temporary byte arrays in finally blocks.
- Telemetry and exception reporting are gated and PII is redacted before reporting.
- SQLCipher is enabled only when `EncryptionUtil.isKeystoreUsable(context)` to avoid creating inaccessible encrypted DBs on unsupported devices.

## Recommendations (code-level)

1. KeyGenParameterSpec (keystore) — ensure the following attributes are applied:

   - Purpose: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
   - Block modes: `GCM`
   - Padding: `ENCRYPTION_PADDING_NONE`
   - Key size: `256`
   - `setRandomizedEncryptionRequired(true)` when available (keystore-generated IVs)
   - Prefer StrongBox only when `PackageManager.FEATURE_STRONGBOX_KEYSTORE` is present; do not force it.

   Example builder (pseudocode):

   ```kotlin
   val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
       .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
       .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
       .setKeySize(256)
   if (supportsStrongBox) builder.setIsStrongBoxBacked(true)
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) try { builder.setRandomizedEncryptionRequired(true) } catch (_: Exception) {}
   val spec = builder.build()
   ```

2. Keystore usability: treat API>=23 (M) as supported, but also verify the keystore can be opened (`KeyStore.getInstance("AndroidKeyStore")` + `load(null)`) and catch errors. Only enable SQLCipher when a persistent encrypted passphrase can be reliably stored and restored.

3. Pre-M devices (API < 23): avoid persisting raw AES keys. The current RSA wrap approach (store RSA key in AndroidKeyStore and store wrapped AES in prefs) is a reasonable fallback; however:

   - Pre-M devices remain less secure. As of 2026-04-23 the codebase implements a conservative policy: SQLCipher will NOT be enabled by default on API < 23 devices. Wrapped-key fallbacks remain available but require explicit user action and are considered less secure than keystore-backed keys on API>=23.
      - Pre-M devices remain less secure. As of 2026-04-23 the codebase implements a conservative policy: SQLCipher will NOT be enabled by default on API < 23 devices. Wrapped-key fallbacks remain available but require explicit user action and are considered less secure than keystore-backed keys on API>=23.
      - The app exposes a Settings toggle ("Enable wrapped-key encryption on older devices (advanced)") to allow an advanced user to opt-in to wrapped-key encryption on pre-M devices. Enabling this option will attempt to create the wrap key and must be followed by a manual DB migration using the in-app "Migrate DB" flow. Users are strongly advised to export a backup before migrating.
   - If continued support is required, keep only wrapped keys (never raw bytes) and ensure `key_wrapped_b64` storage is protected by `MODE_PRIVATE` and rotated if wrap key changes.

4. PBKDF2 / backup KDF:

   - Current iterations (300,000) are acceptable for modern devices. Keep iteration count configurable and consider increasing over time as devices become faster.
   - Use a unique random salt (>=16 bytes) per backup and include salt in the backup header. Keep the salt with the encrypted payload.

5. AES-GCM parameters:

   - Use IV size 12 bytes and tag length 128 bits.
   - When using keystore-backed keys, let the keystore produce the IV (do not pass an IV) if `setRandomizedEncryptionRequired(true)` is used.

6. Memory hygiene:

   - Zero passphrase and intermediate byte arrays in finally blocks (already implemented in `EncryptionUtil`).
   - Prefer `CharArray` for passwords where possible and zero immediately after use.

## Key Rotation

- `EncryptionUtil.rotateKey()` exists to:
  1. Decrypt stored `db_pass_enc`,
  2. Delete old keystore entry (if present),
  3. Create a fresh key, and
  4. Re-encrypt and write `db_pass_enc`.

- Recommended rotation policy: rotate on major app upgrades or when device keystore properties change (StrongBox availability, OS update). Expose a safe user-initiated rotation option in settings that builds a backup-first flow.

## DB encryption migration & rollback (high-level plan)

Two safe strategies (choose one):

A) In-place SQLCipher rekey (best when DB is small and SQLCipher native is stable on target devices)

   1. Open plaintext DB.
   2. Generate secure passphrase and persist encrypted with keystore (`db_pass_enc`).
   3. Use SQLCipher `PRAGMA rekey = 'newpass'` to apply encryption.
   4. Verify integrity and replace DB file atomically.

   Rollback: if rekey fails, restore DB from pre-operation backup copy.

B) Export-import migration (safer, recommended for first rollout)

   1. Export data to an *encrypted* backup file (user-supplied backup password).
   2. Create a fresh encrypted DB using SQLCipher with a keystore-derived passphrase.
   3. Import backup into new DB and verify.
   4. Replace DB after user confirmation.

   Rollback: Keep the exported backup available until user confirms migration success.

Notes:

- Because keystore access may fail (user change, factory reset), include a migration UX that requires the user to keep a password-based backup as fall-back recovery.
- Do not automatically encrypt existing users' DB without explicit user consent and a robust recovery path.

## Operational checklist before enabling SQLCipher by default

 - Confirm keystore usability across supported device range (test on API 23..latest).
 - Validate `rotateKey()` on hardware-backed and software keystores.
 - Implement and test export/import migration (option B) end-to-end.
 - Add UX to prompt for backup creation before migration.
 - Add monitoring for failed key operations; record redacted telemetry only if user consented.

## Tests to add (manual + CI)

 - Device matrix test: key generate, encrypt/decrypt using keystore-backed key.
 - Pre-M RSA wrap/unwrap test and persistence behavior.
 - SQLCipher rekey test and export-import migration test.
 - Backup import failure and recovery scenarios (wrong password, truncated file, corrupted salt/header).

## Next steps (recommended)

1. Create `docs/ENCRYPTION_AUDIT.md` (this file) and review with a security-minded engineer.
2. Implement any small code changes needed to `EncryptionUtil.createSecretKey()` per recommendations (non-breaking; keep try/catch for StrongBox and randomized IV flag).
3. Implement the safer export-import migration workflow and test it across emulators and physical devices.
4. Decide policy for pre-M devices (disable default encryption or keep wrapped-key fallback).

---

If you want, I can now:

- Update `EncryptionUtil.createSecretKey()` to explicitly include the recommended builder attributes, keeping all existing compatibility guards, or
- Implement the export-import migration flow in `data/BackupImporter.kt` + `AppDatabase` migration helper.

Tell me which to implement next.
