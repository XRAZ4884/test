# KQders Auction — Minecraft 1.21.11 source project

This archive contains the source files from the current uploaded project, arranged as a complete Gradle/Fabric project, plus `settings.gradle` so Gradle can resolve the Fabric Loom plugin.

## Build

Use Java 21 and a compatible Gradle installation, then run:

```bash
gradle build
```

## Important compatibility note

This is a source bundle, not a verified successful build. The source files provided in the uploaded project currently contain Minecraft 1.21.11 API compilation errors in GUI rendering, input handling, button rendering, keybinding categories, and sound playback. Those source errors must be fixed before the build can succeed.
