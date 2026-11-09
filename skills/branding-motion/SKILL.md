# MYLO Branding / Splash Motion

## Locked art direction

- Black background.
- Use the user's exact anime-girl character artwork, crimson circular emblem and three wave marks at the lower right.
- The character enters from above with a deliberate thread/web-like motion, swings and settles into the emblem.
- Final splash is logo-only: no MYLO wordmark.
- Preserve the original character, silhouette, colors and composition. Do not invent a replacement character or use the inherited metallic headphone logo.

## Current repository state

- Approved composition: `app/src/main/branding/mylo-logo.webp.base64` (lossless WebP source in Git).
- Build integration: `:app:generateMyloSplashArtwork` and `:tv:generateMyloSplashArtwork` independently validate the RIFF/WEBP payload and generate a module-local `R.drawable.mylo_logo`; do not decode it at runtime or wire both generated directories into one module.
- Runtime: `app/src/main/java/com/music/bitchord/MyloSplash.kt` animates the complete flattened artwork from above with a restrained settling swing, black background, logo-only framing and system animator-duration-scale handling.
- The source is one flattened composition. Separate character/emblem layers are not available; preserve the original image rather than tracing or inventing replacements.
- `app/src/main/assets/Logo.svg` and `LogoTransparent.svg` are inherited metallic headphone artwork and must not be used for this splash.
- The phone and TV modules each compile the shared splash composable but must consume only their own generated drawable. The TV task reads the same encoded source from the app module and writes to `tv/build/generated/myloSplash/res`; never add the app generated directory to TV's resource sources, or Android resource merging fails with duplicate `mylo_logo`.

## Implementation guidance

1. Keep emblem and character as separate layers where source artwork allows it.
2. Use Compose/native animation with a deliberate spring/arc and thread motion; avoid random particle effects, generic template transitions or an animated wordmark.
3. Keep the first frame black and the animation short enough not to delay playback/navigation.
4. Respect animator duration scale/reduced motion and lifecycle cancellation; do not run an animation indefinitely.
5. Verify the app's cold start, warm start, reduced-motion behavior and startup performance. Use screenshots/device recording for visual verification, not compilation alone.
