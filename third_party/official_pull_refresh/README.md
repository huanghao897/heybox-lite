# Official Pull-to-Refresh Animation

`app/src/main/res/raw/official_pull_refresh.lottie` is the unmodified animation
requested by the user, extracted from the locally provided Xiaoheihe APK.

- APK: `xiaoheihe_1.3.379.apk`
- Resource table: `raw/header_refreshing` -> `res/3rt.lottie`
- Identical Flutter asset: `packages/heybox_ui/assets/lotties/pull_refresh.lottie`
- SHA-256: `3637997f64fe111dcf884ef13acdaabfa550ceaff5319aa7de09d784b3efb7fd`
- Container: 6,084-byte dotLottie ZIP; `animations/data.json`, 200x200, 60 fps,
  frames 15-137. All five assets are vector precompositions, not external images.

The local `HeyBoxHeaderV2` implementation uses a 70 dp header, a 50 dp animation,
starts playback past a 0.4 pull fraction and loops while refreshing.
`SmartRefreshInitializer` specifies a 250 ms return duration.
Lite preserves these proportions using the user's UI scale, uses Material 3's
nested-scroll gesture handling, and stops playback when its page is not active
or motion is disabled. Small viewports cap the header at 28% of available height
and keep the same 70:50 header/animation ratio. No official network/sign-in
implementation is imported.

The artwork remains the property of its original creator; this attribution does
not imply that Xiaoheihe endorses Lite or grants a new license for the artwork.
