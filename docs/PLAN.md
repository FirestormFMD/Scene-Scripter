# Scene Scripter: Project Plan

> Status: Phases 0 to 6 implemented (see section 1b). A manual recording check with Flashback, the 26.3 port (waiting for Replay Mod) and publishing the prepared 1.0 release remain.
> Target: Fabric, Minecraft 26.2, singleplayer first.

Scene Scripter is an in-game animation tool for Minecraft cinematics. You stage mobs, players, TNT and other entities, then animate them on a timeline so they walk, jump, fight, explode and die on cue. It covers shots that would otherwise need hand animation in Blender or Maya, or dozens of real players acting in sync.

It is **not** a recorder or a camera tool. Scenes play back live in the world, and Flashback or Replay Mod records them and handles cameras and rendering.

---

## 1. Decisions so far

| Topic | Decision | Reason |
|---|---|---|
| Mod loader | Fabric | Flashback and Axiom are Fabric-only, and Replay Mod's current builds are Fabric. |
| Editor UI | Vanilla GUI: a full-screen `Screen` with an immediate-mode helper and fixed panels, and the in-world overlay drawn with vanilla gizmos. Replaces the earlier Dear ImGui plan. | Flashback and Axiom both bundle imgui-java, so our own copy risked version clashes (the Phase 0 ImGui spike). Vanilla GUI has no native libraries or shared state, runs next to both, and the overlay never reaches recordings. |
| Minecraft version | 26.2 first. Port to 26.3 once Flashback and Replay Mod support it. | 26.2 is the newest version both recorders support today. |
| Multiplayer | Singleplayer first, with a client/server split from day one so dedicated servers and shared editing can be added later without a rewrite. | Keeps v1 focused without closing the door. |
| Objects outside the editor | Objects stay visible when the editor is closed, frozen at the playhead's state. They have no collision and don't interact with anything that wasn't set up in the editor (see 5.4). | Chosen during planning. The scene stays on screen as set dressing but can't be disturbed. |
| Added to scope | Performance capture (act a part out live and record it as keyframes). | Chosen during planning. |
| Out of scope for v1 | Per-limb posing, built-in camera tracks, modded mobs as objects, NeoForge. | Chosen during planning. Can be revisited after 1.0. |

## 1b. Status

What is built, how it is checked, and what is still open. Unit tests cover the core library (142 tests); 17 Fabric server game tests and three client game tests run on every push in CI, the client tests alone and beside Flashback and Replay Mod.

| Phase | Built | Checked by |
|---|---|---|
| 0: Foundations | Gradle multi-project (`core` without Minecraft, `fabric` mod), CI build, inspect-sources workflow for reading game code. Spikes: ImGui avoided by the vanilla-GUI decision; block journal (apply, revert, chest contents, crash recovery); Mannequin skins by username or resource-pack texture, arm model, equipment, sneaking. | Game tests `journalRestoresBlocksAndChestContents`, `crashRecoveryUndoesLeftoverChanges`, `playerObjectsAreMannequins` |
| 1: Editor and static scenes | Right Ctrl editor with top bar, outliner (with visibility, lock and solo), inspector and timeline (with box selection, copy and paste of keys); fly camera; picking, dragging and snapping actors, with move arrows and a turning ring; F to frame, Ctrl+D to duplicate; scenes saved as JSON with backups; edit ops with undo and redo sent to the server; every vanilla mob, Mannequins and TNT as objects; channels and keyframes with all interpolation modes; playback, scrubbing, speeds, a work range that doubles as the loop and capture range, a ruler in ticks or seconds, an F1 shortcut list; actors frozen, never saved, and untouchable when the editor is closed. | Client game test (editor opens and closes on a real client, and its panels and shortcut list draw); game tests `actorsAreFrozenAndNeverSaved`, `playbackFollowsKeyframes`, `deathThenRevive`, `removedObjectsLoseTheirActor`; unit tests for channels, edits and the codec |
| 2: Motion paths | Ground and air paths drawn in the world; ground snapping with the ground filter and its presets, edited in the inspector; stepping, jumping and falling with vanilla physics; freehand sketching simplified to a few points, inserting and deleting points, Bézier handles on any point; speed keys, timing modes, gaits, wait, jump and gait markers, lateral offsets; paths follow craters, both from earlier blasts and from blasts that go off mid-walk; an optional warning for long drops; path problems shown in red; onion-skin ghosts; step sounds, idle sounds and sprint particles. Water paths are left for later (see Later). | Locomotion unit tests; game test `groundPathsJumpOntoRealBlocks` |
| 3: Events and the solver | Attack, hurt, die, revive, swing, use item and equipment channels; health; three-level interaction rules edited in the inspector (scene, object and event), groups and friendly fire; the solver with generated, detachable results and baked knockback; hit and miss previews; hit cooldown; mob-specific channels (aggressive, left-handed, sheared, wool color, tame, sitting, angry wolves and collar colors, rearing and grazing horses with saddles and armor, villager profession, biome and head shake, enderman carried block and scream, charged creeper) and mob poses (warden roar, sniff, emerge and dig; frog croak and tongue; breeze slide, shoot and inhale); teleports; falling blocks, displays and dropped items with chosen content; babies; mob events from a data file (iron golem attack and flower, sheep eating grass, wolf shaking, warden sonic charge and tendrils, ravager stun, teleport, hearts, villager moods, totem); custom int, float and bool variables and event conditions on them; the curve editor. | Solver unit tests; game tests `autoAttacksUseRealAttributes`, `wallsBlockAttacks`, `mobCapabilitiesReachActors`, `propsShowTheirContent` |
| 4: Explosions, projectiles, world changes | TNT, creepers, end crystals and fireballs, with defusing and thrown TNT on a baked arc; the vanilla explosion ray algorithm with seeds, protect masks, block preview and chain reactions; exposure-based damage and throws; optional item drops from blasts and vanilla death loot, which vanish on rewind; projectiles with vanilla drag and gravity, aiming and target leading; place, break and use block events; riding; scene bounds that keep chunks loaded; Apply to world. | Explosion, ballistics and world-solver unit tests; game tests `explosionsRewindCompletely`, `blockEventsPlayAndRewind`, `arrowsFlyAndHit` |
| 5: Performance capture | Possessing an object with countdown and pre-roll, invulnerability, equipment and item use (bows, food, shields), swings, hits and block interactions recorded as events, takes stored next to the scene, punch-in over a loop range, loop capture into new objects, conversion to thinned keys, raw keys or a fitted motion path. | Capture unit tests (simplification, conversion, path fitting, storage) |
| 6: Production polish | Scene track (time of day, weather, sounds, commands with undo, markers); Record and `/scene record`, `/scene run`, `/scene reset`; crowd formations with varied speeds and delays; group move and retime; spawn and despawn poofs; import with relocation; the tutorial scene; a 200-actor benchmark (under a millisecond per tick to evaluate); user guide. | Game test `sceneTracksDriveTimeWeatherAndCommands`; unit tests for tracks, crowds, relocation, event conditions, the tutorial scene and the benchmark |

