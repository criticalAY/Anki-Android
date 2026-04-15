# Multi-Profile

## Glossary

| Term | What it is                                                                                                                                                                                                                 | Where it lives |
|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| **ProfileId** | Immutable string identifier. Either the literal `"default"` or `"p_" + 8 hex chars` (e.g. `p_a1b2c3d4`). Used as the folder name on disk. **Never the user's display name.**                                               | In-memory; persisted as a key in the global registry. |
| **ProfileMetadata** | User-visible data about a profile: `displayName`, `version`, `createdTimestamp`. Serialized as JSON.                                                                                                                       | Value stored under the ProfileId key in the global registry. |
| **Global Profile Registry** | A single `SharedPreferences` file shared across the whole app. Contains the registered profiles (ProfileId -> metadata JSON) and the bookkeeping key `last_active_profile_id`.                                             | `shared_prefs/profiles_prefs.xml` |
| **Profile-namespaced SharedPreferences** | The normal per-profile user settings (sync key, username, deckPath, deck options, etc.). Every pref file is prefixed with `profile_<profileId>_` by `ProfileContextWrapper`, so profiles cannot see each other's settings. | `shared_prefs/profile_<profileId>_*.xml` |
| **Profile root directory** | Private-storage root for a single non-default profile. Holds `files/`, `cache/`, `databases/`, etc.                                                                                                                        | `/data/data/<pkg>/<profileId>/` |
| **Profile collection directory** | The absolute path stored in `PREF_COLLECTION_PATH` ("deckPath") inside the profile's namespaced prefs. This is where `collection.anki2` and `collection.media/` live.                                                      | For new profiles: `<external-files>/<profileId>/` |

The **global registry** and the **profile-namespaced SharedPreferences** are both `SharedPreferences` files, but they serve very different roles. The registry is app-global and tracks which profiles exist; the namespaced prefs are per-profile and hold that profile's user settings.

## Global Profile Registry

Stored in `shared_prefs/profiles_prefs.xml`. Conceptually:

    profiles_prefs.xml
    ├── last_active_profile_id = "p_a1b2c3d4"
    ├── default      = {"displayName":"Default",  "version":1, "created":"2025-12-01T10:00:00Z"}
    ├── p_a1b2c3d4   = {"displayName":"Alice",    "version":1, "created":"2026-01-15T08:24:00Z"}
    └── p_e5f6g7h8   = {"displayName":"Bob",      "version":1, "created":"2026-02-03T14:10:00Z"}

Everything else about a profile (its files, its settings, its collection) is derived from the ProfileId key and lives elsewhere on disk.

## Internal storage (`/data/data/<pkg>/`)

Profile IDs are used as folder names. Display names are **not** used on disk they're only stored inside `ProfileMetadata` in the registry. This avoids collisions with reserved folder names (e.g. a user named "shared_prefs" or "databases"), illegal filesystem characters, and renames breaking paths.

    /data/data/com.ichi2.anki/
    ├── app_webview/                           # Default profile (legacy layout)
    ├── databases/                             # Default profile (legacy layout)
    ├── files/                                 # Default profile (legacy layout)
    ├── cache/                                 # Default profile (legacy layout)
    ├── p_a1b2c3d4/                            # Alice's profile root (ProfileId, not display name)
    │   ├── files/
    │   ├── cache/
    │   ├── code_cache/
    │   ├── no_backup/
    │   └── databases/
    ├── p_e5f6g7h8/                            # Bob's profile root
    │   ├── files/
    │   ├── cache/
    │   └── ...
    └── shared_prefs/                          # SharedPreferences live in one directory for the whole app
        ├── com.ichi2.anki_preferences.xml           # Default profile's user settings
        ├── profiles_prefs.xml                       # The global registry (see above)
        ├── profile_p_a1b2c3d4_com.ichi2.anki_preferences.xml   # Alice's user settings
        ├── profile_p_a1b2c3d4_deck_options.xml                 # Alice's deck options
        ├── profile_p_e5f6g7h8_com.ichi2.anki_preferences.xml   # Bob's user settings
        └── profile_p_e5f6g7h8_deck_options.xml                 # Bob's deck options

