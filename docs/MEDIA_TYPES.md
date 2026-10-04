# Media types, source repos and search – design notes

Goal: bring Mangayomi's multi-media support and easy source management into Kotatsu while keeping
Kotatsu's search/filter UX (hide-empty global search, genre/tag filters, year range).

## What already exists in Kotatsu

| Wanted | Status | Where |
| --- | --- | --- |
| Hide sources with no results | Exists (`hideEmpty`) | `search/ui/multi/SearchViewModel.kt` |
| Genre / tag search | Exists | `filter/ui/FilterCoordinator.kt` |
| Search by year / age | Exists, but only shown when the source parser reports `isYearSupported` / `isYearRangeSupported` | `FilterCoordinator.year`, `yearRange` |
| Multiple media types | Missing | – |
| Add sources by URL, auto-update | Missing (sources are compiled into the parsers lib) | – |

## Phase 1 – media type model (this commit)

`core/media/MediaType.kt`: `MANGA`, `BOOK`, `VIDEO`, plus `ContentType.toMediaType()`
(`NOVEL` -> `BOOK`, rest -> `MANGA`). Additive only; nothing uses it yet.

## Phase 2 – surface media type in the UI

- Explore: media-type chips (All / Manga / Books / Video) filtering the source list
  (`explore/ui/ExploreViewModel.kt`).
- Global search: same chips; keep `hideEmpty` and make it default-on.
- Library (history, favourites): group/filter by `MediaType`.
- Filter sheet: show the year slider as disabled-with-hint instead of hiding it when a source
  has no year support, so "search by age" is discoverable.

## Phase 3 – book reader

`NOVEL` sources already flow through the manga pipeline. Needs a text reader activity (chapter
HTML -> WebView/TextView, font/size/theme settings) routed from `AppRouter` when
`MediaType.BOOK`.

## Phase 4 – video

- New `VideoRepository` (list / details / episodes / stream URLs) parallel to `MangaRepository`.
- Player: AndroidX Media3 ExoPlayer, position saved to history.
- Needs Room migration for history/favourites rows to carry a media type.

## Phase 5 – source repos and updates

- Repo manager: user adds a repo index URL (JSON list of `{name, pkg, version, url, mediaType}`).
- Installer/updater: download, verify signature/hash, install; periodic WorkManager check
  marks sources "update available".
- Format decision pending: APK plugins (extend the existing `content://` plugin provider in
  `core/parser/external`) vs. embedded JS sources.

## Build note

The session this was written in could not reach dl.google.com or jitpack.io, so nothing here
has been compiled. Build locally before relying on it.
