# Changelog

## 0.5.16 - 2026-10-04
### Fixed — Provisioned maps lost their biomes
- Map exports (`saveRegionAsMap`, `saveSelectionAsMap`, `/SchemFlow savemap`, `/SchemFlow upload`) never asked
  WorldEdit to copy biomes (`ForwardExtentCopy` leaves them out unless told to), so no schematic carried any and
  a provisioned map took the biomes of the empty world it was pasted into: wrong grass, foliage, water and sky
  colours, rain instead of snow. Exports now store every block's biome (3D) and round pastes write them back.
- Only maps exported with 0.5.16 or later carry biomes. **Maps exported before, including maps imported into S3,
  must be re-exported to get their biomes back**; provisioning one logs that its schematic stores no biomes.
- A schematic without biomes leaves the target world's biomes untouched, exactly as before (biomes are pasted
  only when the schematic has them).

### Fixed — Display-entity models vanished from provisioned maps
- Models built from block/item/text displays riding a root entity (marker, interaction, another display) were
  missing in game. FastAsyncWorldEdit writes such a stack twice: the root keeps every passenger, complete, in its
  `Passengers` list, and each passenger is also written as a top-level entry without UUID and without any data
  (FAWE reads entities with `Entity#save`, which writes nothing for an entity that is riding). On paste FAWE loads
  the root without its passengers and spawns the empty entries as default entities: block displays of air, item
  displays without an item, blank text displays. The entity count looked right, the models were gone. Ruled out
  with tests: entity loading (a headless export of a world loaded in the same tick captured every entity),
  coordinates (top-level entities land exactly), 26.x entity NBT, and saving before the world is copied.
- Pastes now spawn each vehicle with its passengers mounted, from the vehicle's own NBT through Paper's entity
  API, and skip the duplicate copies, so the entity count matches the source. Existing schematics are restored as
  they are, no re-export needed. The spawning runs on the main thread in batches (`maps.entitiesPerTick`), after
  the blocks are in and before `provisionRoundWorld` completes, so a world copied right away keeps its models.
  Exports no longer write the empty copies.
- Verified on Purpur 26.2 + FAWE 2.15.4 and Paper 1.21.11 + FAWE 2.15.4 through the real API and MinIO,
  reproducing Conduit's flow (provision, then save + unload + copy the world folder at once, load the copy): a
  scene of 45 entities (3 display models with 32 riders, one of them two deep; standalone displays including a
  textured player head and a `custom_model_data` item; an armor stand and an item frame) and 8 biomes laid out in
  3D. Before: 10/45 entities intact (all 32 riders turned into empty displays, the 3 roots without passengers),
  0/10240 biome cells right. After: 45/45 entities back, 44 identical in every compared field (position, rotation,
  transformation, block/item/text, glow, brightness, billboard, view range, riding) and the 45th differing only in
  its interpolation start delay, which vanilla's own save drops; 10240/10240 biome cells right, also on disk in the
  copy. The same holds for a headless export, a paste with `pasteAtOrigin=false` and an old 0.5.15 schematic
  (models restored, the world's biomes unchanged). A production-sized map (46 models, 1702 displays): 0.5.15
  brought back 0 riding entities and 1104 empty displays, 0.5.16 brings back all 1702 with 1656 riding, for
  35–85 ms of main-thread work (about 140 ms on the first paste after a restart) spread over ~14 ticks; the
  NBT is parsed off the main thread.
### Changed
- `/SchemFlow upload` stores biomes by default, as the `-b` flag was documented (also with `-b`); pastes write
  them only when the schematic has them.
### Added
- `maps:` section in `config.yml` (written into existing configs on startup): `copyBiomes` (default `true`),
  `restorePassengers` (default `true`), `entitiesPerTick` (default `128`, entities spawned per tick).

## 0.5.15 - 2026-09-30
### Fixed — Provisioned round maps came out dark
- Maps provisioned with `provisionRoundWorld` were dark everywhere except right next to light sources.
  FastAsyncWorldEdit relights a paste in the background, but the future completed as soon as the blocks
  were pasted; Conduit then unloaded the world and copied its folder as the game map while that relight was
  still running (FAWE logged `Unable to find org.bukkit.World instance ... Is it loaded?`). The copy had
  every block but no light, and the client only lights blocks near light sources on its own.
- Every SchemFlow paste now relights the pasted chunks (plus a one-chunk border) through the server's light
  engine (Moonrise/Starlight, Paper 1.21+). Off the main thread the paste waits for the relight, so
  `provisionRoundWorld` completes only once the map is lit. The light math runs on the light engine's
  worker threads; the main thread only loads/holds the chunks, in batches.
- Verified on Purpur 26.2 + FAWE 2.15.4 and Paper 1.21.11 by reproducing Conduit's flow (provision, then
  unload + copy the world at once): before, the copy had no light at all (0/900 shaded cells correct);
  after, it matches vanilla lighting (899/900, the remaining cell being a light source block itself). A
  ~960-chunk map relights in 2–4 s.
