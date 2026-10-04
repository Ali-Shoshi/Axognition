# Fractions · Share, see & solve

A narrated visual fractions lesson using the same layout, controls, pacing and
Android host as Around & inside.

The Fractions unit also includes **Fraction calculations: animated practice**,
a separate, more challenging lecture with the same player and design. Preview it
at http://localhost:8080/lessons/fractions-calculations.html. Its ten chapters
cover adding and subtracting several fractions, mixed numbers, multiplying
three fractions, cancelling common factors, division and division chains,
operation order, and a final calculation using brackets and all four operations.
Forty narrated scenes reveal calculation steps, fraction bars, removed pieces,
and overlapping multiplication grids. Each chapter ends with a six-option
multiple-choice question and a typed calculation question (20 questions total).

The new lecture has its own `fractions-calculations` ID, progress and scores.
Its English and Albanian JSON files, HTML, diagram renderer and CSS live beside
the introductory lecture. Both use shared `geometry.js` and `geometry.css`,
including narration, preparation, ten-second slide waits, pause, checkpoints,
review and server activity syncing. The Mathematics progress card counts the
Fractions unit complete when both of its lectures are complete.

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
node server/test-fractions-calculations.cjs
node server/test-geometry.cjs
```

The browser checks require Playwright and Chrome (or bundled packages exposed
through NODE_PATH). They cover both languages and themes, portrait/landscape
layouts, scenes and animation bounds, questions, slider, voice speed, Next gating,
progress migration, student isolation, finish and restart. Captures and results
are written to `server/build/fractions-preview`.

Physical-device speech quality and keyboard dismissal remain device checks.

The calculations test independently verifies fraction arithmetic and all
checkpoint answers, checks 480 diagram layouts across both languages and
themes on phone/tablet viewports, and exercises start, resume, navigation,
completion, review and independent progress. Preview captures are written to
`server/build/fractions-calculations-preview`. Server scoring coverage includes
the new lecture in `LectureScoringTest`.

## Two questions per checkpoint

All three mathematics lectures include a calculation pad at every question.
Wide screens keep the original 760px question card on the left and use the
remaining space on the right for scratch work. Smaller screens place the pad
below the question without reducing the question's width. The pad supports pen
and finger input, a pen-thickness slider, an eraser and Clear all. Holding the
stylus barrel button temporarily erases; the Android host also forwards native
stylus button and eraser-tip state for WebView compatibility.
The app disables navigation-drawer swipe gestures while a lecture player is
open, so horizontal handwriting strokes cannot open the menu. The WebView also
keeps parent views from intercepting an active touch stroke. Drawer gestures are
restored when the player closes or the lecture screen is disposed.

Scratch work is kept separately for each question during the open lesson,
survives resizing and question revisits, and clears on a new attempt or page
reload. It is not submitted as an answer or uploaded as activity data. Wrong
answers use solid red fields/options and a red feedback banner beginning with
"Incorrect", including when a wrong result is revisited or resumed.

Run `node server/test-checkpoint-pad.cjs` for drawing, erasing, stylus-button,
thickness, resize, layout, language/theme and wrong-answer feedback checks across
all three lectures. Browser checks do not verify physical stylus hardware.

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
