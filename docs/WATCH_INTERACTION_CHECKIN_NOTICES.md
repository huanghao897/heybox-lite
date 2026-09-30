# Watch interaction, check-in and announcement update

## Objective and acceptance
- Long-press the video preview in a post to open the existing playback settings.
  Playback-screen gestures, including long-press acceleration, are unchanged.
- Round-screen comments place body text below the identity row at full width;
  replies use shallow indentation. Copy, reply, images and likes remain intact.
- Hot/latest are explicit selected tabs. Author badges are locally drawn,
  theme-aware and shared between root comments and replies.
- Check-in shows real recent account runs from CheckinCenter, with existing
  share settings read/written on the service. Password recovery uses email
  verification, no WebView, no stored passwords, and bounded requests.
- Active announcements recur on cold launch until "do not remind" is chosen.
  Ordinary acknowledgement does not suppress them. Blank titles are absent;
  blank start/end mean immediate/no expiry. Existing version targeting remains.

## Structure and style
Java/native Views in app/src/main/java/com/ronan/heyboxlite; JVM tests alongside
existing app/src/test tests. Use focused view/flow/model helpers, existing
SettingsUi/CheckinCenterUi/ThemeTokens, four-space Java indentation, no added
dependencies. Keep the check-in server's signing implementation private.

## Plan and checks
1. Comment layout/badge/selector and in-post video settings dialog.
2. Check-in models, history, sharing and native password recovery; narrowly
   scoped server additions using existing authentication and email services.
3. Announcement display policy, optional title and schedule validation.
4. Run ./gradlew.bat testReleaseUnitTest lintRelease assembleRelease; run
   Python backend tests and JS syntax checks. Inspect diffs and signature.
5. Push owned changes and deploy test channel via SSH with backups; preserve
   the stable release, existing administrator identity and unrelated changes.

## Boundaries
Always preserve user changes, version 2.15/code 218 and the fixed certificate.
Never start an emulator, shut down the computer, publish a GitHub Release,
expose credentials/log bodies, or deploy the whole dirty CheckinCenter tree.
Ask before protocol-breaking or schema changes. Real device visual verification
and real account verification are reported separately from automated tests.
