# Source repositories

Settings -> Manga sources -> **Source repositories** lets you add a repository by URL, browse the
sources it offers, and install or update them. A repository is just a JSON file hosted anywhere
over **https** (GitHub Pages, a raw GitHub URL, your own server).

## Index format

An object with a `plugins` list, or a bare list of the same entries
(see [`sample-repo/index.json`](sample-repo/index.json)):

| Field | Required | Notes |
| --- | --- | --- |
| `pkg` (or `package`) | yes | Android package name of the plugin APK |
| `version` (or `versionCode`) | yes | Integer; must match the APK's `versionCode`. Higher = newer |
| `apk` (or `url`) | yes | **https** link to the APK. `http://` entries are ignored |
| `name` | no | Display name; defaults to `pkg` |
| `versionName` | no | Display version; defaults to `version` |
| `type` (or `mediaType`) | no | `manga` (default), `book`/`novel`, `video`/`anime` |
| `sha256` | no | 64 hex chars of the APK. If present, the download is verified before the system installer opens, and a mismatch is discarded |

Invalid entries are skipped individually, so one typo does not hide the rest of the repo.
If several repos list the same `pkg`, the highest `version` wins.

## Publishing a repo

1. Build your plugin APK (a Kotatsu-Redo parser plugin exposing the
   `app.kotatsu.parser.PROVIDE_MANGA` content provider).
2. `sha256sum my-plugin.apk` and put the hash in the entry (recommended).
3. Host `index.json` and the APKs over https and give users the `index.json` URL.
4. To ship an update, raise `version` (and the APK's `versionCode`) and change `apk`/`sha256`.

## Security notes

- Android's installer still verifies that an update is signed by the same key as the installed
  plugin, so a repo cannot silently replace a plugin with one signed by someone else.
- Only add repositories you trust: a plugin runs inside the app's process once installed.
- Plain `http://` repo and APK URLs are refused.