**Open items**

- **Recording fidelity (Phase 0 spike 3).** CI runs the client game tests beside the 26.2 builds of Flashback (0.43.6) and Replay Mod (2.6.27): the editor, Mannequins and a played tutorial scene work with either installed, Replay Mod records the scene, and the recording plays back in Replay Mod's viewer with the scene's actors in it. Two checks stay manual for each release: recording with Flashback (it has no automatic recording a test can drive) and opening a recording in a game without Scene Scripter. Actors only use vanilla packets, which is what makes the second one work.
- **Mannequin audit: done.** A client game test poses Mannequins drawing a bow, eating, raising a shield and sleeping and checks each state on the client; the screenshot was reviewed. It found that actors' equipment changes never reached clients (actors don't tick, so vanilla never sent them), which is fixed. The fake-`ServerPlayer` fallback has not been needed.
- **Overlay screenshots (section 10):** the editor test compares the panels against a reviewed reference fingerprint on every run and checks that the F1 list draws; the Mannequin and Replay Mod tests print small screenshots to the log for review.
- **Scrub overrides (5.7)** cover death tilt, hurt flash, arm swing and creeper swelling; walk-cycle phase and body-turn lag are left to vanilla.
- **Skins from a local PNG** (5.5) work through a resource pack texture named in the inspector; bundling the PNG with the world as a server resource pack is left to the user.
- **Item drops** are off by default (open question 2): blasts drop items with a scene rule, and deaths drop vanilla loot with a per-object switch.
- **Port to 26.3** waits for Replay Mod: Flashback supports 26.3 since 0.43.4, but Replay Mod's newest build (2.6.27) is still for 26.2 only (checked on Modrinth through the inspect workflow).
- **1.0 release on Modrinth** is prepared (version 1.0.0, changelog, icon, and a release workflow run by pushing the `v1.0.0` tag). Publishing needs a Modrinth project and its token as a repository secret, so it is the owner's step.
- Everything under **Later** below.

## 2. Goals and non-goals

**Goals**

1. **The timeline drives everything.** You can play, pause, scrub and rewind anywhere, including through explosions and deaths. Nothing a scene does is permanent unless you ask for it.
2. **Objects behave like the real thing** because they *are* real vanilla entities. The mod drives them instead of their AI.
3. **Recordings stay vanilla.** The recorder only sees ordinary vanilla packets (entity movement, animations, block updates, sounds). Flashback and Replay Mod need no special integration, and a recording should open even without Scene Scripter installed. Phase 0 checks this.
4. **Battle scale.** At least 200 objects at a steady 20 TPS.
5. **Editing feels like an animation package:** selection, gizmos, keyframes, curves, and undo/redo, all inside the game.

**Non-goals for v1:** recording and rendering (Flashback and Replay Mod do that), camera paths, per-limb posing, modded entities, multiplayer co-editing, and NeoForge.

## 3. Building a shot, start to finish

This is how a typical battle shot would be made, to check the plan against what you have in mind:

1. Press **Right Ctrl** to open the editor. Drag a scene-bounds box around the battlefield.
2. Place 20 knights (player objects with a skin) and 20 zombies from the palette.
3. With the path tool, click across the field to draw a charge route. The spline snaps to the ground and goes under trees because leaves are excluded from "ground".
4. Use the crowd tool to put all 20 knights on that path in formation, with random start delays. Set them to sprint.
5. Add attack events where the lines meet. With interaction rules on **Auto**, swings that would land in vanilla deal damage, knock the target back and, when `health` reaches 0, trigger a death. Green lines in the viewport show hits and red lines show misses.
6. Take control of the hero knight with **performance capture** and act out a duel against the scene as it plays.
7. Place TNT behind the wall and key its explosion. Scrub back and forth: the wall breaks and repairs itself.
8. Start a Flashback recording, press **Play for recording**, and do the cameras in Flashback.

## 4. Glossary

| Term | Meaning |
|---|---|
| Scene | A saved document of objects, paths, tracks and settings. A world can hold many scenes, and one is open in the editor at a time. |
| Object | A scene-controlled entity (mob, player, TNT, arrow and so on). The live entity in the world is its **actor**. |
| Channel | One animatable value on an object: float, int, bool, enum, item, text or vector. `health`, `head_yaw` and `on_fire` are channels. |
| Keyframe | A channel value at a tick, with an interpolation mode. |
| Motion path | A spline drawn in the world that objects walk along. |
| Motion clip | A motion path placed on one object's timeline, with a start tick and timing. |
| Event | A one-off action at a tick: attack, hurt, die, explode, shoot, place block, play sound and so on. |
| Interaction rules | Settings that decide what an event does to others. For example, whether an attack in reach deals damage, or whether explosions break blocks. |
| Solver | The pass that turns events and rules into concrete results (health changes, knockback, deaths, destroyed blocks) and writes them to the timeline. |
| Generated keyframe | A keyframe the solver wrote. It is marked in the UI, and editing it by hand detaches it from the solver. |
| Block journal | A record of every block the scene changed and its original state, so every change can be undone. |
| Take | One performance-capture recording. |

