Fastlane skeleton for t1dTracker

This folder contains a minimal Fastlane skeleton and metadata placeholders to help automate Play Store uploads.

Notes
- The lanes are placeholders. Do not run without configuring the Play Store service account JSON and reviewing the lanes.
- Add the Play Store service account JSON to your repository secrets (e.g. `GOOGLE_PLAY_JSON`) or provide it via CI secrets.

Basic usage (local, after installing Fastlane):

  bundle install
  bundle exec fastlane prepare_release

Replace `prepare_release` with the appropriate lane when ready to build and upload.