### Added
- `lighting:` section in `config.yml` (written into existing configs on startup):
  `relightAfterPaste` (default `true`), `maxWaitSeconds` (default `60`, how long provisioning waits for the
  relight before handing the world over anyway), `chunksPerTick` (default `32`, chunk loads per tick).

## 0.5.14 - 2026-06-16
### Fixed — Round provisioning no longer freezes the server (critical)
- `provisionRoundWorld` previously pasted the schematic **synchronously on the main thread** with
  `ignoreAir=false`, and created the world with a generator that triggered a **blocking spawn-point
  search + multi-second "Preparing spawn area" pass**. On a large map this dropped TPS and could trip
  Paper's watchdog ("server stopped responding") even though nothing changed in the caller. Verified on
  Paper 1.21.11 + FAWE: a 95k-block map went from a **~2900 ms main-thread stall (+ watchdog dump)** to
  **~110 ms**, with no lag warnings.
  - Paste now runs **off the main thread under FastAsyncWorldEdit** (falls back to the main thread only
    if plain WorldEdit is installed); the future still completes on the main thread so callers can
    safely teleport in `whenComplete`. Round pastes use `ignoreAir=true` (a fresh world is all void).
  - `EmptyChunkGenerator` now provides a **fixed spawn location** and disables all vanilla generation
    phases, so world creation no longer blocks hunting for solid ground. "Preparing spawn area" drops
    from seconds to ~1 ms.
### Added
- `/SchemFlow savemap <world> <x1 y1 z1> <x2 y2 z2> <group> <name>` and
  `WorldProvisioner.saveRegionAsMap(pos1, pos2, group, name)` → `CompletableFuture<Void>`: headless,
  coordinate-driven map capture preserving the absolute origin (auto-loads a world folder for capture
  and unloads it afterward), for batch-converting pre-built map worlds without an in-game selection.

## 0.5.13 - 2026-06-14
### Added — On-demand provisioning API (called by reflection from external plugins)
- `WorldProvisioner.provisionRoundWorld(worldName, group, schematicName, pasteAtOrigin, gamerules)` → `CompletableFuture<World>`: creates a fresh void world, async-fetches the schematic from S3, pastes it (paste-at-origin = original absolute coordinates), applies gamerules, and completes only once the map is fully pasted. Thread-safe and idempotent per world name.
- `WorldProvisioner.disposeWorld(worldName)` → `CompletableFuture<Void>`: unload (no save) + delete the world folder.
- `WorldProvisioner.inspect(group, schematicName)` → `SchematicInfo` (dimensions + authored min corner) for callers that paste at a fixed point instead of at origin.
- `WorldProvisioner.saveSelectionAsMap(author, group, name)` → `CompletableFuture<Void>`: save a player's pos1/pos2 selection to S3 preserving the absolute world origin (map builder).
- Internals: `WorldEditUtils.readClipboard` / `pasteClipboard` (paste-at-origin or min-at-0,0,0) / `exportCuboidPreserveOrigin`; `SafeIO.deleteRecursively`.

## 0.5.12 - 2025-09-19
### Major Features Added
- **Local Schematics Support**: New `local:` prefix system for offline schematic usage
  - `/schemflow paste local:name` - Use locally fetched schematics without server connection
  - `/schemflow local` - List all locally downloaded schematics  
  - `/schemflow local delete <name> --confirm` - Delete local schematics with confirmation
- **Dedicated Update Command**: Safer schematic overwriting with explicit confirmation
  - `/schemflow update <schematic> --confirm` - Update existing schematics safely
  - Replaces the old `/schemflow upload -update --confirm` pattern
- **Enhanced Tab Completion**: Complete local and server schematic integration
  - Local schematics appear as `local:name` in paste command
  - Server schematics show proper group prefixes (`group:name`)
  - Default group appears without prefix as intended

### Performance Improvements
- **Major Tab Completion Optimization**: Eliminated live S3 calls during tab completion
  - Now uses cached data for instant responsiveness
  - Reduced network requests by 90%+ during command completion
  - Fixed slow tab completion issues with high-latency connections

### User Experience Enhancements  
- **Command Structure Overhaul**: More intuitive command organization
  - Removed `-local` flag complexity in favor of clear `local:` prefix
  - Consistent group naming throughout (matches storage exactly)
  - Complete interface shows all available options in tab completion
- **Default Group Consistency**: Fixed display inconsistencies
  - List command now shows group names exactly as configured and stored
  - No more "Default:" vs "default:" confusion