## 5. Architecture

### 5.1 The big picture

```
 CLIENT (editor)                            SERVER (integrated server in singleplayer)
 ┌────────────────────────────┐  edit ops   ┌───────────────────────────────────────┐
 │ Editor screen (vanilla GUI) │ ──────────▶ │ SceneManager (authoritative scenes)    │
 │ Tools: place, path, event, │             │   ├─ Solver → generated keys, diffs    │
 │        capture             │ ◀────────── │   ├─ PlaybackClock / SeekEngine        │
 │ Viewport overlays, gizmos  │ scene state │   ├─ ActorController → vanilla entities│
 │ Scrub-time visual overrides│             │   └─ BlockJournal (write-ahead, disk)  │
 └────────────────────────────┘             └───────────────────────────────────────┘
              ▲                                                │
              │  vanilla packets: entity moves, swings, hurts, │
              │  deaths, block updates, explosions, sounds     │
              └────────────────────────────────────────────────┘
                         Flashback / Replay Mod record these
```

The server drives real vanilla entities and makes real block changes every tick. The client receives the same vanilla packets it would get if players and mobs were acting live, which is why recordings need no integration.

### 5.2 Client/server split

Even in singleplayer the world lives on the integrated server thread, so the split is needed anyway:

- **Server:** owns scene data, runs the solver, ticks playback, spawns and drives actors, and applies and reverts block changes.
- **Client:** runs the editor screen, tools, overlays and input. It never changes the world directly.
- **Edit-op protocol:** every edit is a small serializable operation (`AddObject`, `MoveKeyframe`, `InsertPathPoint`, and so on). It is sent to the server as a custom payload, applied there, and echoed back. Undo and redo work as a stack of inverse ops. Multiplayer co-editing later only needs permissions and locking on top.
- Editor payloads flow only while the editor is open. Recordings don't depend on them.

### 5.3 Evaluating the timeline

Scene data comes in three kinds, and each is evaluated differently:

| Kind | Examples | How it is evaluated |
|---|---|---|
| Continuous | position along a path, head yaw, `scale`, `on_fire` | A pure function of time, evaluated directly at any tick. |
| Discrete events | attack, hurt, die, explode, place block | Applied when the playhead crosses them going forward, and reverted when it crosses them going back. |
| Simulated | knockback, TNT and arrow flight, jump and fall arcs | Simulated once with vanilla physics constants and **baked** into keyframes, so they are deterministic and scrubbable. |

**Solver.** Interactions depend on each other: knockback moves a zombie, so a later swing misses it. The solver steps through the scene tick by tick from the earliest changed tick. At each tick it evaluates positions, resolves events under the interaction rules, and writes the results as generated keyframes and events. Per-tick results are cached, so an edit only re-solves from the tick it changed. Each event has its own random seed, so critical hits and explosion rays come out the same every time.

**Virtual world.** The solver reads the world through a view that combines the real world with every block change the scene has made up to that tick. An explosion at tick 200 sees the crater left by one at tick 100, and paths walk into craters correctly.

**Shadow entities.** For vanilla formulas (armor, enchantments, reach hitboxes, explosion exposure), the solver sets up off-world entity instances to match an object's state at that tick. It then calls vanilla code on them instead of reimplementing it.

**Seeking.** To jump to tick T, the engine applies or reverts journal entries between the current tick and T. Then it sets every actor to its evaluated state at T, spawning or removing actors as needed. Nothing is simulated during playback because the solver already baked everything, so seeks are immediate.

### 5.4 Actors: vanilla entities with no mind of their own

Each object spawns as its real entity type, so a zombie object is a `minecraft:zombie`. A server-only actor flag (added with a mixin) switches off everything autonomous:

- No AI, goals, pathfinding or targeting. No physics or gravity, and no pushing by entities, water or pistons.
- Immune to real damage. The `health` channel drives hurt and death instead.
- No despawning, aging (babies stay babies), breeding, item pickup, burning in sunlight, drowning, freeze damage, or conversions (zombie to drowned, villager to witch, pig to zombified piglin, piglin zombification and so on).
- Ambient sounds are a channel: on by default, off to keep a shot quiet.

**No collisions and no outside interaction.** Actors only interact with things set up in the editor. This holds whether or not the editor is open:

- Players and mobs walk straight through actors. Actors don't push anything and can't be pushed, and actors pass through each other. The editor warns when two actors overlap.
- Players can't hit, shoot or right-click actors, even in creative mode. That means no trading, riding, leashing, shearing or name-tagging. The crosshair doesn't target them.
- Real mobs don't see or target actors.
- Projectiles, explosions, lightning, potions, fire and fluids that aren't part of the scene pass through actors or have no effect on them.
- Actors don't trigger pressure plates, tripwires, turtle eggs or any other block. Block interactions happen only through scene events.
- Everything an actor does to the world or to other actors comes from scene events resolved by the solver (6.5).
- The editor can still select actors, because it picks them with its own raycast instead of vanilla targeting.
- Implementation: the server holds the actor flag. The client also needs it so the local player isn't pushed and the crosshair skips actors, so actor entity IDs are synced to modded clients with a small payload. Recordings don't depend on this payload because collisions don't matter during replay playback.

**Visible when the editor is closed.** Closing the editor leaves every object in the world, frozen at the playhead's state, with the no-collision rules above still in force. The scene stays on screen as untouchable set dressing until it is played, reset, or unloaded.
- Actors are **never saved to disk.** They are recreated from the scene when the world loads, so opening the world without the mod leaves no stray entities.
- Movement sets exact positions from the timeline. Walking animations come from vanilla on the client, which animates legs from how far the entity moved. The mod sends step sounds, sprint particles and landing effects itself, based on distance traveled and the block underfoot, so they end up in recordings.
- The tracking range for actors is raised to a per-scene setting so distant fighters still reach the client and the recorder.
- Reviving a dead actor respawns the entity so the client's death state resets cleanly.

