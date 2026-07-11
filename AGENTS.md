# Project Development Guidelines

These rules apply to the entire repository.

## Prefer platform solutions

- Prefer stable AndroidX, Jetpack Compose, and Material 3 components over custom navigation, animation, gesture, or lifecycle implementations.
- Before adding a dependency, verify compatibility with the project's Kotlin, Compose, AGP, compile SDK, and minimum SDK versions. Use official Android documentation and stable releases.
- Keep custom implementations only when the platform component cannot satisfy a documented requirement. Record the reason in code or project documentation.

## Navigation and page transitions

- Use `HorizontalPager` for a small, fixed set of peer-level tabs. Each page must fill and clip to the viewport so adjacent pages cannot draw over one another.
- Use Navigation Compose `NavHost` for hierarchical screens, detail pages, back stacks, and predictive back. Let Navigation Compose manage predictive-back progress.
- Do not implement page navigation by partially translating two full-screen `AnimatedContent` children or by manually stacking screens.
- Do not use `PredictiveBackHandler` directly when Navigation Compose already supports the navigation flow.
- Every animation container must emit one layout root. Never emit multiple sibling form fields or page roots directly into `AnimatedContent`, `Crossfade`, or another overlaying transition container.
- Use standard Compose motion primitives and easing. Keep navigation animations short and interruptible, and test rapid repeated tab selection.

## Compose performance

- Use Lazy layouts for long or data-dependent collections. Supply stable keys and `contentType` when practical.
- For a small fixed pager, precompose adjacent pages when it removes first-switch jank and the memory cost is acceptable.
- Avoid I/O, preference writes, parsing, bitmap decoding, or other blocking work inside pointer events, animation frames, layout callbacks, or composition.
- During drag-and-drop, update in-memory state while dragging and persist the final order only after the gesture settles.
- Avoid starting a new spring or coroutine for every pointer delta. Prefer direct offsets during the gesture and one settle animation afterward.
- Avoid unnecessary full-screen alpha, scale, blur, and dynamic shadow layers during page transitions. Prefer translation and viewport clipping.
- Keep parameters stable and avoid rebuilding collections in frequently recomposed paths. Use snapshot state, immutable UI models, or remembered derived values as appropriate.
- Do not compose all records inside one Lazy-list `item`. Emit records as individual lazy items.

## Performance builds and profiles

- When generating an APK without an explicitly requested build type, generate the `performance` APK by default. Generate a Debug APK only when the user explicitly requests it or when debugging requires it.
- Do not evaluate animation frame rate using the Debug APK alone. Debuggable Compose builds include tooling and runtime overhead.
- Maintain an installable, non-debuggable `performance` build type with R8 optimization and resource shrinking enabled.
- Keep Profile Installer enabled and ensure the optimized APK contains `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm`.
- The local `performance` build may use the debug signing key for device testing only. Production artifacts must use the production release signing configuration.
- Add or regenerate an app-specific Baseline Profile with Macrobenchmark when a suitable physical device or managed benchmark device is available. Cover startup, bottom-tab switching, opening grades and timetable, scrolling, and predictive back.
- Reflection-based libraries such as Retrofit must own their R8 rules in the corresponding library module's consumer rules. Preserve service annotations and suspend-function generic signatures, then smoke-test authentication with the minified performance build.

## UI correctness

- Validate forms at narrow phone widths and with long Chinese text. Text fields, captcha content, checkboxes, and buttons must remain inside their card and viewport bounds.
- Page transitions must not expose duplicated titles, overlapping forms, neighboring pages, or content outside the viewport.
- Preserve system back behavior and `android:enableOnBackInvokedCallback="true"` while predictive back is supported.

## Required verification

For navigation, animation, or performance changes, run at least:

1. `:app:compileDebugKotlin`
2. `:app:assembleDebug`
3. `:app:assemblePerformance`
4. `:app:lintDebug`
5. `git diff --check`

Also verify that the performance APK is non-debuggable, includes the packaged baseline profile, and can replace the locally installed debug-signed build. When a device is available, test rapid tab changes, first-use transitions, grades/timetable opening, drag sorting, normal back, predictive-back completion, and predictive-back cancellation.

Before handing off a minified APK, smoke-test every Retrofit service family at least through converter creation. A successful build alone does not prove that reflection metadata survived R8.
