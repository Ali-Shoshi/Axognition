# Around & inside

A separate narrated, animated perimeter-and-area unit. The fractions unit is unchanged.

## Try it

Restart the existing server from the server directory with `.\run-server.bat`.
Rebuild/install the Android app, then open Lectures > Mathematics >
Around & inside > Around & inside: a moving shape lab.

Browser: http://localhost:8080/lessons/geometry.html
The tablet uses the existing AXOGNITION_SERVER_URL and must be able to reach that server.

## Lesson

Ten chapters cover perimeter versus area, rectangles, squares, triangles,
parallelograms, trapezoids, circles, compound shapes, equal perimeters with
different areas, and a final park-design problem.

The server resources `geometry.json`, `geometry.html`, `geometry.css` and
`geometry.js` hold the content, layout and player. There are 40 spoken scenes.
Each has a 40-second exploration window; it never advances automatically before
speech has finished. A checkpoint follows every four scenes (about 2:40).
The approximately 30-minute estimate includes practice and questions, not continuous
speech. Next fills for seven seconds before unlocking on each scene, including
when returning to a scene. Changing orientation, theme or voice speed does not
reset or bypass that wait. The wait continues while narration is paused.

Models trace edges, fill with square tiles, assemble matching triangles, slide
a parallelogram's cut piece, join trapezoids and fill circles with sectors.
The square chapter includes an adjustable side length. Problems use multiple-choice
and number input; feedback is narrated and displayed in the speech bubble.

Android uses its installed English TextToSpeech voice, with completion callbacks
to synchronize the player. Browsers use speechSynthesis. Captions work without a voice.
The top-right voice-speed selector offers 0.5×, 0.7×, 1×, 1.25×, 1.50×, 1.75× and
2× relative to the lesson's normal narration rate. It saves the selection and
restarts active narration at the selected rate without advancing the scene.
Submitting a numeric answer with Enter/Done dismisses the keyboard.
Pause, backgrounding and exiting stop narration. Motion honours reduced-motion settings.

Completing all ten checkpoints records the lecture as finished. The final review
screen includes Finish, which saves completion before returning to the lecture
list on Android. The lecture and unit show a Finished badge, and the Mathematics
collection reflects actual completion. Restart clears this lecture's completion.
Progress and completion are stored on this device, scoped to the signed-in
student; they do not sync through the server. Existing device-wide Geometry
progress is migrated once to the first student who opens it after upgrading.

## Validation

From repository root: `node server/test-geometry.cjs`.
Checks cover all shapes and checkpoints, numeric inputs, answer gating,
voice callback cancellation, scene advancement and saved progress.
Android compilation and browser landscape/portrait layout were checked.
Physical tablet audio depends on an installed English voice and remains a device test.

Responsive layout regression checks: `node server/test-geometry-layout.cjs`
(requires Playwright and Chrome; bundled installations can use `NODE_PATH`).
This covers all 40 scenes and animation endpoints, every checkpoint and feedback,
square slider values, rotation with a typed answer, enlarged captions, and 12
portrait/landscape sizes. Screenshots and results go to `server/build/geometry-layout`.

The player measures the visible window height because the connected Android
WebView can report zero for CSS viewport height and height media queries. Its
grid reserves separate space for headings, diagrams, captions and the slider;
SVG intrinsic dimensions cannot expand the grid. Diagram frames include labels
and moving pieces in both orientations. Rotation preserves the current diagram
and playback state, and paused scenes show their shapes immediately. Very short
windows or enlarged text can scroll inside the stage and question cards while
navigation remains on screen.

The Geometry WebView follows Axognition's saved light/dark preference, including
when it differs from Android's system setting. The URL selects the first-paint
theme and `geometrySetTheme` applies later changes without resetting playback or
answers. Standalone browsers follow `prefers-color-scheme`. All surfaces, quiz
states, measurement labels and controls have explicit palettes; diagram labels
have a contrasting outline over animated fills. Android system-bar icon colors
also follow the app preference so time, battery and navigation remain legible.

`node server/test-geometry-theme.cjs` checks both palettes across every scene and
checkpoint, text contrast, prominent chapter/Next controls, browser fallback,
app preference precedence and live theme changes. It uses the same Playwright
setup as the layout test and writes captures to `server/build/geometry-theme`.

`node server/test-geometry-controls.cjs` checks the seven-second fill and input
gate, timer resets, rotation/theme/rate changes, all voice speeds, saved speed,
Enter dismissal, completion gating, Finish exit and restart. Android bridge
calls are mocked in this browser test; physical keyboard dismissal and installed
TTS playback still require a connected, authorized device.