### 5.5 Player objects

Player objects use the vanilla **Mannequin** entity (added in 1.21.9). It has the player model, a skin from a profile, the slim or wide arm model, equipment, and poses (standing, crouching, swimming, fall_flying, sleeping). Because it is vanilla, player objects render correctly in recordings with no mod code. Its "NPC" description label is hidden.

- **Skins:** by username, or from a local PNG through a resource pack stored with the world. The local PNG route relies on the profile texture override and is checked in Phase 0.
- **Fallback:** if the Mannequin can't do something needed, such as the bow-draw or eating arm poses, use a Carpet-style fake `ServerPlayer`. It would be kept out of the tab list, sleep checks, mob spawning and chunk loading. The Phase 0 audit decides which parts need it.

### 5.6 World changes and the block journal

- **Every block change** a scene makes goes through the journal. It records the position, the original state with its block-entity data, and the new state. The journal is written to disk *before* the change is applied (`<world>/scene_scripter/journal.dat`), so if the game crashes or quits mid-scene, the world is repaired on the next load.
- **Explosions are precomputed.** The solver runs vanilla explosion rays with the event's seed against the virtual world and produces a **block diff**. Playback applies the diff and sends the vanilla explosion effects. Rewinding applies the reverse. You can paint a protect mask on an explosion to keep specific blocks.
- **No knock-on physics in v1.** Scene block changes don't set off neighbor updates, so sand doesn't fall and water doesn't flow into a crater. Baking those as falling-block objects is a later option.
- **Scene bounds:** a box marking the stage. Chunks inside it stay loaded while a scene plays.
- **Building by hand:** manual block edits are allowed only at the scene's base state, meaning the playhead is before the first block event. Otherwise your build would mix with scene changes. The editor warns you and offers to rewind first.
- **Apply to world:** an explicit action with a confirmation dialog that makes the current scene state permanent.

### 5.7 Client-side visuals and scrubbing

Some vanilla visuals are computed on the client from motion or timers and are never sent by the server: walk-cycle phase, body-turn lag, arm-swing progress, hurt flash, death tilt, and creeper swelling.

- **Linear playback and recording:** vanilla computes these from the packets as usual, so recordings look right with no mod code.
- **Scrubbing in the editor:** when you jump into the middle of a death or a swing, the editor client overrides these values for each actor from the evaluated state, so the viewport matches the timeline exactly. The overrides exist only in the editor.

## 6. Features

### 6.1 The editor

- **Toggle:** Right Ctrl, rebindable in Controls. Opening the editor frees the mouse and shows the panels over the live world. Closing it returns to normal play and keeps the playhead where it was. Objects stay visible but can't be touched (5.4).
- **While editing:** the player flies, can't be hurt, and gameplay input is blocked.
- **UI:** a vanilla full-screen GUI with fixed panels (see section 1 for why not Dear ImGui).
- **Camera:** hold the right mouse button to fly (WASD, Space and Shift for up and down, scroll for speed). Press F to frame the selection.
- **Panels:**
  - *Menu bar:* File (new, open, save, import, export), Edit (undo, redo, preferences), View, Playback, Help.
  - *Toolbar:* Select, Move, Rotate, Place Object, Draw Path, Edit Path, Add Event, Capture, Scene Bounds.
  - *Outliner:* objects, paths and groups, each with visibility, lock and solo toggles.
  - *Inspector:* properties of the selection. Every animatable field has a ◆ button that keys it at the playhead.
  - *Timeline (bottom):* play controls and a ruler in ticks or seconds. Each object gets a row with its motion clips, event markers, and expandable channel rows. Keys can be box-selected, dragged, snapped, copied and pasted. A curve-editor tab handles float channels.
  - *Palette:* a searchable list of entity types to place, plus a skin picker for player objects.
  - *Tool settings:* ground filter, path defaults and interaction rules.
- **Viewport overlays:** paths and handles, onion-skin ghosts at keyframes, event markers in the world, previews of attack reach and explosion radius, hit and miss lines, scene bounds, and warnings such as red path segments.
- **Selection and gizmos:** click actors or path points. Move and rotate gizmos snap to the block grid or half-blocks.
- **Undo and redo:** Ctrl+Z and Ctrl+Y. Undo reverses edits; rewinding moves time.
- **Shortcuts:** Space to play or pause, I to insert a key, Delete, Ctrl+D to duplicate, Home and End to jump to the ends, ←/→ to step one tick, and Shift+←/→ to jump to the previous or next key.

### 6.2 Objects

**Catalog (v1, vanilla only)**

- Players, as Mannequins.
- Every vanilla mob. Each has a **capability descriptor**, a data file that maps its extra channels and events onto vanilla entity data and entity events. Adding descriptors expands coverage without new code.
- Non-living entities: primed TNT, falling block, dropped item, arrow, trident, snowball, fireball, thrown potion, armor stand, boat, minecart, end crystal, lightning bolt, and block, item and text displays.

**Per object**

- Lifetime: a spawn tick and a despawn tick, each with an optional spawn or poof effect.
- Appearance, keyed as step values: variant, baby, custom name, equipment slots (main hand, off hand and armor) and skin.
- Groups: move, offset or retime many objects together.

**First capability descriptors**

| Mob | Extra channels | Extra events |
|---|---|---|
| Zombie family | arms raised | none |
| Skeleton family | aiming | shoot arrow |
| Creeper | ignited, charged | ignite, defuse, explode |
| Iron golem | none | attack swing, offer flower |
| Enderman | carried block, screaming | teleport |
| Wolf | sitting, angry, collar color | shake off water |
| Villager | profession, biome type | head shake |
| Horse | saddle, armor, rearing, eating | none |
| Sheep | wool color, sheared | eat grass |
| Warden | none | roar, sonic boom, emerge, dig |

