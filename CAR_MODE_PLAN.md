# Car Edition Refactor Notes

This document tracks what we already changed for the car/WebView build and what is left to do.

## Goals (Do Not Break Existing Architecture)
- Stable load inside Android WebView.
- Background playback on Android-x86.
- QR login + cookie persistence.
- Car/landscape UI.
- Remove unrelated features.
- HTML5 Audio only (no MediaCodec/DRM/JNI/ARM dependencies).
- WebView fully hosted and x86-native compatible.

## Completed Changes
- Removed Electron-specific code paths, menus, IPC, tray, and shortcuts.
- Added runtime detection for WebView/file protocol and applied runtime classes.
- Switched router to hash mode in WebView/file protocol.
- Disabled service worker in WebView/file protocol.
- Adjusted API baseURL for WebView/file protocol and added cookie persistence.
- Simplified player: removed Electron integrations and added visibility/resume handling.
- Removed Last.fm integration (API, callback page, routing, state, and UI).
- Removed Proxy/Real-IP settings and request injection.
- Cleaned settings page text placeholders and removed unrelated sections.
- Removed Apple Music section, nyancat mode, and reversed playback UI.
- Removed Google Analytics (vue-gtag) integration.
- Removed MV/video feature set (routes, views, components, APIs, search tabs).
- Removed video player assets and dependency (`plyr`).
- Dropped unused `ncmModDef` definitions.
- Removed PWA/service worker setup for a pure WebView-hosted build.

## Current Focus (Car Edition Scope)
- Keep only features needed for car use: playback, library, search, login, playlists, album/artist, lyrics.
- Ensure layout works well in landscape.

## TODO (Next Steps)
- Fix remaining garbled text in UI (search for mojibake strings and replace with clean text).
- Verify WebView background playback behavior on Android-x86.
- Verify QR login and cookie persistence end-to-end in WebView.
- Adjust layout spacing for landscape screens (navbar, player, settings widths).

## How To Run (Local)
- Install deps: npm install
- Dev server: npm run serve
- Production build: npm run build

## How To Run (WebView)
- Build with: npm run build
- Serve dist/ via local HTTP server, or set VUE_APP_WEBVIEW_API_URL to your API host.
- Load index.html in Android WebView (hash routing is enabled automatically).
