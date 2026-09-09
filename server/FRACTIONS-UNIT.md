# Fractions unit

Start the existing server (its database, storage and local .env configuration must already be available):

```powershell
cd C:\Users\ghost\AndroidStudioProjects\Axognition\server
.\run-server.bat
```

Browser preview: http://localhost:8080/lessons/fractions.html

In the rebuilt Android app: Lectures > Mathematics > Fractions > Fractions: interactive guided unit.
The app uses its existing AXOGNITION_SERVER_URL. The tablet must be able to reach that address.

## Content and pacing

The server packages fractions.json and fractions.html under src/main/resources/lessons.
Edit the JSON to revise narration, questions or chapter models; rebuild/restart the server afterward.
There are ten 180-second chapters, with narrated scenes starting at 0, 45 and 90 seconds,
and a checkpoint at 135 seconds. Playback pauses for an answer. Timing includes intentional
observation, paper practice and discussion time; this is not thirty minutes of continuous speech.
Taking longer at questions extends the lesson. Next chapter allows faster self-paced progress.

Topics: equal shares; numerator/denominator; unit fractions; number lines; equivalence;
comparison; addition; subtraction; fractions of a collection; recap.
Addition and subtraction here use like denominators and may need adult guidance for younger children.

Android narrates using its installed English TextToSpeech voice. The browser uses speechSynthesis.
Voice quality and offline availability depend on the installed voice. No prerecorded audio is bundled.
Every narrated scene is also captioned. Use Replay narration to repeat it.
Progress is stored locally in the player, not in a server student record.

## Verification

From the repository root:

```powershell
node server/test-fractions.cjs
```

On the tablet, test Play, Pause, Replay, chapter selection, a wrong answer and then a correct one.
Rotate and reopen the lesson to check local resume. Check English voice output on the actual device.
Final completion requires all ten checkpoints.