### 6.3 Channels and keyframes

- **Types:** float, int, bool, enum, item stack, text and vector.
- **Interpolation:** constant (step), linear, Bézier with handles, and easing presets (ease in/out, back, bounce). Bool, enum, item and text channels are step-only. Int channels are step or rounded linear.
- **Time:** keys are stored in whole ticks (20 per second). The UI can show ticks, seconds, or both.

**Built-in channels**

| Channel | Type | What it does |
|---|---|---|
| `position` | vector | Keyframed movement when the object isn't on a motion clip, such as flying or placed props. |
| `body_yaw`, `head_yaw`, `head_pitch` | float | Facing. |
| `look_at` | object or point | Makes the head track a target, overriding head yaw and pitch. |
| `health` | float | Not real health. Starts at the mob's vanilla max health. Auto damage subtracts from it, and 0 or less can trigger death. |
| `dead` | bool | Plays the death animation. Setting it back to false revives the object. |
| `pose` | enum | Standing, crouching, swimming, sleeping, fall_flying and so on. |
| `sneaking`, `sprinting` | bool | Gait. Motion paths set these automatically. |
| `on_fire`, `glowing`, `invisible` | bool | Visual states. |
| `scale` | float | The vanilla `scale` attribute. |
| `custom_name`, `name_visible` | text, bool | Name tag. |
| `equipment.<slot>` | item | One per equipment slot. |
| `ambient_sounds`, `silent` | bool | Sound control. |
| mob-specific | varies | From the capability descriptor. |

**Custom variables:** user-defined int, float and bool channels on an object or on the scene, such as `lives` or `shield_hp`. In v1 they can be used as event conditions, for example "only die when `lives` reaches 0". Drivers that link channels with expressions come later.

### 6.4 Motion paths

**Drawing**

- Click on the ground to place control points, or hold and drag to sketch freehand. A sketch is simplified to a few control points.
- The curve is a centripetal Catmull-Rom spline through the points, which avoids loops and cusps. Any point can switch to Bézier handles for tighter control. Internally, paths are stored as cubic Bézier segments.
- Editing: drag, insert or delete points, and add markers along the path (jump here, wait N ticks, change speed, change gait).

**Ground snapping**

- A path is drawn in plan view (X/Z). Its height comes from projecting each sample down onto "ground".
- **Ground filter** (tool settings, saved per scene, presets can be shared):
  - *What counts as ground:* by default, any block with a solid top collision surface. Slabs, stairs, snow layers, carpet and dirt paths use their real height. Include and exclude lists by block or tag refine this.
  - *Presets:* **Natural ground** excludes leaves so paths pass under trees. **All solid** includes every collidable block. **Custom** is user-defined.
  - *Fluids:* walk along the bottom, swim at the surface, or treat as blocked.
  - *Search window:* how far above and below the previous point's height to look, so a path doesn't jump onto a roof or drop into a cave.
  - *Headroom check:* the object's hitbox has to fit.
- Snapping uses the virtual world at the tick the object reaches each spot, so paths follow craters left by earlier explosions.

**Locomotion**

