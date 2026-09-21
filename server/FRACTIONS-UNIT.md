# Fractions · Share, see & solve

A narrated visual fractions lesson using the same layout, controls, pacing and
Android host as Around & inside.

## Run

From the server directory, run `.\run-server.bat`, then open
http://localhost:8080/lessons/fractions.html. Rebuild the Android app to use the
shared native lesson host. Open Lectures > Mathematics > Fractions.

The tablet uses the existing AXOGNITION_SERVER_URL and must reach that server.
Use `?theme=dark&lang=sq` to preview the Albanian lesson in dark mode.

## Content and interaction

Ten chapters contain four narrated scenes each, followed by a checkpoint:
equal shares, numerator/denominator, unit fractions, number lines, equivalence,
comparison, addition, subtraction, fractions of a collection and a picnic recap.
Models change with each explanation: unequal shares become halves, selected
parts light up, number-line markers step forward, equal lengths align, added
pieces join and subtracted pieces leave. The unit-fraction slider changes the
number of equal parts while keeping the whole the same size.

Each scene has a 40-second exploration window and waits for narration to finish.
Next unlocks after ten seconds on unfinished scenes; revisited completed scenes
and completed lectures have no wait. Pause, replay, sound, voice speed, chapter
selection and checkpoints work exactly as in Around & inside. The approximately
30-minute estimate includes practice and answering, not continuous speech.
Captions contain the spoken explanation; reduced-motion mode shows the results.

Starts, resumes and retries show a 15-second preparation screen with posture,
scoring and retry rules. It pauses if the app is hidden. A 100% result permits
immediate Review, beginning at the first teaching scene.

English and Albanian content lives in `fractions.json` and `fractions.sq.json`.
`fractions.html`, `fractions.js` and `fractions.css` supply the introduction and
fraction diagrams. Shared `geometry.js` and `geometry.css` supply the player.
Edit the source resources and restart/rebuild the server to serve updates.

Android follows the app's language and theme, stops speech on pause or exit,
preserves the WebView on rotation, and uses speech-completion callbacks. Voice
availability depends on the installed TTS engine. Answering every question
records a score; more than 75% passes. Finish returns to the lecture list even
after failure. The first failure waits one hour; other results below 100% wait
24 hours before retry. Only 100% enables review without a cooldown. Restart
clears only this lesson's current progress, preserving activity history. Progress
and activity sync to PostgreSQL per student, with an offline queue on Android.
See [Lecture progress](LECTURE-PROGRESS.md).
The earlier fractions player's chapter and earned checkpoints migrate once to
the first student who opens the new version. Browser progress stays on the device.

## Verification

From the repository root:

```powershell
node server/test-fractions.cjs
node server/test-fractions-browser.cjs
node server/test-geometry.cjs
```

The browser checks require Playwright and Chrome (or bundled packages exposed
through NODE_PATH). They cover both languages and themes, portrait/landscape
layouts, scenes and animation bounds, questions, slider, voice speed, Next gating,
progress migration, student isolation, finish and restart. Captures and results
are written to `server/build/fractions-preview`.

Physical-device speech quality and keyboard dismissal remain device checks.

## Two questions per checkpoint

Each of the ten chapter checkpoints now has two questions: a multiple-choice
question with exactly six distinct options, followed by a typed numeric answer.
The second question asks for a calculation or a missing number. Each question
allows one answer and reveals the correct answer after an error. All questions
must be attempted before the result is shown; the denominator follows the actual
question count. English and Albanian use matching questions.

The player labels Question 1 of 2 / Question 2 of 2, gives feedback for each,
and saves progress between questions. Reloading resumes the current checkpoint.
Previously earned answers are kept for the matching question; the added question
still needs to be completed. Restart clears both answers in every checkpoint.

The final Fractions checkpoint asks learners to combine quarters and eighths
to find what remains, then solve a separate 24-counter colour problem. Its two
questions replace the former simple addition/equivalence questions. Students
who finished the former checkpoint keep their earlier chapter progress and
attempt these two new questions to restore lecture completion.

Run `node server/test-checkpoint-pairs.cjs` for answer gating, six-option content,
partial resume, completion and restart checks across both lessons and languages.
