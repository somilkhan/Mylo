# Scrobble shared-control audit

## Verdict and scope

**IMPLEMENTED — narrowly scoped shared-control fixes, ready for the parent's Android verification.** The controls retain Mylo's readable theme fills and compact visual proportions. Glass is minimal: only the existing rim on an enabled pressed primary pill or enabled active icon. No blur, backdrop capture, lens, gradient, dependency, row-glass treatment, resource change, or public/default callsite migration was added.

This unit began on `feat/scrobble-screen` at localization checkpoint `bd5a4fb6` in `/home/ubuntu/Mylo`. The parent separately reports all 19 resource-folder/placeholder checks and actual Android AAPT2 passing for that checkpoint; those results are not evidence that this newer UI compiles or its Compose tests pass. Build/test execution, commits/pushes, resources, MainActivity, integration/settings and all Stats implementation remain other owners' scope.

Only these repository files were changed by this unit:

- `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleUi.kt`
- `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleHost.kt`
- `app/src/test/java/com/music/bitchord/ui/scrobble/ScrobbleControlsTest.kt` (new)
- `docs/audits/SCROBBLE_SHARED_UI.md` (this report)

## Findings and corrections

| Before | After | Why |
| --- | --- | --- |
| Compact primary/secondary pills used a 36dp clickable height. | Stable outer targets have 48dp minimum width/height; compact fill stays 36dp, centered inside a 6dp band above/below. Standard pills remain at least 52dp high. | The entire accessible target, including the band outside the fill, accepts taps without making the visual control heavy. |
| Pill press scaling transformed the clickable surface itself. | Only the inner visual surface transforms; the outer target is never scaled. | Hit bounds and layout remain stable during feedback. |
| `pressScale` ignored the host, app and Android system motion preferences. | `LocalScrobbleReduceMotion` and `scrobbleReduceMotion(requested)` combine the host flag, `AppSettings.reduceAnimation` and Compose's observed Android `MotionDurationScale`. | A caller requesting reduced motion cannot be overridden by a default-false child; standalone controls also honor app/system preferences. |
| Generic `PressInteraction` also animated Enter/D-pad activation. | Focused controls return unit scale immediately; disabled/busy shared controls also draw at unit scale. | Compose clickable emits keyboard press interactions too. Conservatively suppressing transforms whenever keyboard/programmatic focus is retained prevents keyboard-triggered movement. |
| Icon press and persistent active state both shrank the circle to `.92`. | Icon pointer press is `.97`; pills use `.96`. Active state is a theme-paired fill/ink change, not a persistent transform. | A subtle press communicates input without collapsing the icon or turning selection into motion. |
| Press easing used the default tween. | Press is 90ms, release 160ms, using the documented strong ease-out cubic `(0.23, 1, 0.32, 1)`. Active fill/ink transition is 180ms. | Immediate, crisp feedback and short state transitions; no spring/bounce or layout animation is introduced. |
| Busy primary pill replaced its only label with an unnamed spinner and could shrink to spinner width. | The real label remains measured but visually hidden; the parent exposes its supplied localized label plus indeterminate progress semantics and is disabled while busy. Spinner/hidden label child semantics are cleared to avoid duplicate announcements. | Assistive technology retains the action's identity and progress state; callers do not need a new label/resource contract. |
| Active icon state had no accessibility state. | Existing Button role/name/disabled semantics are retained, with `selected = active`. | An open/selected control's state is discoverable without relying only on color. |
| Shared controls had flat fills with no constrained glass connection. | Existing `liquidGlassRim(CircleShape)` is used only on currently pressed actionable primary pills and enabled active icons; all normal fills remain. | The existing rim is a stroke-only enhancement. When glass is off or API support is absent it returns the unchanged modifier, preserving the same theme-safe fallback. |

The required design-engineering and motion-review references were read. Color transitions can remain under reduced motion because they communicate state without movement. Focus suppression also intentionally removes pointer scale while a control retains programmatic/keyboard focus; this is a conservative accessibility trade-off, not a new keyboard interaction policy.

## Inspected integrations, unchanged

`LiquidGlass.kt` owns glass support and the no-op rim fallback. The floating tab bar/`GlassSelectionPill` already have their own reduced-motion and selection policy. `FrostedTopBar` owns native floating chrome and existing Haze/opaque treatment. `IosMenuPopover` owns anchoring, Back handling and its app/system motion checks. Those files were inspected but not edited. No alternate glass pipeline was created.

Generate's mode tiles already call `pressScale(interaction)` and automatically inherit the policy with their unchanged signature. Generate/Playlist primary, secondary and icon callsites retain their existing contracts, including compact Add/Retry, busy generation/add, count buttons and menu anchors. The Host keeps its ViewModel, shared-transition scopes, Haze state and callbacks; only policy provision and effective motion forwarding changed. Stats can provide the same internal local from its separately owned Screen:

```kotlin
val effectiveReduceMotion = scrobbleReduceMotion(reduceMotion)
CompositionLocalProvider(LocalScrobbleReduceMotion provides effectiveReduceMotion) {
    // Existing screen content; pass effectiveReduceMotion to its motion-aware children.
}
```

All implementation and test changes are under Android `app/src`; no Android imports were added to desktop/shared modules.

## Focused actual Compose fixtures

`ScrobbleControlsTest` uses the existing Android Compose rule with Robolectric, a real `ComponentActivity`, SDK 35 and native graphics. It renders production controls or the production `pressScale` helper; it has no network, ViewModel, artwork, recreated screen or screenshot pixel fabrication. The eight focused cases cover:

1. Compact primary/secondary 48dp targets, including taps outside the 36dp visual fill.
2. Busy primary accessible name, indeterminate progress, disabled interaction and unchanged bounds/width.
3. Disabled secondary/icon callbacks and minimum targets.
4. Active icon selected semantics and stable window anchor in light theme with glass enabled.
5. `.97` pointer press/release and unchanged outer bounds.
6. Host-local reduced-motion unit scale.
7. App reduced-motion changes stopping an already held press.
8. Focused Enter activation invoking the callback without a transform.

The fixture saves/restores the shared app preference. It does not write screenshot files or assert invented color/pixel values. Existing full-screen screenshot classes remain available to the parent for broader visual regression review.

## Verification and limits

- **PASS — static only:** Kotlin PSI parsing of both implementation files and the new fixture using the cached Kotlin 2.0.20 compiler environment; no syntax errors. This does not resolve Compose APIs/types or run the Compose compiler.
- **PASS — static only:** scoped `git diff --check`; ownership/callsite review; no `.92` icon scale remains; motion durations are within the assigned press/state ranges.
- Cached Compose bytecode confirms clickable emits press interactions for keyboard activation, and Android Compose's `MotionDurationScaleImpl` exposes its scale as observable state. This supports the implementation choice, not device-behavior verification.
- **NOT VERIFIED:** Android compilation, these eight Compose/Robolectric test results, full Gradle tests/builds, CI for the changed tree, screenshots, TalkBack announcements, live system-setting changes, native GPU rim rendering, large-font/long-translation layouts and physical touch/keyboard behavior. Parent owns the real Android run. No Gradle command was run by this unit.
- **NOT COMMITTED by this unit:** no commits/pushes or build/resource/signing edits were performed. Other agents' changes were left intact.

Recommended parent target: `com.music.bitchord.ui.scrobble.ScrobbleControlsTest`, then the project's prescribed full Android suite/build. Native device checks should include reduced motion toggled during a held press, TalkBack while Generate/Add is busy, active sort menus, keyboard/D-pad Enter, glass on/off and unsupported API fallback. Android tests and device acceptance remain explicit gates, not implied successes.
