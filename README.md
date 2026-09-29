# Scene Scripter

A Fabric mod for Minecraft 26.2 for staging and animating cinematics in-game: keyframed mobs and players, motion paths that walk, jump and fall like the real thing, and fights and explosions tied to a timeline. Scenes are recorded with Flashback or Replay Mod.

See [docs/PLAN.md](docs/PLAN.md) for the full design and roadmap.

## Status

All six phases of the [plan](docs/PLAN.md) are implemented; section 1b of the plan lists what is built, how it is tested and what is still open. CI runs the game tests alone and beside Flashback 0.43.6 and Replay Mod 2.6.27 (the 26.2 builds), and checks that Replay Mod records a scene and plays it back, also in a game without Scene Scripter.

- **Editor:** Right Ctrl opens a vanilla-GUI editor with an outliner, inspector, timeline with a curve editor, move and turn handles, and tools for placing objects, drawing or sketching paths and editing blocks. F1 lists every shortcut.
- **Objects:** any vanilla mob with its own looks, poses and animations (charged creepers, rearing horses, a golem offering a flower), Mannequins as players, TNT, props such as displays and falling blocks, and projectiles, driven by keyframed channels and never by their own AI.
- **Motion paths** that snap to the ground and walk, sprint, sneak, jump and fall with vanilla physics.
- **Fights, explosions and block changes** resolved ahead of time by the solver, and fully rewindable.
- **Performance capture:** act a part out and keep it as keyframes or a motion path; loop capture builds crowds.
- **Scene track** for time of day, weather, sounds and commands; **crowds**; **Record** for Flashback or Replay Mod.

Start with the [user guide](docs/USER_GUIDE.md), or run `/scene tutorial` in a creative world.

## Layout

| Directory | Contents |
|---|---|
| `core/` | Plain Java with no Minecraft dependency. Builds and tests without the game. |
| `fabric/` | The Fabric mod. Packages `core` inside its jar. |
| `docs/` | The plan and the user guide. |
| `tools/inspect/` | Queries for the workflow that prints parts of the game's code. |
| `replay-check/` | Test only: a client without Scene Scripter that plays scene recordings back in Replay Mod. |

## Building

Requires Java 25.

```sh
./gradlew build            # builds everything and runs the unit and server game tests
./gradlew :core:test       # core unit tests only
./gradlew :fabric:runClient
./gradlew :fabric:runClientGameTest   # the client game test (needs a display, or xvfb-run)
```

The mod jar is written to `fabric/build/libs/`.

## Releasing

Pushing a version tag such as `v1.0.0` runs `.github/workflows/release.yml`: it builds and tests the mod, creates a GitHub release with the jar and the matching section of [CHANGELOG.md](CHANGELOG.md), and uploads the jar to Modrinth. The Modrinth upload needs, once, a Modrinth project for the mod, a `MODRINTH_TOKEN` repository secret (a Modrinth personal access token that can create versions) and a `MODRINTH_PROJECT_ID` repository variable; without them it is skipped.
