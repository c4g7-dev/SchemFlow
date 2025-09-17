# SchemFlow Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-09-17

### Added
- Initial release of SchemFlow as open-source fork of Skyd.Flow
- Complete namespace migration to `com.skydinse.schemflow`
- Updated branding and documentation for Skydinse project
- S3/MinIO object storage for `.schm` bundles (zip with `.schem` inside)
- In-game upload, fetch, paste, and delete commands
- WorldEdit-style flags: `-e` (entities), `-a` (ignore air), `-b` (biomes)
- Tab completion for subcommands, flags, and schematic names
- Periodic schematic cache refresh and manual cache control
- World provisioning system for automated world creation and pasting
- Skript integration with custom syntax for schematic operations
- Command alias support (`/schem` -> `/SchemFlow`)
- Configuration sanitization for TABs and quotes
- Themed command output with gradients
- Permission system (`schemflow.admin`)

### Changed
- Plugin name from "Skyd.Flow" to "SchemFlow"
- Main command from `/Skyd.Flow` to `/SchemFlow` 
- Permission from `flow.admin` to `schemflow.admin`
- Package namespace from `com.skyd.flow` to `com.skydinse.schemflow`
- Maven artifact from `skyd-flow` to `schemflow`
- Group ID from `com.skyd` to `com.skydinse`
- Author attribution to Skydinse project
- Updated all documentation and help text

### Technical Details
- Java 17+ required
- Compatible with Paper/Purpur 1.20.4+
- WorldEdit 7.2.18+ integration
- Skript 2.7.3+ support (optional)
- MinIO Java SDK 8.5.8
- Adventure API for modern text formatting

---

*This changelog documents the SchemFlow fork. For the original Skyd.Flow changelog, see the upstream repository.*