- A rise no higher than the step height (0.6 blocks by default, from the mob's `step_height` attribute) is walked up.
- A rise higher than that but no higher than the jump height triggers an automatic jump. Arcs use vanilla constants: gravity of 0.08 blocks/tick² and drag of 0.98. The default jump height is about 1.25 blocks. It can be changed, and the launch velocity is solved to reach the chosen height.
- On a drop, the object falls with vanilla gravity. An optional warning flags drops longer than a set height.
- Jump markers make an object jump on flat ground.
- A rise higher than the jump height turns that segment red, and the editor suggests a jump height or a new route.
- Legs animate from movement, so the walk cycle always matches speed.

**Speed and timing**

- Each path has a base speed. Presets include player walk (4.317 m/s), sprint (5.612 m/s), sneak (1.295 m/s), and each mob's vanilla speed.
- Speed keys along the path speed up, slow down, or stop the object. Wait markers add pauses.
- **Timing modes:**
  - *Speed-driven:* you set the start tick and speed, and the end tick follows from them.
  - *Fit to time:* you pin the start and end ticks, and speed is scaled to fit.
- Gait markers switch between walk, sprint (sprint flag and particles), sneak (crouch pose), and swim.

**Facing:** the body turns along the path with vanilla-like smoothing. The head follows the body unless a look-at target is set.

**Other path types**

- **Air paths:** free 3D splines with no snapping, for ghasts, phantoms, bees, allays, and props.
- Water paths (fish, dolphins, drowned) come later.

**Composition**

- An object can have any number of motion clips over time. Between clips it stands still or follows its keyframed position.
- Several objects can share one path, each with a sideways offset and a time delay, to march in formation.
- **Crowd tool:** duplicates N objects along a path with spacing, random speed variation and random start delays.

### 6.5 Events and interactions

**Events (v1)**

| Event | Used by | Effect |
|---|---|---|
| Attack (melee) | players, melee mobs | Arm swing or mob attack animation. Resolves a hit under the interaction rules. |
| Hurt | any living object | Hurt flash and sound, optional knockback direction, optional damage. |
| Die | any living object | Death animation and poof. Sets `dead`. |
| Revive | any living object | Clears `dead` and restores health. |
| Use item start/stop | players, some mobs | Bow draw, shield raise, eating, drinking. |
| Shoot / throw | players, skeletons, pillagers, snow golems, ghasts, blazes and others | Spawns a projectile object with a baked trajectory. It can aim at a point or object at a chosen tick. |
| Ignite / explode | TNT, creepers, end crystals, fireballs | See 6.6. |
| Place / break block | players, endermen | Block change through the journal, with break-progress cracks. |
| Use block | players | Doors, trapdoors, gates, levers, buttons, chest lids. |
| Mount / dismount | any | Riding horses, boats, minecarts, jockeys. |
| Teleport | any | Instant position change, with enderman particles if wanted. |
| Play sound | scene or object | Any sound event. |
| Run command | scene | Runs a command at the tick, with an optional undo command. |

**Interaction rules** are layered: scene defaults, then per-object overrides, then per-event overrides.

| Rule | Options | Default |
|---|---|---|
| Attack resolution | **Auto** (vanilla hit test), **Animation only**, **Always hit** | Auto |
| Hit test (Auto) | Vanilla reach (the player `entity_interaction_range` of 3 blocks, or the mob melee hitbox), line of sight, target in front | On |
| Damage amount | Vanilla calculation (attribute, weapon, enchantments, crit, armor, protection) or a fixed number | Vanilla |
| Critical hits | Auto (vanilla conditions, such as falling and not sprinting), always, never | Auto |
| Knockback | On or off, with a strength multiplier. Baked as a motion offset layered over paths. | On, ×1 |
| Hit cooldown | Respect the vanilla 10-tick invulnerability window | On |
| Auto death | A generated Die event when `health` reaches 0 or less | On |
| Friendly fire | Teams or groups that can't hurt each other | Off |
| Explosions | Break blocks / damage objects / damage real entities / drop items / start fires | On / on / off / off / off |

**How the modes behave**

- **Auto:** you add an attack event, and the solver checks the hit at that tick. If it lands, the target gets a generated hurt event, a `health` drop and knockback, plus a death if health runs out. The viewport shows a green line for a hit and a red dashed line for a miss, with the reason (out of reach, blocked, on cooldown).
- **Animation only:** only the swing plays. You animate the hurt and death yourself with Hurt and Die events or with `health` and `dead` keys.
- **Always hit:** a staging shortcut that ignores reach.
- Generated results are visibly marked. Moving the attacker re-solves automatically. Editing a generated key by hand turns it into a normal key, and the solver stops changing it.

### 6.6 TNT and explosions

- **TNT object:** has a spawn tick and a fuse (80 ticks by default) shown by vanilla flashing. It explodes at spawn tick plus fuse, or at an explicit explode event. It can be thrown or launched, with the trajectory baked.
- **Creeper:** an ignite event starts the vanilla swell, and an explode event removes the creeper. Rewinding brings it back.
- **Explosion power defaults:** TNT 4, creeper 3, charged creeper 6. All editable.
- **Block damage:** a precomputed diff (see 5.6). Blocks that will break are highlighted before playback, and a protect mask keeps chosen blocks intact.
- **Chain reactions** (optional): TNT blocks inside the blast become generated TNT objects with vanilla random fuses, seeded so they repeat exactly.
- **Actors in range** take damage and knockback under the interaction rules, using vanilla exposure calculations.
- The visuals and sound are vanilla, so they record.
- Scrubbing backward restores blocks, and scrubbing forward breaks them again. Stopping the scene or closing the world restores them too.

### 6.7 Performance capture

- **Start:** select an object and press Capture. After a countdown with configurable pre-roll, you control the object while the rest of the scene plays in sync, so you can react to it.
- **Captured every tick:** position, rotation, head, sneak, sprint and swim state, jumps, attacks with the target hit, item use, hotbar and equipment changes, and block interactions.
- **Safety:** you can't be hurt during a take, and block interactions go through the journal.
- **Punch-in/out:** re-record only part of an existing performance.
- **Takes:** each capture is kept as a take. You pick the active take and keep the alternates.
- **Afterwards**, a take can be:
  - kept as raw per-tick samples, thinned down to editable keyframes, or
  - converted to a motion path. The mod fits a spline and speed keys to the movement so the path tools work on it.
  - Captured attacks become normal events and are resolved under the same interaction rules.
- **Loop capture:** re-record the same time range over and over, with each take becoming a new actor. One person can build a crowd this way, which is the direct replacement for "dozens of players acting in sync".

### 6.8 Scene-level tracks

- Time of day, weather (rain and thunder), sounds, and commands.
- Named markers, plus a work range and a loop range for previewing.

### 6.9 Playing and recording

**Workflow**

1. Build the scene in the editor.
2. Start recording in Flashback or Replay Mod.
3. Play the scene with **Play for recording** in the editor (hides overlays, plays at 1×, includes pre-roll) or with `/scene play <name>`, which also works from command blocks.
4. Stop recording, then do cameras and rendering in the recorder.

**Details**

- Recording always plays at 20 TPS and 1× speed. Editor previews can play at 0.25× to 2× and loop.
- Actors spawn during pre-roll so they appear in the recorder's first snapshot.
- Actors use the raised tracking range (5.4), so distant ones are recorded.
- Commands: `/scene play|stop|seek|reset|list`.
- Later: if Flashback exposes a way to start and stop recording, trigger it automatically. This needs investigation.

### 6.10 Files

- Scenes are saved to `<world>/scene_scripter/scenes/<name>.json`, gzipped for large scenes. Each file has a schema version and migrations.
- Files use registry IDs (`minecraft:zombie`), not numeric IDs, so scenes survive game updates.
- Export and import a scene as one file for sharing. Coordinates are relative to an origin marker so a scene can be placed somewhere else.
- Autosave keeps the last N versions as backups.
- `journal.dat` holds the block journal (see 5.6).
- Performance-capture takes are stored next to their scene.

## 7. Data model sketch

```json
{
  "format": 1,
  "name": "castle_siege",
  "length": 2400,
  "origin": [120, 64, -310],
  "bounds": { "min": [-60, -10, -60], "max": [60, 40, 60] },
  "settings": {
    "groundFilter": "natural_ground",
    "rules": { "attack": "auto", "knockback": 1.0, "autoDeath": true,
               "explosions": { "breakBlocks": true, "damageObjects": true } },
    "trackingRange": 160
  },
  "paths": [
    { "id": "p1", "kind": "ground",
      "points": [ { "pos": [0, 0, 0] }, { "pos": [12, 0, 4], "in": [-2, 0, 0], "out": [2, 0, 0] } ],
      "speed": { "base": 4.317, "keys": [ { "at": 0.4, "value": 5.612 } ] },
      "markers": [ { "at": 0.25, "type": "jump" }, { "at": 0.6, "type": "wait", "ticks": 20 } ],
      "jumpHeight": 1.25 }
  ],
  "objects": [
    { "id": "o1", "name": "Knight", "type": "minecraft:mannequin",
      "appearance": { "skin": { "name": "SomePlayer" }, "model": "wide" },
      "life": [0, 2400],
      "motion": [ { "path": "p1", "start": 40, "timing": "speed", "offset": 0.0 } ],
      "channels": {
        "equipment.mainhand": { "keys": [ { "t": 0, "v": "minecraft:iron_sword" } ] }
      },
      "events": [ { "id": "e1", "t": 300, "type": "attack", "target": "o2" } ] },
    { "id": "o2", "name": "Zombie", "type": "minecraft:zombie",
      "life": [0, 2400],
      "channels": {
        "health": { "keys": [ { "t": 0, "v": 20 }, { "t": 300, "v": 13, "generatedBy": "e1" } ] }
      },
      "events": [ { "id": "g1", "t": 300, "type": "hurt", "generatedBy": "e1" } ] }
  ],
  "sceneTracks": { "timeOfDay": { "keys": [] }, "weather": { "keys": [] }, "sounds": [], "commands": [] }
}
```

Path marker positions (`at`) are fractions of path length, so they stay put when the path is reshaped.

## 8. Code layout

```
core/src/main/java/io/github/firestormfmd/scenescripter/core/   plain Java, no Minecraft classes, unit tested
  math/        Vec3, BlockPos, BlockBox, Bezier splines
  anim/        Channel, Keyframe, Interpolation, Handles, ValueType
  scene/       Scene, SceneObject, the scene track, MotionPath and clips, events, rules, settings,
               SceneTransform (relocation), TutorialScene
  edit/        EditOp, Edits, UndoStack
  io/          SceneCodec, EditOpCodec, migrations
  path/        TerrainView, ground probing, jump physics, LocomotionPlanner
  journal/     BlockJournal (write-ahead, reversible block changes)
  runtime/     SceneEvaluator, PlaybackClock, ObjectState, EventWindow
  solve/       Solver, CombatModel, VanillaCombat, Explosions, Ballistics, BlockWorld
  capture/     CaptureSample, Take, Rdp, TakeConverter, TakeCodec
  crowd/       Formation, CrowdBuilder
fabric/src/main/java/io/github/firestormfmd/scenescripter/
  SceneScripter.java   entrypoint, callbacks, payload receivers
  actor/       Actors (the actor flag, tracking range, scene actions), Capabilities (mob-specific channels)
  server/      SceneManager, SceneSession, SceneStorage, ActorController, ActorApplier, EventPlayer,
               VirtualWorld, WorldBlocks, JournalFile, LevelTerrain, WorldCombat, WorldTracks, CaptureSession
  net/         payloads and chunked transfers
  command/     /scene
  mixin/       actor autonomy, collisions, tracking, not saving actors, arrows, swing capture
fabric/src/client/java/io/github/firestormfmd/scenescripter/client/
  editor/      EditorMode, EditorScreen, Ui, EditorCamera, Picking, Outliner, Inspector, Timeline (with the
               curve editor), Viewport (tools), EventTools, EditActions
  render/      EditorOverlay (paths, ghosts, hit lines, blast previews, bounds)
  net/         ClientNet
fabric/src/gametest/   Fabric server game tests and the client game test
tools/inspect/         the inspect-sources workflow's queries
```

**Build:** the non-remapping `net.fabricmc.fabric-loom` Gradle plugin (26.x is unobfuscated, so the project uses Mojang's names directly without Yarn), Java 25, Gradle 9, Fabric API, and split `main`/`client` source sets plus a `gametest` source set. The `core` module is packaged inside the mod jar. The scene model and solver avoid Minecraft classes, which keeps them unit-testable and cheaper to port.

**26.2 notes found while building:** day time is a world clock (`ServerClockManager` with the dimension's default clock) rather than a day-time setter; weather is set on the server with `setWeatherParameters`; the `forced` chunk ticket persists with the world, so scene bounds use a ticket type of their own that is never saved.

## 9. Roadmap

Each phase ends with something usable and has a concrete exit test.

### Phase 0: Foundations and spikes

- Gradle and Loom project for 26.2, a GitHub Actions build, and a mod that loads.
- Four spikes, each answering a risk. If one fails, stop and rethink that part before going on.
  1. **ImGui coexistence:** the ImGui layer runs with Flashback and Axiom installed. (Settled instead by building the editor with vanilla GUI; see section 1.)
  2. **Mannequin audit:** skins by name and by file, equipment, swing, hurt, death, bow draw, eating, riding, sneaking, sleeping. For each, does it work through vanilla packets?
  3. **Recording fidelity:** drive a zombie and a Mannequin along a line with a swing, a hurt, a death and a revive. Record in Flashback and in Replay Mod, then open both recordings with and without Scene Scripter installed.
  4. **Block journal:** apply and revert an explosion diff, confirm the world matches afterwards (chunk hash), and recover correctly after a forced crash.
- **Exit:** each spike is written up with a go or fallback decision.

### Phase 1: Editor shell and static scenes

- Right Ctrl toggle, docking panels, fly camera, selection and gizmos.
- Scene model, saving and loading, the edit-op protocol, and undo/redo.
- Place objects (every vanilla mob, Mannequins, TNT), with the outliner and inspector.
- Channels and keyframes for position, rotation, bools, enums and equipment, with all interpolation modes.
- Timeline with dope sheet, playback and scrubbing, full actor autonomy suppression, and the no-collision rules.
- **Exit:** a scene of posed, keyframed mobs plays, scrubs, saves and reloads. With the editor closed, the mobs stay visible and a player can walk through them, hit at them and shoot at them without affecting them. Nothing is left behind after the world is closed.

### Phase 2: Motion paths

- Drawing and editing ground paths, the ground filter and presets, and locomotion (step, jump, fall).
- Speed keys, timing modes, gaits, wait and jump markers, facing and look-at.
- Path validation, onion-skin ghosts, air paths, and step sounds and particles.
- **Exit:** 20 players and mobs walk, sprint and jump over uneven terrain on cue, and look natural in a Flashback recording.

### Phase 3: Events, interactions and the solver

- The event system: attack, hurt, die, revive, use item, and equipment changes.
- The `health` channel, three-level interaction rules, the solver with generated keys and detaching, and baked knockback.
- Hit and miss previews, hit cooldowns, and friendly fire.
- The first wave of capability descriptors, and the curve editor.
- **Exit:** a 10-vs-10 melee skirmish resolves automatically, and moving one fighter re-solves the fight. The same scene can also be fully hand-animated in animation-only mode.

### Phase 4: Explosions, projectiles and world changes

- TNT, creepers, end crystals and fireballs. The explosion baker with protect mask and preview. Chain reactions.
- Projectiles (arrows, snowballs, potions, tridents) with aim solving.
- Place, break and use block events. Mount and dismount.
- Scene bounds with chunk loading, and Apply to world.
- **Exit:** a castle wall is blown open by TNT while archers fire. It rewinds fully, and the world is identical after a reset.

### Phase 5: Performance capture

- Possessing an object and recording it, countdown and pre-roll, takes, and punch-in.
- Loop capture into new actors, and conversion to keyframes or to a motion path.
- **Exit:** one person builds a 30-actor charge with loop capture in under an hour.

### Phase 6: Production polish and release

- Scene tracks (time, weather, sounds, commands), `/scene` commands, and Play for recording.
- Crowd tools and the remaining capability descriptors.
- Performance work to reach 200+ actors, import and export, docs, and a tutorial scene.
- Port to 26.3 once the recorders support it.
- **Exit:** a 1.0 release on Modrinth.

### Later (after v1)

- Multiplayer co-editing. The protocol already supports it; permissions and locking are needed on top.
- Per-limb posing, camera tracks, modded mobs, and NeoForge.
- Expression drivers, baked block physics (falling sand, flowing water), and water paths.

## 10. Testing

- **Unit tests (JUnit):** spline math, ground projection on synthetic block grids, keyframe interpolation, locomotion arcs against vanilla constants, solver determinism (the same scene always produces the same result hash), journal apply and revert, and JSON round-trips and migrations.
- **Fabric GameTests (server):** actors ignore AI, damage and despawning. Path following hits the expected positions. Auto attacks match vanilla reach. An explosion followed by a rewind leaves the world identical. Actors are never saved to disk.
- **Client game tests:** the editor opens and closes, panels render, and overlay screenshots match references.
- **Manual check per release:** a reference scene recorded with Flashback and with Replay Mod, and each recording opened with and without the mod.
- **Benchmark scene:** 200 actors, tracking TPS and solve time.

## 11. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| ImGui conflicts with Flashback or Axiom (all bundle imgui-java) | The editor can't run next to the recorder | Resolved: the editor uses vanilla GUI, so there is nothing to conflict with. |
| Mannequin lacks some player animations | Player objects look wrong for some actions | Phase 0 audit. Fall back to a fake `ServerPlayer` for those cases. |
| Editor scrubbing looks different from recorded playback | The preview doesn't match the render | Linear playback in the editor uses the same vanilla path as recording. Overrides apply only to seeks. The per-release manual check compares them. |
| A crash mid-scene leaves the world damaged | Lost builds | Write-ahead journal, restore on load, and a backup before the first block event. |
| API changes from 26.2 to 26.3 and later (26.x brought Fabric's largest tooling and API changes so far) | Porting cost | Keep Minecraft-facing code behind small adapters, and keep the scene model and solver free of Minecraft classes. |
| Performance with hundreds of actors | Dropped ticks and choppy recordings | Incremental solving, spatial hashing, and packet batching. Benchmark regularly. |
| Vanilla randomness leaking in (sounds, particles) | Takes differ between runs | Seeded events, and suppress ambient randomness where it matters. |
| Scope | Slow road to a first useful release | Each phase ships something usable. Phase 2 on its own already covers crowd walking shots. |

## 12. Open questions

These are the defaults the plan assumes. Say if any should change.

1. **Explosion block damage.** Blocks really break and are restored on rewind. Should there also be a visual-only option that leaves blocks alone?
2. **Item drops** from explosions and deaths are off by default. Do you want them, as baked item objects?
3. **Building mid-scene.** Manual building is allowed only at the scene's base state (5.6). Is that workable, or do you need to build while the scene is partway through?
4. **Skins.** Is username plus local PNG enough, or do you need other sources, such as a URL or MineSkin?
5. **Scale.** Is 200 actors the right target, or are you aiming larger (500+)?
6. **Apply to world.** Should this exist at all, or should scenes never be able to change the world permanently?

Resolved: objects stay visible when the editor is closed, with no collisions and no outside interaction (see 1 and 5.4).

## References

- [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html) and [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html): Java 25, unobfuscated game, non-remapping Loom.
- [Removing Obfuscation from Fabric](https://fabricmc.net/2025/10/31/obfuscation.html): Yarn discontinued after 1.21.11.
- [Flashback versions](https://modrinth.com/mod/flashback/versions) and [Replay Mod versions](https://modrinth.com/mod/replaymod/versions): supported Minecraft versions.
- [Mannequin (Minecraft Wiki)](https://minecraft.wiki/w/Mannequin): the vanilla player-model entity used for player objects.
