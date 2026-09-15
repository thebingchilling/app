# Browser (fork of Titanium Browser for Android)

This is a fork of [Titanium Browser for Android](https://github.com/jqssun/android-titanium-browser)
(itself based on [Vanadium](https://github.com/GrapheneOS/Vanadium) by GrapheneOS), with two
changes on top of upstream Titanium:

1. **DevTools**: Titanium enables Google's own `kAndroidDevToolsFrontend` runtime feature to gate
   the on-device "Inspect Element" context menu entry. This fork removes that dependency: the
   "Inspect Element" entry is always shown (`shouldShowDeveloperMenu()` unconditionally returns
   `true`), and clicking it goes straight to the native
   `ContextMenuNativeDelegateImpl::InspectElement()` -> `DevToolsWindow::InspectElement()` call,
   which does not itself depend on `kAndroidDevToolsFrontend` (see
   `chrome/browser/android/context_menu/context_menu_native_delegate_impl.cc` upstream). This is
   the closest faithful re-creation of Kiwi Browser's on-device DevTools button achievable without
   Kiwi's exact original `AppMenuBridge.java`/`FixDevToolsWindow.java` source, which is no longer
   recoverable from any available Kiwi Browser mirror (Kiwi Browser was archived in January 2025;
   see `patch.sh` for the full explanation).
2. **CRX/ZIP import**: adds a new "Install extension file" button next to "Load unpacked" on
   `chrome://extensions`, for installing an already-packed `.crx`/`.zip` extension file picked
   from local storage. Upstream Chromium/Titanium only supports installing an *unpacked* directory
   via a Storage Access Framework folder picker, or a packed file via HTML5 drag-and-drop (which
   doesn't work from an Android file manager onto a WebUI page). This reuses the exact same
   `ui::SelectFileDialog` flow `loadUnpacked()` already uses (just `SELECT_OPEN_FILE` instead of
   `SELECT_EXISTING_FOLDER`) and the exact same `.crx`/`.zip` install dispatch
   `installDroppedFile()` already uses. See `patches/crx_import/`.

Both changes are implemented as additions to `patch.sh`, in the same style as Titanium's own
patches (`sed`/`perl` transformations applied to a real Chromium checkout at build time), plus two
new source fragments under `patches/crx_import/`.

## Important: these patches are unverified

They were written by directly reading Titanium's and Chromium's real source at the exact pinned
version (via `vanadium/args.gn`), not guessed - but they have **not been compiled**. Building
Chromium for Android needs ~150GB+ disk and 32GB+ RAM, which is far more than this development
environment (or a standard GitHub-hosted Actions runner) has available. Expect the first real
build to surface compile errors that need a round or two of fixing.

## Building

**Do not use `ubuntu-latest` for a real build** - GitHub's standard hosted runners have ~25GB
usable disk and 7GB RAM, nowhere near the ~150GB disk / 32GB+ RAM a Chromium Android build needs.
You need a **self-hosted runner** (e.g. a rented cloud VM with those specs, with the GitHub Actions
runner agent installed and registered to this repository).

To build:
1. Register a self-hosted runner with ~150GB+ disk and 32GB+ RAM to this repository
   (Settings -> Actions -> Runners).
2. Supply `base64`-encoded `LOCAL_TEST_JKS`/`STORE_TEST_JKS` secrets (a `local.properties` with
   `keyAlias`/`keyPassword`/`storePassword`, and a keystore `.jks`) under
   **Settings > Secrets and variables > Actions**, matching what `common.sh`'s `set_keys()` expects.
3. Go to **Actions > Build Browser**, **Run workflow**, and enter your runner's label (or
   `self-hosted` if that's how it's registered).

This produces signed `.apk`/`.aab` files attached to a new GitHub Release, the same way upstream
Titanium's own CI does.

---

The rest of this README is Titanium's own (see [upstream](https://github.com/jqssun/android-titanium-browser)
for the authoritative, up-to-date version):

A secure and fully open-source, Chromium-based web browser with support for extensions, based on
[Vanadium](https://github.com/GrapheneOS/Vanadium) by [GrapheneOS](https://github.com/GrapheneOS).

### Installing Extensions

For Chrome extensions, navigate to [Chrome Web Store](https://chromewebstore.google.com/), enable
**Desktop site** using the menu button in the top right corner, and proceed as normal.

You can also load an unpacked extension manually by navigating to the **Manage extensions** page or
`chrome://extensions`. Enable **Developer mode**, select **Load unpacked** (folder) or **Install
extension file** (`.crx`/`.zip`, added by this fork). Manifest V2 (MV2) and MV3 extensions are
supported.

### Debug URLs

To view and access the debug URLs, use `chrome://chrome-urls`. For **Experiments**, use
`chrome://flags`.

## Credits

This fork would not have been possible without [Titanium Browser for Android](https://github.com/jqssun/android-titanium-browser)
and [Vanadium](https://github.com/GrapheneOS/Vanadium) by [GrapheneOS](https://github.com/GrapheneOS),
and the archived [Kiwi Browser](https://github.com/kiwibrowser) project, whose on-device DevTools
button this fork's DevTools change is inspired by (its own exact implementation is no longer
recoverable - see `patch.sh`). All credit for the underlying browser goes to those projects' authors
and contributors.
