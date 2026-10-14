# BumBoard — instructions for code agents

Read [HANDOFF.md](HANDOFF.md) in full before changing anything.

- Never edit `upstream/fcitx5-android` in git. Change upstream behaviour only through
  `patches/` (keep them small; prefer putting code in `app/` or `core/` and adding a one-line hook).
- Push straight to `main`. Builds run in GitHub Actions (`.github/workflows/build-bumboard.yml`);
  there is no Android SDK in the dev container. You can run `data/build_data.py`, `data/test/run.sh`
  and the `core` JVM tests locally.
- Check licences before adding any data or library (see LICENSES.md): no GPL code or data in the APK,
  no non-commercial data.
