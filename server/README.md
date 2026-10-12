# ktor-sample

## Learning assistant

Each typed or spoken question includes a fresh JPEG of the current app window
(up to 1600 pixels on its longest side). The screenshot is sent only with that
question; it is not saved in conversation history or to files. The local vision
model uses it for relevant visible work, ignores it for unrelated questions, and
asks for clarification if the referenced problem cannot be read. If capture is
unavailable, the question continues with text context. Use a vision-capable model
in LM Studio; text-only models cannot interpret these screen images.

Typed chat and voice replies send the complete history of the selected conversation,
without a fixed message-count cutoff. Follow-ups use that conversation's topics, facts
and corrections. Recall questions repeat relevant original student statements near
the question so earlier incorrect assistant replies do not override those facts.
Direct English recall questions about simple personal assertions (such as names or
preferences) use the latest original student statement from that conversation.
Ordinary replies stop after one complete sentence; openings longer than 25 words
are rewritten briefly before display or speech. Requests for more
detail, steps or examples receive only the extra explanation requested. New conversations start
with their own context.

## Lecture progress and analytics

All mathematics lectures save individual progress and activity on the server.
See [LECTURE-PROGRESS.md](LECTURE-PROGRESS.md) for deployment, endpoints, event
definitions, offline syncing, database scaling and verification.
The Fractions unit includes an introduction and a separate animated calculations
lecture. See [FRACTIONS-UNIT.md](FRACTIONS-UNIT.md) for content and preview links.

## Local child authentication setup

The server reads local secrets from `server/.env`, which is intentionally ignored by Git.
Copy `.env.example` into your local `.env` values and add a random `JWT_SECRET` of at least
32 characters. Do not commit that secret.

Child credentials are stored in the `child_credentials` table as Argon2id password hashes.
The server never stores the child's original password.

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:
 * [Ktor Documentation](https://ktor.io/docs/home.html)
 * [Ktor GitHub page](https://github.com/ktorio/ktor)
 * [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).


## Features
Here's a list of features included in this project:

| Name | Description |
|------|-------------|

## Building & Running
To build or run the project, use one of the following tasks:


| Task | Description |
|------|-------------|
| `./gradlew test`    | Run the tests     |
| `./gradlew build`   | Build the project |
| `./gradlew run`     | Run the server    |

If the server starts successfully, you'll see the following output:
```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
