# Personal fork: Agent Launcher (Galaxy A35)

Read this before AGENTS.md. AGENTS.md and docs/ARCHITECTURE.md are upstream's
rules and still apply in full.

## Goal

Target phone: Samsung Galaxy A35 (SM-A356, Exynos 1380, One UI), for daily use.
Test phone: Galaxy A34 (SM-A346E, Dimensity 1080, Mali-G68, Android 15), adb
serial `RRCW804PK8R`. Try every build on the A34 first. Anything that depends on
the chip (GPU, battery killing) gets checked on the A35 as well.

A blank, text-only home screen on the A35:
to-dos, countdowns, Google Calendar events, notification counts by category
(WhatsApp included), and a quick-capture field that appends to a markdown vault.
The vault is a private git repo shared with the laptop through GitHub. Claude
Code runs in the proot Arch guest for remote-machine work over Tailscale + ssh.

Design brief: https://claude.ai/artifact/JzXHrbkdBkjcAqYWSzWNRd

## Home screen design

Chosen 20 Nov 2026: layout "B · text bar at the top" from the Design canvas
https://claude.ai/artifact/S88ASpzhwfBZe9QnUVqyNL. Solid black, the Android
status bar hidden and redrawn as two lines of text (time, Wi-Fi, signal,
battery, date, alarm), then notification counts per app, empty black middle,
tasks at the bottom.

Architecture (decided 20 Nov 2026), top to bottom: a native Compose home screen
in `app/src/main/kotlin/dev/omalauncher/personal/home/` → one view model with
repos for status, notifications, tasks, calendar → Android services used
directly (Battery/Connectivity/Telephony managers, our own
NotificationListenerService into app-private SQLite, CalendarContract, file
read/write of `files/arch/root/vault/` = the guest's `~/vault`, WorkManager
sync, optional device-owner shade block) → upstream runtime (Runtime,
LinuxService, proot) → Arch guest → GitHub and the laptop. The OWL face
`personal/faces/home.owl` is the prototype for layout and vault format only.

## Fork rules

- Touch upstream files as little as possible, so `git merge upstream/main`
  stays easy. Put our own code in its own package or module, and say in the
  commit subject when an upstream file had to change.
- Notifications and their text never leave the phone and are never committed.
- Vault format: `todo.md` one task per line (`- [ ] text @due(YYYY-MM-DD)`),
  `countdowns.md` one per line (`YYYY-MM-DD Title`), `daily/YYYY-MM-DD.md`
  append-only timestamped lines, `inbox.md` for unsorted capture.

## Who does what

- **Claude Code** (laptop): plans, splits work into tasks, reviews, merges.
- **Codex** (`codex exec`): the work agent. Writes code and runs the build
  and tests for one task at a time.
- **GitHub Copilot CLI** (`copilot -p`): second opinion. Reviews Codex's
  diff before it is merged, and takes small isolated tasks.

A task is done only when the AGENTS.md build and test commands pass.

Task prompts, agent reports, reviews and artifact sources live in `.agents/`
(gitignored via `.git/info/exclude`), never in /tmp or the session
scratchpad: a tmp cleanup wiped those once (20 Nov 2026).

Codex's `workspace-write` sandbox cannot run Gradle (it blocks socket
creation, so the daemon fails with "Could not determine a usable wildcard
IP"). Codex compiles what it can without Gradle; Claude Code runs the
AGENTS.md Gradle checks outside the sandbox after each Codex task.

## The core

The Compose app is the core. Android and Linux never talk to each other
directly: the core's Android bridge is the only code calling Android APIs,
its Linux bridge the only code touching the guest (vault files, git, agents,
through upstream `Runtime`). Keep the screen thin; logic sits in the view
model and the bridges.

## Full screen (requirement, 20 Nov 2026)

The core runs full screen and hides as much Android UI as possible:
- Immersive mode: status and navigation bars hidden
  (`WindowInsetsControllerCompat.hide(systemBars())`,
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), edge to edge, drawn under the
  camera cutout (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`), black window
  background and splash so nothing flashes on start or Home.
- Phone settings, set by hand: gesture navigation with the gesture hint off;
  Samsung Edge panels off.
- Device owner + `setStatusBarDisabled(true)` while the core is in front, off
  when an Android app opens: blocks the swipe-down shade too. A34 test first.
- Not hideable without root: lock screen, Recents, the shade on a swipe when
  device owner is off, the system bars inside Android apps.

## Local setup (not committed)

- `local.properties`: `sdk.dir=~/Android/Sdk`
- `mise.toml`: Java temurin-17 (listed in `.git/info/exclude`)
- `./scripts/fetch-proot.sh` once per checkout (needs NDK 30.0.16248370)
