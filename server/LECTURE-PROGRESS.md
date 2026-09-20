# Lecture progress and activity

## Enable the changes

1. Restart the server with `server/run-server.bat`. Flyway applies migration V10
   automatically using the existing PostgreSQL connection.
2. Rebuild/install the Android app. Sign in as a child, then open either lecture.
   The old APK cannot upload activity because it lacks the native sync bridge.
3. Existing local answers/slides are imported on the next start. Historical
   timestamps, attempts and durations were never collected and cannot be recovered.

Standalone browser previews have no child session and keep progress locally.
The authenticated Android app collects and syncs activity for each child.

## Player behavior

- Advancing with Next or automatic playback completes that slide. Back retains
  completed slide markers, so Next is immediately available on those slides.
  The next unfinished slide still has its ten-second wait.
- Completing every question enables immediate Next on every slide, and correct
  choices/numeric answers are prefilled when revisiting checkpoints.
- Resume loads the server snapshot after uploading pending work. A slow network
  does not hold the welcome screen longer than 3.5 seconds; local progress works
  while native syncing continues. New devices can resume synced progress.
- Restart resets the current answers/slide markers but retains lifetime starts,
  wrong-answer counts, Back counts and raw activity. A generation number prevents
  old offline events from undoing a newer reset.
- Question revisions invalidate only the affected checkpoint. Bump the lesson
  `version` for content edits, and the chapter `checkpointRevision` when changing
  answers/questions. English and Albanian must share these versions and answers.

## Data stored

| Table | Purpose |
|---|---|
| `child_lecture_progress` | One small snapshot per child/lecture: cursor, completed slides, answers, completion timestamp, first start, lifetime counters, reset generation and content revisions. |
| `child_lecture_sessions` | Unique start sessions and start timestamps. Opening the welcome screen alone does not count; Start, Resume, Review or Restart does. |
| `child_lecture_events` | Append-only activity with UUID, child, lecture, session, event type, client timestamp, server receipt timestamp, server-graded correctness and event details. |

Event details include content version, reset generation, session sequence, slide,
question, visit UUID, submitted answer, destination for navigation, revisit flag,
playback position and timing. Each wrong submission is retained separately.
The server grades answers against its lesson definition; client correctness flags
are not accepted. Old content events remain in history with unknown correctness
and cannot award current answers. Legacy imports are explicitly identified and
retain previously earned local answers; they are not fabricated answer attempts.

Recorded events: lecture start/finish/exit; slide entry, Next, automatic advance,
Back and chapter selection; question display, each submitted answer and Continue;
pause/resume, background/foreground, voice replay/speed, mute, explorer changes,
restart and legacy import.

### Timing definitions

- `elapsedMs`: monotonic time since this slide/question visit began.
- `activeMs`: that visit's time while visible and outside the chapter chooser.
  Background time is excluded. Reading a question or pausing narration to study
  a visible slide counts; separate pause/resume events capture playback changes.
- `sinceAttemptMs`: time since the previous submission, or since question display
  for the first attempt. Each submission also retains total elapsed/active time.
- `occurredAt`: device wall-clock time. `received_at`: database receipt time;
  use this to audit clock skew and delayed offline uploads. UUID/sequence/visit
  preserve event identity and order within a session.

For time before Next, query `slide_next` events and their elapsed/active times.
For question duration and wrong attempts, query `answer_submitted` plus `correct`.
For revisits, query `slide_back` and `slide_entered` with `revisit=true`. The Back
event records both the source and target slide. Reopening a question starts a new
visit, preserving previous attempts. Sudden process termination cannot emit a
final exit or measure time beyond the last recorded action.

## Offline reliability and ownership

Android stores events in private SQLite `lecture_sync.db` before acknowledging
the JavaScript bridge. A localStorage fallback retains events if that handoff
fails. Native uploads are batched after approximately five seconds; a persisted,
network-aware JobScheduler job retries with exponential backoff after failures
and process restarts. Android may defer background work for battery restrictions.
Only server-acknowledged UUIDs are deleted locally. The server inserts events,
updates counters and saves progress atomically; retries cannot count twice.

