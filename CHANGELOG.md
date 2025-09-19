# Changelog

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