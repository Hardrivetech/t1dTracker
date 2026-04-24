Release checklist — t1dTracker

1) App Signing & Keystore
   - Create and securely store a release keystore.
   - Add signing properties to CI as encrypted secrets (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD).
   - Consider enabling Google Play App Signing and upload the original key securely.

2) Privacy & Legal
   - Finalize a privacy policy document and host it at a stable HTTPS URL.
   - Add the privacy policy link to the Play Console listing.
   - Confirm data deletion and export flows meet local regulations (GDPR, CCPA as applicable).
   - A draft privacy policy is included in `PRIVACY_POLICY.md` in the repository; host a public URL before publishing.

3) Backups & Recovery
   - Validate backup export/import flows across devices and Android versions.
   - Test pre-migration backups and restore flows on a clean device.
   - Document recovery steps in the app support page.

4) Security & Audits
   - Run dependency vulnerability scans (Dependabot enabled).
   - Perform static analysis (detekt / ktlint) and review warnings.
   - Run CodeQL analysis (workflow included) and review findings before release.
   - Consider a 3rd-party security audit for crypto/key management.

5) Testing
   - Add unit tests and instrumentation tests (use in-memory Room for unit tests, emulator/physical devices for connected tests).
   - Run a beta/internal track rollout and collect feedback.

6) Play Store Preparation
   - Prepare store listing: app description, screenshots, feature graphic, icons, privacy policy, content rating.
   - Create an internal release track and test the update flow.

7) CI/CD
   - Keep CI green: run ktlint, detekt, unit tests on PRs.
   - Automate release bundle builds and signed artifacts for distribution.
