# MYLO Branding / Splash Motion

## Locked art direction

- Black background.
- Use the user's exact anime-girl character artwork, crimson circular emblem and three wave marks at the lower right.
- The character enters from above with a deliberate thread/web-like motion, swings and settles into the emblem.
- Final splash is logo-only: no MYLO wordmark.
- Preserve the original character, silhouette, colors and composition. Do not invent a replacement character or use the inherited metallic headphone logo.

## Current repository state

`app/src/main/res/values/themes.xml` provides a black startup window. `app/src/main/assets/Logo.svg` and `LogoTransparent.svg` are inherited headphone artwork, not the requested identity. The exact anime artwork is not present among the currently identified repository assets; do not mark branding complete until the source art is available and integrated.

## Implementation guidance

1. Keep emblem and character as separate layers where source artwork allows it.
2. Use Compose/native animation with a deliberate spring/arc and thread motion; avoid random particle effects, generic template transitions or an animated wordmark.
3. Keep the first frame black and the animation short enough not to delay playback/navigation.
4. Respect animator duration scale/reduced motion and lifecycle cancellation; do not run an animation indefinitely.
5. Verify the app's cold start, warm start, reduced-motion behavior and startup performance. Use screenshots/device recording for visual verification, not compilation alone.
