# InstaCleanLink

InstaCleanLink is an Android app that removes Instagram tracking parameters from links before those links are opened or shared.

The app uses a multi-layered strategy to sanitize Instagram tracking parameters:

- Known & rotating parameters: `igsh`, `igshid`, `igsi`, `stkn`, `dlrf`, `psln`, `cplk`, `rpxt`, `srtk`, `obrf`, `xtok`, `mdxt`, `exln`, `vrfl`, `ig_rid`, `ig_mid`, `click_source`, `ndid`, `fbclid`, etc.
- Smart heuristic detection for rotating 4-letter Base64 share tokens.
- Content-path allowlisting on self-contained media routes (`/p/`, `/reel/`, `/reels/`, `/tv/`, `/stories/`), stripping unknown tracking parameters while preserving functional parameters like `img_index`.

## What the app does

- intercepts Instagram links on Android;
- removes known tracking parameters without changing the main destination of the link;
- opens the cleaned link in the user's preferred Instagram app or in a browser;
- works when the user taps links received in apps such as WhatsApp;
- acts as a share target for plain text and forwards cleaned content;
- informs the user when a link was cleaned, when cleaning failed, or when no known trackers were found;
- supports both **English** and **Portuguese** directly in the app UI.

## App behavior

### When opening a link

1. The app checks whether the link belongs to Instagram.
2. If it finds `igsh`, `igshid`, `igsi`, or `stkn`, it removes those parameters.
3. If sanitization succeeds, the app shows a success message and opens the cleaned link.
4. If no known trackers are found, the app informs the user and opens the link normally.
5. If sanitization fails:
   - on the first attempt, the link is blocked for safety;
   - on the second attempt, the app allows the original link to open and shows a warning.

### When sharing text

1. The app scans the shared text for Instagram links.
2. It removes `igsh`, `igshid`, `igsi`, and `stkn` from each supported link.
3. It forwards the sanitized text, prioritizing WhatsApp.

## Supported link hosts

The app primarily handles these hosts:

- `instagram.com`
- `www.instagram.com`
- `m.instagram.com`
- `ig.me`

## Tech stack

- **Kotlin**
- **Android SDK**
- **Jetpack Compose**
- **Material 3**
- **Gradle Kotlin DSL**
- **JUnit 4**
- **Robolectric**

## Requirements

- Android Studio
- JDK 11
- Android SDK configured locally
- the Gradle Wrapper included in this repository

Current app configuration:

- **Min SDK:** 24
- **Target SDK:** 36
- **Compile SDK:** 36

## Installation and local development

### Option 1: Android Studio

1. Clone the repository:

   `git clone https://github.com/FredericoReid/InstaCleanLink.git`

2. Open the project in Android Studio.
3. Wait for the Gradle sync to finish.
4. Run the app on a physical device or emulator.

### Option 2: command line

From the project root:

- build the debug APK:

  `./gradlew assembleDebug`

- run unit tests:

  `./gradlew :app:testDebugUnitTest`

On Windows, use `gradlew.bat` or `./gradlew` depending on the terminal you use.

## Automatic GitHub releases

The repository includes a GitHub Actions workflow that automatically publishes the latest APK to GitHub.

### How it works

- the workflow runs on every push to the `main` branch;
- it can also be triggered manually via `workflow_dispatch`;
- it builds an installable **debug APK**;
- it moves and updates a rolling release tagged `latest-apk`;
- it uploads `InstaCleanLink-latest-debug.apk` to that release.

This makes it possible to always download the newest APK directly from the GitHub Releases page.

### Important note

At the current stage of the project, the workflow publishes a **debug APK** because it can be installed without storing a private signing key in the repository.

If you want to publish a **signed release APK** in the future, you will need to configure GitHub secrets for the keystore and adjust the workflow for secure signing.

## How to use the app

### Opening Instagram links

1. Install the app.
2. Tap an Instagram link on your phone.
3. Choose InstaCleanLink as the app that should handle that link.
4. InstaCleanLink will remove known trackers before opening it.

### Sharing text that contains Instagram links

1. In any app, share text that contains one or more Instagram links.
2. Choose InstaCleanLink from the share sheet.
3. The app sanitizes the text and tries to forward it to WhatsApp.

### Changing the app language

From the main screen, the user can switch the interface between:

- **English**
- **Portuguese**

All user-facing messages, dialogs, and status toasts follow the language currently selected in the app.

### Preferred Instagram app

From the main screen, the user can:

- choose a preferred Instagram app;
- change that preference later;
- reset the saved preference.

## Permissions and storage notes

The project requests storage permissions because it includes a persistent logging system.

- On supported Android versions, logs can be written to `Downloads/LOGS INSTACLEAN LINK/`.
- If that is not possible, the app falls back to internal app storage.

Relevant permissions declared in `AndroidManifest.xml`:

- `WRITE_EXTERNAL_STORAGE` (up to SDK 28)
- `READ_EXTERNAL_STORAGE` (up to SDK 32)
- `MANAGE_EXTERNAL_STORAGE` (Android 11+)

## Logs

The app includes an internal logger for debugging:

- default file: `instacleanlink_debug.log`
- preferred destination: the public Downloads folder
- fallback: internal app storage

This is useful for testing and diagnosing link sanitization failures.

## Project structure

- `MainActivity` — main screen and shared-text flow
- `UrlHandlerActivity` — intercepts links and decides how to open them
- `UrlCleaner` — identifies and removes known tracking parameters
- `PrefsManager` — stores preferences, retry state, and language selection
- `AppLanguageManager` — applies English/Portuguese localization choices
- `AppLogger` — records events and errors to a file

## Tests

The project includes tests for:

- URL sanitization;
- Instagram link cleanup inside shared text;
- link opening flows;
- localized user-facing messages;
- retry behavior when sanitization fails.

Run the test suite with:

`./gradlew :app:testDebugUnitTest`

## Privacy

The goal of InstaCleanLink is to reduce tracking in Instagram links before those links are opened or forwarded. The app currently depends on a list of known parameters, so any new trackers introduced in the future will need to be added explicitly to the sanitizer.

## Current status

The app currently removes:

- `igsh`, `igshid`, `igsi`, `stkn`
- `dlrf`, `psln`, `cplk`, `rpxt`, `srtk`, `obrf`, `xtok`, `mdxt`, `exln`, `vrfl`
- dynamically rotated Instagram Base64 share tokens and unknown parameters on post/reel/story URLs

while preserving legitimate non-tracking parameters in the same URL.