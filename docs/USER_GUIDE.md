# Scene Scripter user guide

Scene Scripter lets you stage and animate a Minecraft scene inside the game: mobs and players that walk, jump, fight, shoot and die on cue, TNT that blows real holes you can rewind, and weather and time that change on a timeline. You then record the scene with Flashback or Replay Mod and do the camera work and rendering there.

This guide walks through a first scene, then covers each tool.

## Before you start

- Minecraft 26.2 with Fabric Loader and Fabric API.
- Put Scene Scripter in your `mods` folder, next to Flashback or Replay Mod if you want to record.
- Use a creative world where you have operator rights. Scene commands need permission level 2.

## Your first scene in five minutes

1. Stand somewhere flat and run `/scene tutorial`. This builds an example scene around you and opens it.
2. Press **Right Ctrl** to open the editor. The mouse is freed and the panels appear over the world.
3. Press **Space** to play. A knight sprints along a path at dusk, a skeleton fires at him, a TNT blows a crater beside the road, he cuts down a zombie, and thunder rolls in.
4. Press **Home** to rewind. The crater fills back in and everyone returns to their start.
5. Click the knight to select him and look at the inspector on the right: his motion clip, the attack events, and the look-at key that turns his head toward the archer.

Everything in the tutorial is made with the tools below, so it is a good scene to take apart.

## The editor

Right Ctrl opens and closes the editor (rebind it under Controls). While it is open:

| Input | Does |
|---|---|
| Hold right mouse | Fly the camera: WASD, Space and Shift or E and Q for up and down, scroll for speed |
| Left click | Select an actor or a path point. A selected actor shows red, green and blue arrows and a ring: drag an arrow to move it along that axis, the ring to turn it, or the actor itself to move it over the ground. Letting go keys the change |
| Space | Play or pause |
| ← / → | Step one tick |
| Shift + ← / → | Jump to the previous or next keyframe or event |
| Home / End | Jump to the start or end of the work range, or of the scene (press again) |
| I | Key the selected actor's position at the playhead |
| R | Turn the selected actor 45° and key it |
| F | Frame the selected actor |
| G | Snap placing, dragging and path points to half blocks or block centres, and turning to 15° steps (press again to change, again to turn off) |
| Ctrl+D | Duplicate the selected object |
| Ctrl+C / Ctrl+V | Copy the selected keyframes / paste them at the playhead |
| Delete | Delete the selection |
| Ctrl+Z / Ctrl+Y | Undo / redo |
| Ctrl+S | Save |
| F1 | Show every shortcut |

The screen has four parts:

