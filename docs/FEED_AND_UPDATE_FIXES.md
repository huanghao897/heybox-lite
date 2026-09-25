# Feed metadata and test-update delivery

Date: 2026-09-25. App version remains 2.15 / 218.

## Scope

- Preserve the existing card layout, profile silent refresh, navigation and animations.
- Feed cards show a community name, not an arbitrary hashtag, in the lower-left row.
- Detail headers show the community image/name separately from hashtags.
- No additional official API requests are made to classify feed items.
- Initial feed loading uses a small inline status instead of a central spinner and
  an automatically expanded pull-refresh header. User-triggered refresh still works.
- This is a test-channel build, not a stable or GitHub Release publication.

## Verified metadata contract

The local official APK source was inspected at these declarations and call sites:

- `BaseLinkFeedsFlowItemDto.link_style`, `LinkStyle`, and the feed DTO mapper
  `gd.a`: 100 = picture/text post, 101 = web article, 102 = video.
  This field is distinct from `content_type`, which selects the feed card layout.
- `LinkStyleKt.getLinkStyleByLinkTag`: legacy `link_tag=1` opens a web article;
  27 and 28 open picture/text posts.
- `BBSLinkObj.is_article` and legacy news card constants.
- `FeedsFlowItemDtoDeserializer`: `content_type=-1` is a published-work container,
  not proof that the content is an article. `use_concept_type` is not used as an
  article identifier.
- Communities come from topics, explicit topic metadata, community protocol tags,
  or a feedback option containing both `topic_id` and `topic_name`.
  Generic tags are kept separate. Community icons survive local serialization.

`FeedMetadata` owns this parsing. `FeedItem` owns the cached display model. The
renderer does not guess identity from title length, cover shape or text content.
Unknown metadata remains a post; real response variants still need watch testing.

## Test-update root causes

1. A historical account-keyed approved record and a newer device-keyed record
   referred to the same full device ID/account, but the new record had no approval.
   The backend migration transfers that existing approval once, consumes the old
   approval and preserves the current token hash. It never grants access solely
   because a client claims the administrator account ID.
2. Startup update checking could run before device registration finished.
   `UpdateChecker` now waits behind the existing presence queue. The same path is
   used for manual checks. Failed registration is not reported as successful.
3. Download-button clicks previously persisted `testReleaseId` before installation.
   `InstalledApkFingerprint` now hashes the installed APK on a worker thread once
   per process/file revision. The backend compares `installedApkSha256` for
   same-version test releases. Cancelled/failed installation is not marked complete.
   Old clients retain their `testReleaseId` protocol.

Normal heartbeat spacing remains ten minutes. Retry state uses monotonic time;
failed registration is eligible after 30 seconds when another check is requested,
not through a new polling loop. No account Cookie or official token is added to
these requests.

## Verification

- `gradlew.bat testDebugUnitTest lintRelease assembleRelease` passed.
- 252 Android unit tests passed; Java class-size and UTF-8 checks passed.
- Release lint: zero errors, 83 warnings across the existing project.
- Backend: `python -m unittest discover`, 110 tests passed.
- Fixed signing certificate:
  `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`.
- A pre-existing API-19-only reflection exception in the vendored crown runtime
  prevented lint from passing with minSdk 14. Its exception handling now uses
  compatible types; haptic constants and scroll behavior are unchanged.

No emulator or physical-device UI test was performed. Still verify on a round and
rectangular watch: narrow community rows, cold launch, empty-list manual refresh,
returning to a cached list, article/post/video samples, and receiving/installing the
test update. The optional splash screen has not been redesigned in this change.

## Rollback

The changes are separate commits on `feature/ui-v2-native-view` and the admin
repository. Revert those commits rather than resetting unrelated work. The SSH
deployment retains old server files, an online SQLite backup and the previous
test-release publication flags. Stable publication flags and APKs stay unchanged.
