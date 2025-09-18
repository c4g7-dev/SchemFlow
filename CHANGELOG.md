# Changelog

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