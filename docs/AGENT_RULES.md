# Agent Rules — SpinWheel Project

Rules I must follow at all times during this project.

---

## 1. Git — Never commit or push autonomously

- Do **not** run `git commit`, `git push`, `git add`, or any git write command without explicit user instruction.
- If the user says "commit this" or "push", do it once and only for what they specify.
- Do not create branches, tags, or remotes without being asked.

---

## 2. Do not undo user changes

- Before editing any file, read it first to understand what the user may have changed.
- If the user says "I made changes, don't redo them" — only add or fix the specific thing they asked about. Do not rewrite the whole file.
- When in doubt, make the smallest targeted edit possible.

---

## 3. Build before claiming success

- Always run `gradlew assembleDebug` (or the equivalent) and confirm `BUILD SUCCESSFUL` before saying the code is ready.
- If the build fails, fix it before reporting back. Do not describe what the code should do without verifying it compiles.

---

## 4. Test the network call before trusting it

- Before wiring a new URL format into the code, verify the URL returns the expected response (status code + content-type) using PowerShell `Invoke-WebRequest`.
- Do not assume a Google Drive URL works just because it looks correct.

---

## 5. No speculative fallbacks for broken config

- If the Firebase RC `host` field is wrong or empty, **log a clear error and stop** — do not silently substitute a fake URL.
- Fallback URLs mask real configuration bugs and cause silent failures in production.

---

## 6. Diagnose before rewriting

- If the user reports a runtime bug, **read the relevant files first** and form a hypothesis before changing any code.
- State the diagnosis explicitly before making any edit.
- Prefer a targeted fix over a full file rewrite.

---

## 7. Widget update — use AppWidgetManager directly

- `GlanceAppWidgetManager.getGlanceIds()` depends on Glance's internal DataStore being populated by `super.onUpdate`'s async block. This is a race condition on first add.
- Always query `AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(...))` directly in the worker to get widget IDs reliably.
- Then call `SpinWheelGlanceWidget().update(context, AppWidgetId(rawId))` for each ID.

---

## 8. No duplicate manifest entries

- The `SpinWheelWidgetReceiver` is declared in both the spinwheel library manifest and the app manifest. Do not add a third declaration.
- If you need to change the receiver declaration, edit the spinwheel library manifest (`spinwheel/src/main/AndroidManifest.xml`).

---

## 9. Keep Compose out of the `:spinwheel` widget process

- The home screen widget runs in a separate process managed by the launcher.
- Do not add Jetpack Compose (`compose-ui`, `activity-compose`, etc.) to `:spinwheel`'s dependencies.
- Glance is the only Compose-based API allowed in the widget layer; it translates to `RemoteViews` internally.

---

## 10. Architecture layers — respect boundaries

| Layer | Allowed dependencies |
|---|---|
| `domain/` (use cases, repository interface) | No Android types, no framework imports |
| `data/remote/` | OkHttp, Firebase RC, kotlinx.serialization |
| `data/local/` | SharedPreferences, File I/O, Kotlin Flow |
| `data/repository/` | domain + data.remote + data.local |
| Widget UI | Glance, repository (via di/SpinWheelGraph) |
| Worker | WorkManager, domain use cases, Glance update |

Do not bypass these layers (e.g., do not call OkHttp directly from the widget composable).

---

## 11. Communication style

- State what you are about to do before doing it.
- After making changes, summarize only what changed — do not reprint whole files in the chat.
- If something is uncertain or risky, say so explicitly before proceeding.
