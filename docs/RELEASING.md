# Releasing Mylo

Releases are built and signed by `.github/workflows/release-signing.yml`. Pushing a tag
creates the GitHub release; the workflow can also be run by hand from the Actions tab
(`workflow_dispatch`), which builds and verifies the APKs but does not create a release.

## Cutting a release

1. Set `bitchordVersionName` in the root `build.gradle.kts` (currently `1.9.1`).
2. Merge the change to the release branch.
3. Create and push the tag. It must be exactly `v` plus that version name:

   ```bash
   git tag v1.9.1
   git push origin v1.9.1
   ```

   The workflow fails with a clear error if the tag and `bitchordVersionName` differ.
   A tag containing a hyphen (for example `v1.9.1-beta.1`) is marked as a prerelease.
The in-app updater reads `releases/latest`, which skips prereleases, so a prerelease tag is not offered as an update.
   Note that a suffixed tag such as `v1.9.1-mylo.1` is rejected by the tag check, since
   the version name has no suffix; bump `bitchordVersionName` to change the version.
4. The job builds the prod flavor of the phone app, the TV app and the Wear OS app,
   renames the APKs, checks every APK is signed by the MYLO release key, and creates
   the GitHub release with `--generate-notes`.

Tag pushes from forks do not run this workflow, because forks do not receive repository
secrets. Only the release job has `contents: write`; the workflow default is read-only.

## Secrets

Set under the repository's Settings > Secrets and variables > Actions.

Used by `release-signing.yml`:

| Secret | Purpose |
| --- | --- |
| `MYLO_RELEASE_KEYSTORE_BASE64` | Base64 of the release `.jks` keystore |
| `MYLO_RELEASE_STORE_PASSWORD` | Keystore password |
| `MYLO_RELEASE_KEY_PASSWORD` | Key password |
| `MYLO_RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `LASTFM_API_KEY` | Last.fm API key, passed to the release build |
| `LASTFM_SECRET` | Last.fm shared secret, passed to the release build |
| `LISTEN_TOGETHER_SERVER` | Listen Together server URL, passed to the release build |

Used only by the CI workflows (`check.yml`, `tv.yml`, `android-ci.yml`), not by the release:

| Secret | Purpose |
| --- | --- |
| `DEBUG_KEYSTORE_BASE64` | Shared debug keystore, so CI builds are signed consistently |

The Last.fm and Listen Together values are read from the environment when Gradle runs.
If they are unset, the Last.fm keys are empty and Listen Together asks for a server
address on first use. The workflow does not stop for them, so check them before tagging.

## What the in-app updater expects

`AppUpdateChecker.kt` polls `releases/latest` on `somilkhan/Mylo` and compares the tag
(with the leading `v` removed) to the running `VERSION_NAME`.

It downloads the first asset whose name starts with `UPDATE_APK_PREFIX` (`Mylo-` in
`app/build.gradle.kts`) and ends in `.apk`:

- The universal APK, `Mylo-<version>.apk`, is preferred.
- Otherwise the ABI split for the device, `Mylo-<version>_<abi>.apk`
  (`arm64-v8a`, `armeabi-v7a` or `x86_64`).

The TV and Wear APKs use `MyloTV-` and `MyloWear-`, which do not match `Mylo-`, so they
are never offered to the phone app. The workflow renames Gradle's output to these names
(`rename_module` in the workflow). If the prefix changes in a build file, change the
rename call in the workflow as well.

## Duplicate CI jobs (not consolidated yet)

Some checks run twice on the same push or pull request. The check names are kept as they
are, because branch protection may refer to them. Consolidating them is a separate change.

- `check.yml` > **Android** and `android-ci.yml` > **Android unit tests (optional APK build)**
  both run Android unit tests.
- `web.yml` > **Web · typecheck, test, build** and `web-ci.yml` > **Web typecheck, tests and build**
  both run the web app's typecheck, tests and build.

## Device-test checklist: scrobble UI

Run on a phone with a Last.fm account before tagging:

- [ ] The Scrobble floating action button sits where the design says, clear of the nav bar
      and the mini player, in both portrait and landscape.
- [ ] The Scrobble tabs switch, and each keeps its own scroll position.
- [ ] Stats show the correct counts and update after a scrobble.
- [ ] Generate creates a set of songs, and "save" hands it to Playlists, where it appears.
- [ ] The gradient toggle changes the background and survives an app restart.
- [ ] "Start mix" from a song starts playback of the mix.
- [ ] Connecting Last.fm and returning to the screen refreshes the connected state.
- [ ] Back from Scrobble closes the screen and does not leave the app or open a stale screen.