**SharedPreferences:** Android stores every `SharedPreferences` file in a single `shared_prefs/` directory regardless of which context requested it. `ProfileContextWrapper` prefixes every pref filename with `profile_<profileId>_` (see `ProfileContextWrapper.getSharedPreferences`), which is how per-profile isolation is achieved within the shared directory.

**WebView data** (for API 28+): isolated via `WebView.setDataDirectorySuffix(profileId.value)`, which makes Android place the WebView's internal storage under `/data/data/<pkg>/<profileId>/app_webview/`.

## External storage (`/storage/emulated/0/Android/data/<pkg>/files/`)

This is where the actual Anki collections live (`collection.anki2`, `collection.media/`, backups). The location is controlled by `PREF_COLLECTION_PATH` (the `"deckPath"` preference) inside each profile's namespaced preferences.

    /storage/emulated/0/Android/data/com.ichi2.anki/files/
    ├── AnkiDroid/                             # Default profile's collection (deckPath)
    │   ├── collection.anki2
    │   ├── collection.media/
    │   ├── backup/
    │   └── .nomedia
    ├── p_a1b2c3d4/                            # Alice's collection (ProfileId as the leaf folder)
    │   ├── collection.anki2
    │   ├── collection.media/
    │   ├── backup/
    │   └── .nomedia
    └── p_e5f6g7h8/                            # Bob's collection
        ├── collection.anki2
        └── ...

The default profile's leaf folder is `AnkiDroid/` for legacy compatibility (that's where existing installs have always kept the collection). Non-default profiles use their `ProfileId` as the leaf the ID *is* the collection folder; there is no nested `AnkiDroid/` subfolder.

### How deckPath is set

For the default profile, `deckPath` is seeded lazily on first run by existing `CollectionHelper` code.

For non-default profiles, `ProfileManager.loadProfileData` explicitly writes `deckPath` into the profile's namespaced prefs the first time the profile is loaded (see `ensureProfileCollectionPath`). If the preference is already set e.g. because the user has since relocated their collection it is not overwritten.

---

## Lifecycle

### Profile Addition

Only metadata is created at this stage; no folders exist on disk until the profile is loaded.

1. A `ProfileId` is generated via `UUID.randomUUID()`, with a collision check against the registry (up to 10 attempts).
2. A `ProfileMetadata` object (display name, creation timestamp, version) is built.
3. The metadata is serialized to JSON and written into `profiles_prefs.xml` under the `ProfileId` key.
4. The profile is now *registered* but *dormant*.

### Profile Switch

A profile switch requires a complete environment swap because many Android components (DatabaseOpenHelper, WebView) cache file paths at first use.

1. The user selects a profile - we update `KEY_LAST_ACTIVE_PROFILE_ID` in the global registry.
2. The app restarts.
3. On process startup, `ProfileManager.create()` reads `last_active_profile_id`.
4. It resolves the profile root directory (e.g. `/data/data/<pkg>/p_a1b2c3d4/`).
5. A `ProfileContextWrapper` is created, wrapping the application context.
6. `requireCustomDirectories()` creates the private-storage subfolders on disk if they don't exist yet.
7. `ensureProfileCollectionPath()` writes `PREF_COLLECTION_PATH` into the profile-namespaced prefs if it isn't already set.

### Profile Deletion

Deletion follows a **disk-first, registry-last** pattern: if the app crashes mid-way, the registry entry still points at the remaining files so the user can retry cleanup.

1. Refuse to delete the currently-active profile.
2. **Internal data wipe:** recursively delete the profile root (`/data/data/<pkg>/<profileId>/`). For the Default profile, delete the legacy top-level folders (`files/`, `databases/`, `cache/`, …) individually.
3. **SharedPreferences cleanup:** scan `shared_prefs/` and delete every file matching `profile_<profileId>_*.xml`.
4. **External data wipe:** delete the profile's external collection directory (`<external>/<profileId>/` for non-default, or `<external>/AnkiDroid/` for the Default profile).
5. **Registry removal:** only after all files are confirmed gone, remove the entry from `profiles_prefs.xml`.
