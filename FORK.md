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

## Local setup (not committed)

- `local.properties`: `sdk.dir=~/Android/Sdk`
- `mise.toml`: Java temurin-17 (listed in `.git/info/exclude`)
- `./scripts/fetch-proot.sh` once per checkout (needs NDK 30.0.16248370)
