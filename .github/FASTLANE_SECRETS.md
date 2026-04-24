Fastlane / Play Store CI secrets

To enable automated uploads with Fastlane in CI, store the Play Store service account JSON as a repository secret.

Recommended secret names:
- `GOOGLE_PLAY_JSON` — the full JSON content of the Play Store service account key (base64-encoded or raw). Use Actions secrets or your CI secret store.

In GitHub Actions, you can reference it as:

```yaml
env:
  GOOGLE_PLAY_JSON: ${{ secrets.GOOGLE_PLAY_JSON }}
```

Do NOT commit service account JSON into the repository. Use CI secrets and restrict access to the CI runner.
