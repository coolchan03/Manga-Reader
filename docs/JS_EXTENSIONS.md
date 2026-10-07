# Running Mangayomi JavaScript extensions

`org.koitharu.kotatsu.jsext` runs **unmodified Mangayomi JS extensions** (the
`class DefaultExtension extends MProvider` files from
[mangayomi-extensions](https://github.com/kodjodevf/mangayomi-extensions)), so Kotatsu can use
Mangayomi's novel and anime sources. Manga sources stay on Redo's native parsers, which already
cover most of them.

## How it works

Mangayomi's runtime has two halves, and both are reused:

| Half | Mangayomi (Dart) | Here |
| --- | --- | --- |
| JS side: `MProvider`, `Client`, `Document`, `Element`, `SharedPreferences`, helpers | embedded in `lib/eval/javascript/*.dart` | copied verbatim to `src/main/resources/jsext/*.js` (Apache-2.0, attribution in each header) |
| Host side: HTTP, HTML parsing, crypto, preferences | Dart (`http`, `html` packages) | Kotlin: OkHttp transport, **Jsoup** (Mangayomi's selector dialect is modelled on Jsoup's), `javax.crypto` |
| JS engine | `flutter_qjs` (QuickJS) | `quickjs-kt` (QuickJS) |

JS talks to the host through one `sendMessage(name, json)` function. QuickJS bindings are either
sync or async, so a small prelude routes the async names (HTTP, extractors) to an async binding.

## Status

Implemented: `Client` (all verbs), `Document`/`Element` (select, selectFirst, attr, text, innerHtml,
outerHtml, getSrc/getHref/getImg/getDataSrc, siblings, children, by tag/class/id), `SharedPreferences`
with `getSourcePreferences()` defaults and editable source settings (text, toggle, single-choice and multi-choice preferences), `console.*`, `cryptoHandler`, and the full extension contract
(`getPopular`, `getLatestUpdates`, `search`, `getDetail`, `getPageList`, `getVideoList`,
`getHtmlContent`, `cleanHtmlContent`, `getFilterList`). JS network requests use Kotatsu's scraping
client and carry their source identity, so Cloudflare challenges can use Kotatsu's normal automatic
verification flow.

Novel HTML is consumed by the dedicated text reader. `parseEpub` and `parseEpubChapter` are also
implemented natively, including EPUB 2/3 table-of-contents parsing and a per-source book cache; this
covers the EPUB helper contract used by the current Anna's Archive JavaScript source. Anime/video
lists are consumed by the Media3 player when a JavaScript anime source returns direct playable
streams; headers and subtitles from the extension are forwarded to the player.

Not implemented yet (fail with a clear "not supported yet" error, never silently): legacy video-host
extractor host calls, `evaluateJavascriptViaWebview`, `unpackJs*`, `decryptAES*`, `parseDates`, XPath
attribute results. Dart extensions cannot run at all, only JavaScript ones.

## Tests

`JsExtensionTest` runs the real `wordrain69.js` against canned HTML. It needs QuickJS's desktop
native library, so it skips itself on the Android unit-test classpath and is run on a plain JVM.
