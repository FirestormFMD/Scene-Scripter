# Scene Scripter

A Fabric mod for Minecraft 26.2 for staging and animating cinematics in-game: keyframed mobs and players, motion paths that walk, jump and fall like the real thing, and fights and explosions tied to a timeline. Scenes are recorded with Flashback or Replay Mod.

See [docs/PLAN.md](docs/PLAN.md) for the full design and roadmap.

## Status

Early development (Phase 0/1 of the plan).

- **Core library** (`core/`): the scene model, keyframe channels and interpolation, motion path planning (ground snapping, stepping, jumping, falling, timing), undoable edits, the reversible block journal and the JSON scene format. Unit tested.
- **Mod** (`fabric/`): loads, and Right Ctrl toggles editor mode. The editor UI, actors and playback come next.

## Layout

| Directory | Contents |
|---|---|
| `core/` | Plain Java with no Minecraft dependency. Builds and tests without the game. |
| `fabric/` | The Fabric mod. Packages `core` inside its jar. |
| `docs/` | Design documents. |

## Building

Requires Java 25.

```sh
./gradlew build            # builds everything and runs the tests
./gradlew :core:test       # core unit tests only
./gradlew :fabric:runClient
```

The mod jar is written to `fabric/build/libs/`.