- **Top bar:** scenes, save and close, undo and redo, the tools (Select, Place, Path, Blocks), **Bounds**, **Record** and **Apply**.
- **Outliner (left):** the scene track, every object, and every path. Each object has three toggles: **V** hides it, **L** locks it so clicks in the world pass through it, and **S** shows it alone. Hidden objects only disappear while the editor is open, so they are never missing from a recording.
- **Inspector (right):** everything about the selection. A diamond next to a value keys it at the playhead; clicking the value changes it and keys it.
- **Timeline (bottom):** play controls, speed, loop, the ruler (**tick**/**sec** switches its labels), and the selected object's lifetime, motion clips, events and keyframes. Right-drag along the ruler to mark a **work range** (shaded blue): **Loop** plays it over and over, Home and End jump to its ends, and capture records only it. A short right-click on the ruler clears it. Drag keys to move them, right-click a key to change its curve, scroll to zoom. Drag a box on empty track space to select several keys, then drag them together, copy them, or delete them. **Curves** shows the selected number channel as a graph with draggable keys and Bézier handles; click a channel's name to select it first.

When the editor is closed, the scene stays visible, frozen at the playhead. Actors can't be pushed, hit, shot, ridden or targeted by anything that isn't part of the scene.

## Objects

Click **Place** (or **+ Add** in the outliner), choose a type from the palette, and click the ground. Player objects are vanilla Mannequins: set their skin by username in the inspector, or use **Skin file** to take the skin from a resource pack texture (for `myskins:entity/knight`, the pack holds `assets/myskins/textures/entity/knight.png`), and **Arms** to choose slim or wide.

Every object has:

- **A lifetime:** "Spawn here" and "Leave here" set when it appears and disappears. **Appears** and **Leaves** choose whether that happens quietly or in a puff of smoke.
- **Channels:** position, facing, head turn and tilt, look-at (an object or an `x y z` point), scale, pose (including mob poses such as a warden roaring, emerging or digging), sneaking, sprinting, on fire, glowing, invisible, dead, health, name tag, equipment (plus body armor and saddles on horses and wolves), item use (bow draws, eating, shields), and idle sounds.
- **Mob-specific channels** for types that have them: a zombie's raised arms (aggressive), a sheep's wool and shearing, tame animals sitting, an angry wolf and its collar color, a horse rearing or grazing, a villager's profession, biome and head shake, an enderman's carried block and scream.
- **Look switches** for some types: a charged creeper (which also blasts harder), an end crystal's bedrock base, an arrow's critical trail, and **Baby** for mobs that have babies.
- **Content** for props: the block a falling block or block display shows (in command syntax), the item a dropped item or item display shows, the text of a text display, and whether a display turns to face the camera.
- **Variables** of your own: under **Variables**, **Add** takes a name, a type and a starting value, such as `lives int 3`. Types are `int`, `float` and `bool`. Key them like any other channel and use them in event conditions (see below).

Health is not real health. It is a number that attacks subtract from and that can trigger a death at 0 when auto death is on. Turn on **Loot on death** and a mob drops its vanilla loot when it dies; like items from blasts, nobody can pick the loot up, it is never saved, and it goes away when you rewind.

## Motion paths

Choose **Path**, click along the ground to add points, and press **Enter**. Hold the mouse button and drag to sketch a stretch freehand instead; when you let go it is thinned down to the few points needed to follow your stroke. **Tab** switches between a ground path, which follows the terrain, and an air path, which flies. Select an object and use **Walk** in its inspector to put it on the last selected path at the playhead.

Ground paths snap to real blocks. What counts as ground is set with nothing selected, under **Ground for paths**: a preset (natural ground, all solid blocks, or terrain only), blocks or tags that never count, blocks that also count, and whether paths swim, walk along the bottom of water, or treat it as a wall. Objects step up single blocks, jump up higher ledges with a real jump arc, and fall off edges. Select a path to change its gait (walk, sprint, sneak), speed, speed keys, jump height, a warning for drops longer than a height you choose, and wait, jump and gait markers. Problems such as a wall that is too high show in red in the world.

To edit a path, select it and click one of its points (the selected point is drawn larger):

- Drag a point to move it.
- **N** inserts a point halfway to the next one.
- **Delete** removes the point (a path keeps at least two).
- **H** gives the point Bézier handles, drawn in white. Drag either handle to shape the curve; the other mirrors it so the path stays smooth. **H** again returns the point to the automatic curve.

Paths follow craters: an object walking when a blast goes off falls into the hole, and a path that starts later walks through it.

The selected object shows faint ghosts of where it will be a little before and after the playhead, and its events for three seconds either side are marked in the world where it will be when they happen (purple for auto results). An object with attacks also shows how far it can hit from where it stands: a box for mobs, and for players a circle at eye height with a line where they look.

## Events and fights

The inspector's event buttons add an event at the playhead:

- **attack** targets the nearest other object. Under the default rules the solver checks whether the hit would land in vanilla (reach, facing, line of sight, hit cooldown), and if it does, writes the hurt flash, health loss, knockback and death onto the target. Hits show as green arrows and misses as red ones with the reason.
- **hurt**, **die** and **revive** do what they say.
- **shoot** fires the object's usual projectile (arrows for skeletons, snowballs for snow golems, and so on) at the nearest other object. The shot is aimed, leads a moving target, follows vanilla drag and gravity, and hits objects or sticks in walls.
- **explode** sets off TNT, creepers, end crystals and fireballs. **ignite** makes a creeper swell and explode after its fuse.
- **swing** plays an arm swing.
- **Riding** mounts the nearest other object, or dismounts.
- **defuse** (creepers) stops a swell before the fuse runs out, so the creeper doesn't go off.
- **teleport to...** moves the object to `x y z` in one tick, with the vanilla teleport particles. It keys the position, so it works outside motion clips.
- **throw...** throws the object to come to rest at `x y z`, in a baked arc. TNT flies with TNT physics and explodes where it lands; anything else flies like a knocked-back mob.
- **Mob events** appear as extra buttons for the types that have them: an iron golem offering a flower, a sheep eating grass, a wolf shaking off water, a warden's sonic charge, a ravager stunned, hearts, a villager's moods, and more. Iron golems, ravagers, hoglins and wardens use their own attack animation for attack events. The list lives in `data/scenescripter/mob_events.json` inside the mod.

Each event you add is listed in the inspector with a condition button and an **x** to remove it. Click the event itself to edit its own settings as `key=value; key=value`, which override the object's and scene's rules for that event alone: for an attack, `damage=4` (a fixed amount), `knockback=0.5`, `crit=always` or `never`, and `mode=animation_only` or `always_hit`; for an explosion, `breakBlocks=false` or `fire=true`; for an ignite, `fuse=40`. A condition makes the event happen only when it holds at that tick, for example `lives <= 0` or `scene.act == 2`, where `scene.` reads a variable on the scene track. The operators are `<`, `<=`, `==`, `!=`, `>=` and `>`, and `true` or `false` compare switches. Built-in channels such as `health` work too. A condition that can't be read, such as a misspelt name, lets the event happen, so a typo never hides an event.

Results the solver works out are marked "auto" and in purple. Editing one by hand turns it into your own key, and the solver leaves it alone.

**Rules** decide how events resolve: with nothing selected, the inspector shows the scene's rules (attack mode, friendly fire, auto death, and whether blasts break blocks, hurt players, start fires or drop items). Each object can override the attack mode and friendly fire, and objects in the same **group** don't hurt each other unless friendly fire is on. A group can also be moved (**Move group**, by `x y z` blocks) or retimed (**Retime group**, by ticks) in one step.

## TNT and explosions

A TNT object explodes when its fuse runs out (80 ticks after it spawns by default), or at an explode event. Explosions break blocks using the vanilla algorithm with a fixed seed, so the crater is the same every time. TNT blocks caught in a blast become lit TNT with vanilla random fuses. Objects in range are hurt and thrown.

Select the TNT to see every block it will break outlined in orange, and three rings showing how far the blast hurts and throws. Scrubbing back puts the blocks back, and closing the scene or the world restores everything. **Apply** (click twice) keeps the block changes up to the playhead in the world for good.

## Blocks

Choose **Blocks**, select the object that does the work, and click blocks in the world. Keys 1, 2 and 3 switch between breaking, placing and using (doors, trapdoors, gates, levers, buttons), and B sets the block to place, in command syntax such as `minecraft:oak_stairs[facing=east]`. Block events go through the same journal as explosions, so they rewind too.

## Performance capture

Select an object and use **Capture** in the inspector. The editor closes, you are placed where the object is, the scene rewinds by the pre-roll, and a countdown runs while everything plays in sync. Then act the part out: walk, sprint, sneak, jump, swing, draw a bow, eat, raise a shield, switch items, hit other actors, break and place blocks. You can't be hurt, and your block interactions become scene events instead of changing the world. Press **Right Ctrl** to stop.

The take is stored with the scene and applied to the object as keyframes. From the take list in the inspector you can re-apply any take as thinned keys, raw keys every tick, or a motion path fitted to your route.

To record only part of a scene, set a work range on the timeline first; capture then records just that range. **Loop capture** replays the range over and over and turns each pass into a new object, so one person can perform a whole crowd.

## Crowds

Select an object and use **Crowd** in the inspector: choose a formation (line, grid, circle or scatter), the number of extra members and the spacing, and press **Make crowd**. The members copy everything the original does. On paths they walk side by side, rear ranks set off a little later, and each gets a small random delay and walks a little faster or slower, so the crowd doesn't move like one body.

## The scene track

The first row of the outliner is the scene track. It holds:

- **Time of day** and **weather** keys. The world follows them only once they are keyed, and goes back to how it was when the scene closes.
- **Sound** events: a sound ID such as `entity.lightning_bolt.thunder`, heard everywhere.
- **Command** events: a command, optionally followed by `|` and an undo command, such as `setblock 10 64 10 minecraft:gold_block | setblock 10 64 10 minecraft:air`. The undo runs when you rewind past it.
- **Markers:** named points on the timeline.
- **Variables** for the whole scene, read in event conditions as `scene.<name>`.

## Recording

Scene Scripter is tested beside Flashback 0.43.6 and Replay Mod 2.6.27 for Minecraft 26.2.

1. Press **Bounds** once so the scene's chunks stay loaded while it plays.
2. Start recording in Flashback or Replay Mod.
3. Press **Record** in the editor, or run `/scene record`. The editor closes so no overlay shows, the scene rewinds, holds its first frame for two seconds with every actor in place, and plays at normal speed.
4. Stop recording when the scene ends, then do cameras and rendering in the recorder.

From a command block, `/scene run <name>` opens a scene and plays it for recording.

## Commands

| Command | Does |
|---|---|
| `/scene new <name> [ticks]` | Create and open a scene |
| `/scene open <name>`, `/scene close`, `/scene save`, `/scene list`, `/scene delete <name>` | Manage scenes |
| `/scene tutorial [name]` | Create the example scene here |
| `/scene play [speed]`, `/scene pause`, `/scene stop`, `/scene seek <tick>`, `/scene status` | Playback |
| `/scene record [preroll]` | Play from the start for a recorder |
| `/scene run <name>` | Open a scene and play it for recording |
| `/scene reset` | Rewind and restore the world |
| `/scene capture <object> [preroll] [loop]`, `/scene capture stop` | Performance capture |
| `/scene takes`, `/scene take <id> keys\|raw\|path` | List and apply takes |
| `/scene bounds fit`, `/scene bounds <x1 y1 z1 x2 y2 z2>`, `/scene bounds clear` | The stage whose chunks stay loaded |
| `/scene apply`, `/scene apply confirm` | Make block changes permanent |
| `/scene export`, `/scene import <file> [here]` | Share scenes; `here` moves the scene so its origin is where you stand |

## Files

Everything lives in the world folder under `scene_scripter/`:

- `scenes/<name>.json`: the scene (`<name>.json.gz` once it grows past a megabyte). `scenes/<name>.takes.gz`: its performance-capture takes.
- `backups/`: the last five saves of each scene.
- `exports/`: shared scene files for `/scene export` (or **Export** in the scene browser) and `/scene import` (or **Import...** in the scene browser, which places the scene where you stand).
- `journal.json.gz`: what the open scene changed in the world, written before each change, so the world is repaired on the next start if the game stops mid-scene.
