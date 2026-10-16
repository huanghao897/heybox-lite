# Official comment presentation references

Checked against the locally decoded official Android app 1.3.391:

- `ld/b.java` (`CommentFloorDecoration.kt`): a top-left triangle with a -45 degree
  white pinned label, rather than a standalone badge row.
- In the locally decoded official 1.3.379 APK, `triangle_left_top` resolves to
  `res/ueA.xml` on API 24+. Its path is `M0,0 L40,0 0,40` and its linear gradient
  runs from `(0,40)` to `(40,0)`, with `black_start=#ff464b50` and
  `black_end=#ff14191e`. The bitmap fallback copied previously is entirely
  transparent. Compose draws the verified vector geometry and gradient directly
  so that the corner is visible on API 21+ without adding a new bitmap.
  APK SHA-256: `d99a6b0ebd2ea993b5b96917b8f4a3c18072d604eaf45529428b241cc6119d45`.
- `ConceptLinkContentRender.java`, companion `a.a(TextView, String)`: an unfollowed
  author uses a small filled rectangle, plus icon and follow text; followed uses
  a subtle outline, muted text and no plus. `BBSUserSectionView.java` gives that
  control a 24dp height, 12sp text and compact icon spacing.

Compose keeps these presentation patterns with the app's existing grayscale theme
and watch sizing. It does not import the official networking or account logic.
