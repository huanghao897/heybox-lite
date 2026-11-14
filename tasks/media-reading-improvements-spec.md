# Media and Reading Improvements

## Objective

Improve watch reading without changing official API contracts, account behavior,
version 2.19/222, or the existing uncommitted visual refinements.

## Agreed Scope

- Data saver keeps content-image frames and starts no request until the image is
  tapped. Avatars and official inline emoji remain automatic. Existing no-image
  settings remain separate. Cover taps must not accidentally open another page.
- Investigate intermittent scrolling interruptions in both touch and rotary paths;
  remove evidenced stalls and retain refresh, paging, and back gestures.
- Nested comments have a compact, distinct thread treatment while their content
  stays aligned with the top-level comment text. The final user feedback removes
  the new separator lines and keeps the rounded tonal background.
- Official emoji and recognized external blue links, including lottery links,
  survive parsing, asynchronous completion, and lazy-list reuse.
- The image viewer uses a Compose page, pager, controls, and gesture state. Keep
  bounded region decoding for long images rather than a full-height bitmap.
  Screen shape alone must not request the original. Preserve GIF, save, zoom,
  dismiss, multi-image paging, and launch contracts.
- Cache cleanup measures actual reclaimed bytes and includes owned disposable
  files. Preserve credentials, settings, pending crash uploads, and saved offline
  content. Installed APK/native library size is not removable cache.
- Update the existing cnwearoverlay runtime to upstream 1.2.0, retaining Lite's
  input ownership and feedback rate limit; do not install an Xposed module or
  claim physical-device acceptance without a connected watch.

## Implementation Boundaries

Use existing Kotlin/Compose, Java media/cache helpers, and JUnit/Robolectric.
Production and tests live under app/src/main and app/src/test respectively.
No added dependencies, server changes, emulator, shutdown, version bump, or
artifact publication. New Java/Kotlin files stay below 500 lines.

Follow existing four-space indentation and focused ownership, for example:

```kotlin
internal data class ImagePageState(val loading: Boolean, val failed: Boolean)
```

## Plan

1. Preserve the dirty-worktree baseline; inspect settings, loaders, gestures,
   rich text, and storage owners.
2. Add independent image-request gating and settings; regression-test image
   requests, stable placeholders, and avatar/emoji exceptions.
3. Repair rich-content ownership and nested-comment presentation.
4. Migrate viewer shell and state into focused components, retaining tested
   bitmap/region decoding primitives.
5. Correct cache accounting and cleanup; audit touch/rotary scroll stalls.
6. Integrate, run tests and lint, build signed Release, inspect scoped diff,
   then commit/push only this task's changes.

## Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
git diff --check
```

## Acceptance and Evidence

Request-count tests must show no content-image fetch before a tap and no duplicate
fetch on recomposition. Viewer tests cover round/square control bounds, zoom reset,
long-source disposal, asynchronous cancellation, and no automatic original fetch.
Rich-text tests cover delayed emoji resolution and non-game official links.
Storage tests create real owned files and verify retained user data and actual
reclaimed bytes. Scroll regression tests cover flings away from the refresh edge
and accumulated rotary events.

Automated behavior/rendering tests are not watch GPU performance measurements.
No physical watch is attached for this work; actual smoothness, native decoder
behavior, and vendor touch input remain device acceptance items, not proven claims.

## Progress

- [x] Confirmed data-saver scope with the user.
- [x] Preserved 21 pre-existing changed/new files outside the repository.
- [x] Read project ownership and migration constraints.
- [x] Implement the six improvements and their focused regressions.
- [x] Complete scoped full-suite and focused integration verification; keep unrelated UI failures separate.
- [x] Build and verify fixed-signer Release.
- [x] Review the scoped staged tree, leaving unrelated UI edits unstaged.

Delivery is confirmed against the remote branch HEAD after commit/push, rather
than treating this local checklist as proof of publication. No APK is published.
