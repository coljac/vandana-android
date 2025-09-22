# Tiratnavandana — Native Android (Kotlin) Implementation Plan

This plan ports the existing Godot app to a native Android app using Kotlin, Jetpack Compose, and Media3. It preserves current behavior: play verse-by-verse from a single audio file per chant using timecodes; allow selecting a subset of verses; loop playback; and keep the text in sync while playing. Clarifications incorporated: AAC audio is acceptable; background and lockscreen controls are required; no multilingual support in v1; legacy per‑verse v?.ogg assets are ignored.

## Goals
- Verse-by-verse playback from a single audio track per chant, using start/end timecodes.
- Select any subset of verses, play sequentially, with optional looping.
- Show current verse text, previous/next verses dimmed; auto-scroll as audio progresses.
- Fully offline: ship audio + JSON in app assets.
- Background playback with a media notification and lockscreen controls.
- Simple, touch-friendly UI for mobile.

## Source (Godot) Summary
- Data: `Resources/chants.json` with `chants[]`, each with `id`, `title`, `audio_file` (single .ogg per chant), and `verses[]` each with `id`, `title`, `start_time`, `end_time`, `text`.
- Playback: `audio_manager.gd` plays segment via `play_verse_segment(file, start, end)`, using a timer to stop at `end_time`.
- UI: chant selector (dropdown), grid of verse checkboxes (2 columns); buttons for Select All, Play/Stop, Loop; click verse to jump.
- Scrolling text: centers current verse, dims neighbor verses; progress uses proportion of current audio clip.

## Android Architecture
- Kotlin, single-module app, Jetpack Compose (Material 3) + Navigation-Compose (optional if single-screen).
- MVVM: ViewModel + StateFlow for reactive UI state.
- Media3 ExoPlayer for playback; MediaSession + foreground service for background/lockscreen controls.
- DataStore Preferences for persistence (last chant, selection, loop).
- Hilt (optional; used minimally for Player and repository provisioning).

## Assets & Data Schema
- Keep the existing schema; copy `chants.json` and audio into assets.
  - `app/src/main/assets/Resources/chants.json`
  - `app/src/main/assets/Resources/Audio/<chant_id>.aac` (or `.ogg` initially)
- Path mapping: Godot `res://Resources/...` → ExoPlayer `asset:///Resources/...`.
- Data classes:
  - `Chant(id: String, title: String, audioFile: String, verses: List<Verse>)`
  - `Verse(id: Int, title: String, startTime: Double, endTime: Double, text: String)`
- JSON loader reads assets and parses into data classes; repository exposes current chant and verse lists.

## Playback Design (Media3)
- Build a playlist of clipped items from the chant’s single audio file:
  - For each selected verse, create `MediaItem` with `ClippingConfiguration(startMs, endMs)`.
  - Set `player.repeatMode = REPEAT_MODE_ALL` for looping selection, or `REPEAT_MODE_OFF` for one pass; `REPEAT_MODE_ONE` for single-verse loop.
- Jump-to-verse: compute index of the verse within the current selection and call `player.seekTo(index, 0)`.
- Events: use `Player.Listener` (`onMediaItemTransition`, `onEvents`) to update current verse index and UI.
- Audio attributes & focus: set `AudioAttributes.USAGE_MEDIA, CONTENT_TYPE_SPEECH/MUSIC`, handle audio focus/ducking and becoming noisy (headphones disconnect).

## Background & Lockscreen Controls
- Service: `PlaybackService : MediaSessionService` (Media3) running in foreground while playing.
- MediaSession: create `MediaSession(context, player)`; expose actions for play/pause, previous/next, seek, repeat.
- Notification: use `PlayerNotificationManager` (Media3 UI) with a `MediaStyle` notification and media session token; shows on lockscreen with transport controls and metadata.
- Metadata: set `MediaMetadata` per verse item (e.g., title = "Verse N — <Chant Title>").
- System integration: request audio focus; configure service to stop foreground on pause/stop; ensure playback resumes via Bluetooth headset controls.

## UI (Compose)
- Top AppBar: chant dropdown selector; optional resume chip.
- Verse grid: two-column list of items with checkbox + label "Verse N"; click label to jump (auto-select if needed).
- Controls row: Select All, Play/Stop, Loop toggle.
- Scrolling text panel: shows previous/current/next verse; current centered; dim others; auto-scroll according to clip progress.
- Mini-player (optional): persistent bar with quick controls when navigating.
- Accessibility: large touch targets, dynamic type, content descriptions.