### Technical Improvements
- **Cache Consistency**: Unified all cache population methods
  - Fixed duplicate entries in tab completion
  - Proper handling of default group vs non-default groups
  - Consistent extension stripping and name processing
- **Code Architecture**: Cleaned up flag parsing and command logic
  - Removed unused methods and redundant code paths
  - Better separation of local vs server operations
  - Enhanced error handling and user feedback

### Configuration
- Cache refresh interval (`cacheRefreshSeconds`) fully functional and optimized
- Default group handling respects `storage.defaultGroup` setting throughout
- Local schematic directory configurable via `downloadDir` setting

## 0.5.11-2 - 2025-09-19
### Fixed
- CI fallback: removed direct dependency on `getEphemeralCacheDir()` in `SchemFlowCommand` (now derives path locally) to avoid build mismatch with older tag snapshots.

## 0.5.11 - 2025-09-19
### Added
- Group management: `/SchemFlow group delete <name> [--confirm]` (with pre-confirmation schematic count)
- Group renaming: `/SchemFlow group rename <old> <new>` (supports case-only renames)
- Root tab completion extended: `undo`, `redo`, `restore`, `trash`, `group delete/rename` context, `restore -group`, `trash clear`
- Confirmation previews: group delete & trash clear show counts before requiring `--confirm`

### Changed
- Paste now uses ephemeral disk cache at `work/cache` purged on enable/reload/disable (no persistent extraction)
- `/SchemFlow undo` & `/SchemFlow redo` delegate directly to WorldEdit when no SchemFlow delete action is pending
- Help text updated with confirm flags & group management details
- Configurable schematic extension limited to `.schem` or `.schematic` (legacy `.schm` bundle support removed)

### Fixed
- Duplicate group creation now properly blocked (case-insensitive check)
- Case-only group renames allowed (reject only exact identical)
- Tab completion adjustments: `paste` flags (no `-group`), `restore -group`, trash root, group subcommands
- Plugin.yml indentation & command key quoting issues resolved; usage updated for confirm flags

### Removed
- Legacy `.schm` bundle handling and related references (only raw WorldEdit formats now)

### Documentation
- README & Wiki updated: ephemeral cache, confirmation behavior, group delete/rename, extension constraints, removal of bundle wording

## 0.5.10-5 - 2025-09-18
- Fixed: plugin.yml YAML indentation under `commands` causing Invalid plugin.yml on load

## 0.5.10 - 2025-09-18
- Changed: Trash is now a flat directory at `<rootDir>/.trash` (no per-group subfolders)
- Added: `/SchemFlow trash clear --confirm` to permanently purge all trashed schematics
- Changed: `restore` restores from flat trash; `-group <dest>` optionally targets the destination group

## 0.5.9 - 2025-09-18
- Added: `undo` / `redo` commands for last paste/delete (delete now moves to trash for safe restore)
- Added: Path-based fetch: `/SchemFlow fetch /path/to/name(.schm)` under `storage.rootDir`
- Added: Skript support for `group:name`, group creation, and trash/restore effects
- Changed: Prohibit `:` and `/` in schematic and group names with helpful errors
- Docs: README and wiki updated (commands, Skript)

## 0.5.8 - 2025-09-18
- Changed: Paste/Delete/Fetch support `group:name` syntax (e.g., `nature:mountain1`)
- Changed: Tab completion for `paste`/`delete` lists ALL schematics, showing default group as plain names and other groups as `group:name`
- Changed: `list` output grouped by group with section headers; cache/reload messages show total and group counts
- Changed: Remove schematic-name prefixing from uploads; keep legacy compatibility when reading existing prefixed objects
- Added: Root path creation on enable/reload; collision check on upload per-group
- Docs: Updated README/command usage and plugin.yml

## 0.5.6 - 2025-09-18
- Added: Group system with S3 hierarchy: `rootDir/SF_<group>/SF_<name>.schm`
- Added: `-group <name>` flag for `upload`, `fetch`, `paste`, `delete`, and support in `list`
- Added: `/SchemFlow groups` and `/SchemFlow group create <name>` commands
- Added: Config keys `storage.rootDir` and `storage.defaultGroup`
- Docs: README, Wiki (Commands/Configuration) updated

## 0.5.5 - 2025-09-18
- Added: Granular permissions per subcommand (`schemflow.*` nodes). Defaults:
	- help/list/fetch/pos1/pos2 are `true`
	- upload/paste/delete/cache/reload/provision are `op`
	- `schemflow.admin` retains full access

## 0.5.2 - 2025-09-17
- Added: bStats metrics (plugin id 27301)
- Changed: Version alignment between tag, jar, and plugin.yml

## 0.5.1 - 2025-09-17
- Added: Initial GitHub release
- Fixed: Java 21 build/runtime and shaded dependencies