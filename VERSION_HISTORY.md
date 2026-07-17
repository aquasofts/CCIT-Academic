# Version History

This file is the canonical record of the app version. Keep it synchronized with the default
`versionName` and `versionCode` in `app/build.gradle.kts`.

## Current version

- Version name: `2.1.14`
- Version code: `20`
- Updated: 2026-07-17

## Changes

### 2.1.14 (versionCode 20) — 2026-07-17

- Ported TiebaLite's Retrofit sign-in API and ordered FormBody interceptor chain instead of maintaining a hand-built request.
- Made manual and automatic sign-in consume the fresh `data.anti.tbs` from the current FRS response, without using the web profile endpoint or the account database's stale TBS.
- Persisted the TiebaLite client identity and sync state, and now reject nominal code-0 responses that omit the complete sign-in user information.

### 2.1.13 (versionCode 19) — 2026-07-17

- Aligned the single-forum Tieba check-in request with TiebaLite's final V11 request fields, headers, ST anti-abuse parameters, encoding, and signature.
- Preserved consecutive signed-day display and fixed alternate Tieba error-code parsing so server errors are no longer reported as code 0.

### 2.1.12 (versionCode 18) — 2026-07-17

- Renamed the Gradle project, launcher label, and in-app product branding to Cithub while preserving the existing application ID and user data.
- Updated documentation, release artifact names, and news request identifiers to the Cithub name.

### 2.1.11 (versionCode 17) — 2026-07-17

- Removed the redundant “来自××” forum-name label beneath user names in Tieba thread floors while preserving time, floor number, badges, and IP location.
- Simplified the Tieba long-press menu to Reply and Copy by removing Report and Collect This Floor.

### 2.1.10 (versionCode 16) — 2026-07-17

- Changed Tieba reply actions to launch the official Baidu Tieba client directly with TiebaLite's exact thread/floor dispatch URI.
- Removed installed-app and intent-handler discovery from the reply launch path to avoid Android package-visibility false negatives.

### 2.1.9 (versionCode 15) — 2026-07-17

- Replaced Tieba floor long-press actions with an anchored animated popup menu matching the supplied reference.
- Added a fully in-app TiebaLite-compatible reply flow, including emoji, original-image selection, and up to nine uploaded images, without checking for the official Tieba app.
- Added persistent disk caching for news list and article images, plus stable shimmer and reveal transitions while article images load.

### 2.1.8 (versionCode 14) — 2026-07-17

- Reordered the main navigation to Tieba, News, Academic, and Mine, with Tieba opening first.
- Moved article titles into the reader body, added section labels, removed official-account covers, aligned all official-news dates, and replaced eager image prefetching with lazy loading, shimmer transitions, and aspect-ratio-safe rendering.
- Made Tieba rules interactive and added thread-list emoji rendering, IP locations, manager badges, and long-press Copy/Reply actions that launch TiebaLite's official-client reply route.
- Replaced web sign-in with TiebaLite's signed official-client protocol and display the server-returned consecutive sign-in days after success.

### 2.1.7 (versionCode 13) — 2026-07-17

- Fixed blank in-app article readers across official accounts, campus news, and official news, and improved RSS body-image normalization and alignment.
- Replaced infrastructure feed labels with per-article official-account names from RSS metadata and added disk-backed prefetching for the next 20 official-news cover images.
- Reworked Tieba rules and pinned threads into compact scrolling rows, excluded the first floor from displayed reply totals, and added in-thread author titles and levels.

### 2.1.6 (versionCode 12) — 2026-07-17

- Added an Official News home section that concurrently aggregates School News, Notices, and Academic News from the official CCIT portal and sorts all entries newest-first.
- Added in-app official-news detail loading with responsive rich text, HTTPS image normalization, cached list fallback, and source-aware metadata.
- Improved home-feed image loading with stable skeleton placeholders, short cache-friendly crossfades, reduced-motion support, and explicit failure states.

### 2.1.5 (versionCode 11) — 2026-07-17

- Replaced the hand-written feed field matcher with RSS Parser 6.1.7 and added consistent RSS 0.9x/2.0, RSS 1.0/RDF, Atom, Media RSS, enclosure, iTunes, and common namespace mapping.
- Added BOM, HTTP charset, XML declaration, response-size, HTTPS, DOCTYPE, structured per-source failure, and normalized Unicode cache handling.
- Added deterministic placeholders for incomplete or interactive feed content, with user-initiated original-page loading, timeout, retry, and browser fallback states.
- Added regression fixtures for the two supplied feeds and compatibility coverage for alternate XML feed formats, encodings, partial failures, and cache fallback.
- Changed the default official-account subscription to the single Cloudflare RSS Hub feed and kept article ordering newest-first even with one source.

### 2.1.4 (versionCode 10) — 2026-07-16

- Fixed RSS article details appearing blank or remaining indefinitely in a loading state, especially with dark theme enabled.
- Added regression coverage for the live campus-news RSS structure and theme-aware article rendering.

### 2.1.3 (versionCode 9) — 2026-07-16

- Added separate, editable multi-source RSS lists for official accounts and campus news, with the two supplied official-account feeds and campus-news feed as defaults.
- Merged and deduplicated articles from all configured feeds in newest-first publication order.
- Improved RSS and Atom compatibility, HTTPS redirect handling, relative-link resolution, media covers, date parsing, and cached fallback behavior.

### 2.1.2 (versionCode 8) — 2026-07-16

- Added a default home tab that aggregates two official-account feeds and campus news with source attribution and cached refresh.
- Added an in-app rich-content reader with image support and safe fallback to original interactive articles.
- Replaced the campus Tieba toolbar refresh action with animated pull-to-refresh.
- Expanded the bottom navigation to Home, Tieba, Academic, and Mine with narrow-screen-safe sizing.

### 2.1.1 (versionCode 7) — 2026-07-16

- Removed the app-wide WebVPN login gate so Tieba and settings remain available while signed out.
- Required an active WebVPN session before showing the academic-system login.
- Shortened the bottom navigation label from “教务系统” to “教务”.

### 2.1.0 (versionCode 6) — 2026-07-16

- Reworked the app interface, navigation motion, settings, and performance build pipeline.
- Added separate automatic-captcha and manual-captcha product flavors.
- Expanded native academic-system pages and WebVPN protocol handling.
- Added the Changchun Institute of Technology campus Tieba experience, including forum, thread, profile, search, floor replies, and sign-in support.
- Added the branded “长春工程学院 / 校园贴吧” thread header.
- Fixed original-image viewing by following TiebaLite's signed `/c/f/pb/picpage` flow and rejecting the 238×238 Tieba placeholder.

### 1.1.0 (tag `V1.1.0`) — historical

- Updated project documentation and screenshots.
- Consolidated repository guidance and removed the obsolete standalone web-test workspace.

### 1.0.0 (tag `V1.0.0`) — historical

- Established the first tagged production version line.
- Earlier detailed change notes were not preserved; this entry records the existing repository tag without inventing missing release details.
