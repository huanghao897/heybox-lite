# Watch Crash Recovery

## Scope
- Fix the reported ListView header/footer index failure without moving the feed toolbar.
- Reduce optional offline-cache allocation; keep the first ten comment groups.
- Install Java crash capture at Application startup, including secondary processes.
- Persist bounded, redacted reports before terminating an unsafe process.
- Upload reports automatically to the existing diagnostics endpoint. A failed upload
  remains queued for the next launch; only a successful response acknowledges it.
- The request's `source` explicitly distinguishes `manual`, `auto_crash`,
  `auto_exit`, `auto_error` and `crash_test`. Outbox reports retain their source.
  Old unmarked records remain `unknown`; a manually uploaded report containing a
  historical crash is still manual. The admin list prioritizes real automatic
  crashes and supports filtering by source.
- Keep recovery actions visible without scrolling through a stack trace. Reuse the
  current theme and support both round and rectangular watches.
- Provide a confirmed "崩溃测试" command under Settings / Content and cache / Maintenance.
  It deliberately throws once on the main thread and labels its report as a manual test.
- On Android 11+, collect available native-crash, ANR and low-memory exit metadata
  on the next launch. Do not claim that SIGKILL or native faults are interceptable.

## Boundaries
- Java/native Views, existing diagnostics protocol, no new runtime dependencies.
- No version or signing-key change; no unrelated UI or official API changes.
- Automatic reports exclude credentials and content dumps.
- The uploaded Huawei diagnostic has no crash stack. It may have been manually
  exported; neither the cause nor the absence of a crash can be inferred from it.

## Verification
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
- Unit tests cover bounded stacks, causes, redaction, persistent queue acknowledgement
  and bounded offline snapshots.
- Device tests exercise fixed list rows and recovery layout without real credentials.
  Queue delivery and retry logic are covered by JVM tests. Run device tests only
  when an authorized device is connected.
- The device-only layout/list suite can be selected with instrumentation argument
  `-e suite crash_recovery`; it does not invoke the pre-existing account tests.
- Confirm the release certificate and unchanged version metadata.

## Verification Results (2026-09-25)
- Android: 236 unit tests passed; `lintDebug` completed with 0 errors and 81
  existing warnings. Release and device-test APKs compiled.
- Release remains version 2.15 / code 218. Certificate SHA-256:
  `fbd5642c3c1b5882545f6f1227cf2dc38a54bcd18609203935eedbef408d1382`.
- Backend: 104 tests passed, including source migration, upload classification,
  authenticated filtering and automatic-crash priority.
- No Android device is connected. Round-watch touch bounds, actual process
  termination, crash-screen launch and real automatic delivery still require
  device verification; no production crash reports were fabricated.
- Huawei report 40 is a diagnostic export without a crash stack. The supplied
  Android 10 environment cannot use the Android 11 exit-history API.