## State & Logic
- `ChantRepository`: loads `chants.json`, resolves `res://` paths to `asset:///`.
- `PlayerController` (wraps ExoPlayer): builds playlist from selection; exposes `play()`, `stop()`, `setLoop()`, `setSelection()`, `jumpToVerse()`; emits current verse index.
- `ChantViewModel`:
  - State: `currentChant`, `verses`, `selectedVerses: Set<Int>`, `isPlaying`, `loopEnabled`, `currentSelectedIndex`, `currentVerseIndex`.
  - Actions: toggle/select all, play/stop, loop toggle, change chant, select & jump to verse.
  - Persists user prefs via DataStore (per-chant selection and last verse).

## AAC Conversion (optional but recommended)
- Benefits: smaller size and broad device compatibility; ExoPlayer supports AAC well.
- Command example (per chant):
  - `ffmpeg -i heart_sutra.ogg -c:a aac -b:a 128k heart_sutra.aac`
  - Update `audio_file` path in `chants.json` to the new `.aac` file name.
- Keep sample rate 44.1 kHz and CBR 128–160 kbps; test for quality.

## Packaging Considerations
- Place audio in `assets/Resources/Audio/` to keep folder structure consistent with JSON.
- APK/AAB size: if size grows with more chants, consider HE-AAC v2 for longer tracks or App Bundle asset delivery (phase 2 optimization).

## Persistence (DataStore)
- Keys:
  - `lastChantId: String`
  - `selectedVerses:<chantId> = Set<Int>` (serialize as CSV or JSON)
  - `loopEnabled: Boolean`
  - `lastVerseIndex:<chantId> = Int`
- Behavior: restore on launch; offer Resume to continue.

## Error Handling & Edge Cases
- Timecode mismatches: clamp clip windows within media duration; log and skip invalid verses.
- Very short verses: enforce minimum clip duration (e.g., 500 ms) to avoid janky transitions.
- Audio interruptions: handle focus loss transient/duck; pause/resume appropriately.
- Headset/noisy broadcast: pause when headphones unplugged unless explicitly configured otherwise.

## Testing
- Unit: JSON parsing; path mapper; selection sorting; playlist building and loop behavior.
- UI (Compose): selection toggling; Select All; jump-to-verse; verse highlighting.
- Instrumentation: small test asset to validate clipping transitions, repeat modes, service lifecycle.

## Class/Module Outline
- `data/ChantRepository`
  - `suspend fun loadIndex(): List<Chant>`
  - `fun assetUriFor(godotPath: String): Uri` (maps to `asset:///...`).
- `player/PlayerController`
  - `fun setChant(chant: Chant)`
  - `fun setSelection(indices: List<Int>)`
  - `fun setLoop(enabled: Boolean)`
  - `fun play()` / `fun stop()`
  - `fun jumpToVerse(globalVerseIndex: Int)`
  - `val currentSelectionPosition: StateFlow<Int>`
- `service/PlaybackService : MediaSessionService`
  - Owns ExoPlayer + MediaSession; foreground with notification while playing.
- `ui/ChantScreen` (Compose)
  - Verse grid, controls row, scrolling text; binds to ViewModel state.
- `prefs/UserPrefs` (DataStore)
  - Load/save last chant, selection, loop mode, last verse.

## Migration Steps from Godot
1) Export data:
   - Copy `Resources/chants.json` and audio files for all chants.
2) Optional transcode to AAC (see above) and update `audio_file` paths in JSON.
3) Place files under `app/src/main/assets/Resources/...` with identical folder structure.
4) Validate JSON: number of verses matches timecode entries; start < end; clamp within media duration.

## Milestones
1) Bootstrap project, parse assets, render chant list + verse grid (no audio).
2) Integrate ExoPlayer; build clipped playlist from selection; Play/Stop/Loop; jump-to-verse.
3) Scrolling text synced to per-clip progress; verse highlighting and auto-scroll.
4) Persistence (DataStore) and Resume; polish UI and accessibility.
5) Background playback + lockscreen controls (MediaSessionService + notification).
6) Final QA, asset pipeline checks, and Play Store readiness.

## Next Actions
- Scaffold the Android project (Compose + Media3 + DataStore + optional Hilt).
- Implement `ChantRepository`, `PlayerController`, `ChantViewModel`, and scaffold `ChantScreen`.
- Add a simple asset copy step and path mapping.

Notes
- No screenshot is required to begin; the behavior is clear from the Godot sources. If you have updated visual preferences, a screenshot can guide the Compose theming/layout.

