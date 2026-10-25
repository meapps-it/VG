# V e G 0.8.4 — Android appearance

Scope: existing `vg` APK flavor, package `com.vg.gestionale.seconda`, versionCode 20.
The native-only variant and the PWA's content, data and appearance settings are unchanged.

## One text scale

The APK bundles its appearance script, so the controls do not depend on deploying
new web assets or waiting for a service worker update. It mounts the single
`Aspetto e caratteri` card inside `#sec_impostazioni`; there is no extra Android toolbar.
The slider covers 75–150 in steps of 5, defaults to 100, updates immediately and
resets to 100 without closing settings. `vg_android_settings/font_percent` is the
single source of truth, retaining the preference from the previous APK.
Android SharedPreferences persists across process restarts and package updates.

WebSettings.textZoom is set to round(system fontScale × chosen percentage).
No CSS font-size multiplier, page zoom or scaledDensity multiplier is added.
CSS text autosizing is pinned to 100% to prevent a second scale. Legacy web
font-settings requests are suppressed in this APK; cached legacy styles and
per-node overrides are removed. Native dialogs use a configuration context with
system fontScale × chosen percentage and Android's own sp rendering. Android 14+
nonlinear sp scaling is handled by Android in native widgets; WebView's percentage
textZoom is a linear approximation, not an exact implementation of that curve.

Buttons wrap and grow, inputs retain touch height, settings and dialogs scroll,
and narrow layouts switch to one column at large effective text sizes. This
cannot prove every possible combination of catalog content and accessibility
settings fits: device review is still required at the user's chosen font size.

## Font transfer and honest fallback

`system-ui` and WebSettings.sansSerifFontFamily alone cannot guarantee a Samsung
Galaxy Store/One UI font. The VG flavor explicitly clears the base theme's fontFamily with @null (omitting
the item would merge the forced family back in from the main resources).
The APK does not hard-code Roboto or guess private
Samsung font paths and does not use reflection or hidden Android APIs.

On API 31+ a themed native TextView supplies its Typeface. TextRunShaper shapes
Latin text using that typeface; PositionedGlyphs.getFont returns the actual
font selected by Android, and Font.getBuffer supplies its bytes. Four style
variants are loaded through WebView's FontFace API with ArrayBuffer sources.
Repeated buffers are deduplicated by SHA-256; a variable font is transferred once,
not once per style. Variable font axes are retained.
The bytes stay in this app's trusted web document; they are not written to disk,
uploaded, or added to the APK. Fonts are refreshed when the Activity resumes.
SVG graphics and icon-font elements are excluded from text font overrides.

If Android does not expose the selected Samsung font through the native renderer,
this mechanism cannot invent it. Font collections requiring a nonzero TTC index,
fonts larger than 8 MB per file (16 MB total unique data), unavailable buffers, Android 8–11, and WebViews
that reject the font receive an explicit fallback status in settings. The fallback
uses the WebView's system font and does **not** claim the Samsung font is applied.
Even a successful transfer proves only that the native renderer's font loaded;
it does not prove that an OEM's user-selected font was returned. A Samsung device
with a visibly distinctive font must be checked before claiming One UI success.

The preference bridge uses AndroidX WebKit's origin allowlist, a main-frame check
and the existing /VG path check. No unrestricted JavascriptInterface is added.
Old WebViews without WEB_MESSAGE_LISTENER show a disabled control and an update
message, rather than pretending persistence worked. Document-start injection is
feature-checked, with a page-finished fallback.

Public API references:
- https://developer.android.com/reference/android/graphics/text/TextRunShaper
- https://developer.android.com/reference/android/graphics/text/PositionedGlyphs#getFont(int)
- https://developer.android.com/reference/android/graphics/fonts/Font#getBuffer()
- https://developer.android.com/reference/android/webkit/WebSettings#setTextZoom(int)
- https://developer.android.com/reference/androidx/webkit/WebViewCompat

## Verification

The workflow runs VG unit tests, lint, compilation, the bundled script's browser
tests, existing management UI/photo/action regression tests, and an Android 35
emulator test. The device test checks native font loading into a real WebView,
actual text growth at both endpoints, preference retention after Activity recreation,
reset, native-dialog scale, icon preservation, and rejection of untrusted origins.
Separate instrumentation invocations save 145%, force-stop the app process,
then verify the saved percentage and WebView zoom in a newly launched Activity.
Physical Samsung and OEM font changes require a real device. CI evidence must be consulted: tests listed here are not a claim
that a particular run succeeded.

Delivery signing uses the existing private signing config outside the repository.
The CI test key is never presented as the user's delivery identity. Compare the
new APK and previous private-signed 0.8.3 using ci/VerifyVgUpdate.java to validate
same package, same certificate and increased versionCode. This proves update
identity compatibility; installation on the user's handset remains unverified.
