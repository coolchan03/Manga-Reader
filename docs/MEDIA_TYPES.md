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
- Library (history, favourites): quick-filter by `MediaType` (Manga / Books / Video) without a database migration; JS source ids and native parser types are classified at query time.
- Filter sheet: show the year slider as disabled-with-hint instead of hiding it when a source
  has no year support, so "search by age" is discoverable.

## Phase 3 – book reader

Mangayomi JavaScript `NOVEL` sources now open in `MediaReaderActivity`. The activity reuses
Kotatsu's chapter/history/incognito pipeline, renders `getHtmlContent()` in a locked-down WebView,
restores scroll position, and provides persistent text-size controls. Native Kotatsu-Redo sources
keep their existing reader behavior.

## Phase 4 – video

Mangayomi JavaScript `ANIME` sources use the same media activity. Episodes come from the ordinary
chapter model; stream URLs come from `getVideoList()` and play in AndroidX Media3/ExoPlayer with
HLS support, request headers, subtitles, episode navigation, and saved playback position.

## Phase 5 – source repos and updates

- Repo manager: user adds a repo index URL (JSON list of `{name, pkg, version, url, mediaType}`).
- Installer/updater: download, verify signature/hash, install; periodic WorkManager check
  marks sources "update available".
- Format decision pending: APK plugins (extend the existing `content://` plugin provider in
  `core/parser/external`) vs. embedded JS sources.

## Build verification

The feature branch is compiled by `.github/workflows/build-check.yml`: unit tests run first, then a
debug APK is assembled and uploaded as a workflow artifact.