The queue is scoped to child, server origin and lecture. Only the signed-in
child's queue is uploaded using their JWT; tokens never enter the page or URL.
Sign back into the same child if authentication expires. Unsynced data survives
normal app/process restarts, but clearing app data or uninstalling removes it.
The native SharedPreferences completion badges are a local server-backed cache.

## API

All endpoints require an active child account's bearer JWT. Child identity comes
from the verified token, never a submitted user ID.

| Method and path | Result |
|---|---|
| `GET /me/lectures/{lectureId}/progress` | Current snapshot, including default fields. |
| `POST /me/lectures/{lectureId}/events` | `{ "events": [...] }`; returns accepted UUIDs and current snapshot after commit. |
| `GET /me/lecture-progress?limit=100&after=...` | Keyset-paginated lecture snapshots and `nextCursor`. |
| `GET /me/lectures/{lectureId}/events?limit=100&afterTime=...&afterId=...` | Keyset-paginated raw events and server correctness; use both fields from `nextCursor`. |

Event batches contain 1–100 events and are limited to 256 KiB. Android additionally
limits its batches to about 192 KiB. History pages are at most 100 rows. Unknown
lecture IDs return 404; invalid batches return 400; oversized requests return 413.
Lecture definitions are discovered from resources with a bounded catalog cache.

## Scaling and operations

- Normal resume reads one indexed snapshot, independent of history size.
- Event storage has 16 PostgreSQL hash partitions by child. Child-scoped reads
  prune partitions; the primary key deduplicates `(child_id, event_id)`.
- History uses `(child_id, lecture_id, received_at, event_id)` and keyset
  pagination, avoiding large OFFSET scans. A BRIN receipt-time index supports
  archival scans without indexing every JSON field.
- Writes are bounded batches. A short row lock coordinates only that child's
  lecture, allowing different children/lectures to upload concurrently. Stateless
  API instances can share PostgreSQL; no in-memory session state is required.
- Configure `DB_POOL_SIZE` (default 10, range 1–100) per API instance within the
  database connection budget; connection acquisition times out after five seconds.
- Keep production traffic behind HTTPS, with per-account request limits at the
  gateway. Use PostgreSQL backups/PITR and monitor write latency, pool saturation,
  disk growth, autovacuum and upload failures. Export analytical workloads to a
  replica/warehouse instead of scanning event history on the request path.
- Raw events are retained by default. Set a retention policy and archive older
  rows in bounded jobs to object storage/analytics before deletion. Keep dedupe
  records for any accepted retry window if deleting raw events; deleting IDs
  without such a policy would allow very old retries to count again. Hash
  partitions spread data; they do not automatically expire it or shard servers.

This is a scalable PostgreSQL foundation, not a measured capacity guarantee.
Load-test expected concurrent learners and event volume before selecting
production capacity. No automatic history deletion is enabled.

## Verification

From the repository root (Playwright/Chrome required for browser checks):

```powershell
node server/test-lecture-progress.cjs
node server/test-checkpoint-pairs.cjs
node server/test-geometry-controls.cjs
```

From `server`, with PostgreSQL and `.env` configured:

```powershell
$env:LECTURE_DB_TESTS='true'
.\gradlew.bat test --no-daemon
```

The database test creates a random `lecture_test_*` schema, applies migrations,
verifies concurrent retries, pagination, isolation, JWT checks, request limits and
reset behavior, then removes only that schema. Without the opt-in variable, unit
tests run without the database integration scenario. Browser tests mock the native
bridge. `LectureOutboxTest` runs on Android and verifies real SQLite persistence,
student/server isolation, bounded batches, duplicate handoff and acknowledgement
rollback. Run it with `:app:connectedDebugAndroidTest` and the instrumentation
class `com.example.axognition.LectureOutboxTest`. Android's deferred background
scheduling under battery restrictions still needs longer device testing.
