# Personal home face

- [faces/home.owl](faces/home.owl) is the black, text-only home face.
- [layout.toml](layout.toml) selects `home.owl`. [style.toml](style.toml) starts the launcher on page 0, hides the extra pages and keeps swipe-up for the drawer.
- [faces/blank.owl](faces/blank.owl) is the previous face.
- [vault-sample/todo.md](vault-sample/todo.md) contains four example tasks. Change the first due date to your phone's local date if you want to preview the `today` label after 20 November 2026.

## Install on a debug build

Install and open a Foss debug build of Omalauncher once. Set it as your home app. If multiple devices are connected, set `ANDROID_SERIAL` to the test phone's serial (the fork's A34 is `RRCW804PK8R`).

These commands replace the phone's home layout and style. Back up your copies first if you want to keep them. Run the commands from the repo root. The launcher scans `files/widgets` directly, so copy the face there without the repo's `faces/` directory. [WidgetEngine.kt](../app/src/main/kotlin/dev/omalauncher/widgets/WidgetEngine.kt#L703) · [LauncherData.kt](../app/src/main/kotlin/dev/omalauncher/storage/LauncherData.kt#L31)

```sh
adb shell 'run-as dev.omalauncher mkdir -p files/widgets files/home files/style'
adb shell -T 'run-as dev.omalauncher sh -c "cat > files/widgets/home.owl"' < personal/faces/home.owl
adb shell -T 'run-as dev.omalauncher sh -c "cat > files/style/style.toml"' < personal/style.toml
adb shell -T 'run-as dev.omalauncher sh -c "cat > files/home/layout.toml"' < personal/layout.toml
```

Install Linux from the launcher's settings before copying the sample. On Android, files created with `run-as dev.omalauncher` are owned by the app's UID. The guest runs under that same Android UID, so it can access those files. PRoot's `-0` presents the process as root inside the guest, and `HOME=/root` makes `~/vault/todo.md` resolve to `/root/vault/todo.md` there. That path is backed by `files/arch/root/vault/todo.md` in the app's private data directory. [Proot.kt:20](../app/src/main/kotlin/dev/omalauncher/linux/Proot.kt#L20) · [Proot.kt:227](../app/src/main/kotlin/dev/omalauncher/linux/Proot.kt#L227) · [Proot.kt:241](../app/src/main/kotlin/dev/omalauncher/linux/Proot.kt#L241) · [Proot.kt:323](../app/src/main/kotlin/dev/omalauncher/linux/Proot.kt#L323)

```sh
adb shell 'run-as dev.omalauncher mkdir -p files/arch/root/vault'
adb shell -T 'run-as dev.omalauncher sh -c "set -C; cat > files/arch/root/vault/todo.md"' < personal/vault-sample/todo.md
```

The sample copy refuses to overwrite an existing regular `todo.md`. Reopen home after the copies. Tap **allow notification access** on the face and enable notification access for Omalauncher in Android Settings. The face needs no other Android permission.

Task rows show up to six open items in file order. Tapping completes the task. The vault format has no completion date, so completed items stay out of the face. The face uses an optional OWL command source for edits because a direct `run(...)` handler would hide the face until Linux was installed.
