# DLSS GAME SPACE - Stage 1 Prompt

Build a native Android app called "DLSS GAME SPACE" (version 1.0.0) with Kotlin, Jetpack Compose, Material 3, Room, and Coroutines. minSdk 29, target the latest SDK. It must work without root.

STAGE 1 ONLY: project foundation, navigation, theme, Home Dashboard, and Game Library.

## Theme
Black background, neon green (#39FF14) primary, dark gray surfaces, white text, amber for warnings, red for critical. Rounded cards, dark mode, light animations. Do not copy any other brand's logo or identity.

## Bottom navigation
Home, Games, Booster, Tools, Settings. Booster, Tools, and Settings are placeholders that clearly say which stage implements them. No fake buttons.

## Home Dashboard (real data only)
- Device name, Android version, chipset (Build.SOC_MODEL on API 31+, else Build.HARDWARE)
- Display resolution and refresh rate
- RAM and storage usage with progress bars
- Battery level and battery temperature
- Games in library, favorites, recently played, total gaming time
- Poll device data only while the screen is visible, to save battery

## Game Library
- Scan installed apps with PackageManager. Use a <queries> MAIN/LAUNCHER intent, not QUERY_ALL_PACKAGES.
- Detect games via ApplicationInfo.CATEGORY_GAME
- Add game manually, favorite, search, sort (name/recent/favorite), grid and list view
- Show package name and app version
- "Remove from library" only hides the entry in Room. NEVER uninstall or touch the game. A hidden game must not reappear after a rescan.
- Tap a game to launch it

## Architecture
Modular Gradle project with modules: app, core, database, game-library, ui-components. Use MVVM with StateFlow. Do not put everything in one file.

## Rules
- No simulated data. If a value is unavailable, show "N/A". Any demo data must be labeled DEMO.
- Handle errors, and make sure the project compiles and runs.
- Do not claim features that are not implemented.
