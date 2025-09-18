# SchemFlow

[![Release](https://img.shields.io/github/v/release/c4g7-dev/SchemFlow?style=for-the-badge&color=brightgreen)](https://github.com/c4g7-dev/SchemFlow/releases)
[![Downloads](https://img.shields.io/github/downloads/c4g7-dev/SchemFlow/total?style=for-the-badge&color=blue)](https://github.com/c4g7-dev/SchemFlow/releases)
[![License](https://img.shields.io/github/license/c4g7-dev/SchemFlow?style=for-the-badge)](https://github.com/c4g7-dev/SchemFlow/blob/main/SchemFlow/LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Server-Paper%2FPurpur-00ADD8?style=for-the-badge&logo=minecraft)](https://papermc.io/)

## What is it?
SchemFlow is a Paper/Purpur plugin that makes schematic management fast and cloud‑ready. It stores your schematics as compact “.schm” bundles in S3/MinIO and lets you upload, fetch, paste, and provision worlds directly from object storage.
- So syncing Schematics is not an issue any more and so is the size of ur Schmatics!

## Why use it?
- Faster and more scalable than disk‑only workflows.
- Clean in‑game commands with tab completion.
- Works with WorldEdit or FAWE (install one, not both).
- Great for build servers, networks, map distribution, and automated provisioning.

## Before you download
- Minecraft: 1.21+
- Java: 21+
- Server: Paper or Purpur (official support)
- Dependency: WorldEdit 7.2.18+ OR FastAsyncWorldEdit (FAWE) — at least one is required
- Optional: Skript 2.7+ for simple automation

---

## Features
- S3/MinIO storage for `.schm` bundles (zip containing `.schem`)
- In‑game tools: upload, fetch, paste, delete, list, cache refresh
- WorldEdit flags: `-e` (entities), `-a` (ignore air), `-b` (biomes)
- Group-based storage with `-group <name>` targeting and S3 grouping
- Selection helpers: `/SchemFlow pos1`, `/SchemFlow pos2`
- Optional world provisioning on startup
- Clear logs and command feedback

### Group-Based Storage (New)
- Organize schematics by groups using a fixed S3 key layout.
- Path format: `<rootDir>/SF_<group>/SF_<name>.schm`
- Example: `FlowStack/SchemFlow/SF_lobby/SF_castle.schm`
- Group and file prefixes `SF_` are fixed (not configurable).

## Installation
1) Download `SchemFlow-<version>-all.jar`  
2) Place it into `plugins/`  
3) Install WorldEdit OR FAWE (Skript optional)  
4) Start once to generate `plugins/SchemFlow/config.yml`  
5) Configure your S3/MinIO endpoint, credentials, and bucket, then restart

### Minimal config excerpt
```yaml
# plugins/SchemFlow/config.yml
endpoint: "your-minio-server.com:9000"   # or https://s3.your-cloud.com
secure: true                              # HTTPS when using host:port
accessKey: "ACCESS_KEY"
secretKey: "SECRET_KEY"
bucket: "schematics"
extension: "schm"
cacheRefreshSeconds: 60

# Storage hierarchy (S3 object keys)
storage:
  rootDir: "FlowStack/SchemFlow"   # Root prefix in bucket
  defaultGroup: "default"          # Used when -group not provided
```

## Quick Start
- List available bundles: `/SchemFlow list`
- Set selection corners: `/SchemFlow pos1`, `/SchemFlow pos2`
- Upload the current selection:
  - `/SchemFlow upload <id> [-e] [-a] [-b] [-group <name>]`
  - Flags:
    - `-e`: include/paste entities
    - `-a`: ignore air blocks when pasting
    - `-b`: include/preserve biomes
- Paste a bundle at player location:
  - `/SchemFlow paste <name> [-e] [-a] [-b] [-group <name>]`
- Refresh in‑memory cache: `/SchemFlow cache`
- Reload config/services: `/SchemFlow reload`
- Use groups:
  - List groups: `/SchemFlow groups`
  - Create a group: `/SchemFlow group create <name>`
  - Fetch from a group: `/SchemFlow fetch <name> -group <name>`

## Commands (All)
- `/SchemFlow help`: Show all commands and usage
- `/SchemFlow list`: List available `.schm` bundles in the bucket
- `/SchemFlow groups`: List all groups
- `/SchemFlow group create <name>`: Create a new group
- `/SchemFlow fetch <name> [-group <name>]`: Download a bundle to the local cache
- `/SchemFlow pos1`: Set first selection corner at your location
- `/SchemFlow pos2`: Set second selection corner at your location
- `/SchemFlow upload <id> [-e] [-a] [-b] [-group <name>]`: Export current selection and upload
- `/SchemFlow paste <name> [-e] [-a] [-b] [-group <name>]`: Download and paste at your location
- `/SchemFlow delete <name>`: Remove a bundle from storage
- `/SchemFlow cache`: Refresh the in‑memory names cache
- `/SchemFlow reload`: Reload config and re‑init services
- `/SchemFlow provision <world>`: Provision a world from config and paste its base

## Flags
- `-e`: include entities (upload/paste)
- `-a`: ignore air blocks when pasting (paste)
- `-b`: include/preserve biomes (paste)
- `-group <name>`: target a specific group (upload/fetch/paste)

## Permissions
- `schemflow.admin` — full access (default: op)
- `schemflow.help` — view help (default: true)
- `schemflow.list` — list schematics (default: true)
- `schemflow.fetch` — download schematic (default: true)
- `schemflow.pos1` — set first selection corner (default: true)
- `schemflow.pos2` — set second selection corner (default: true)
- `schemflow.upload` — export and upload schematic (default: op)
- `schemflow.paste` — paste schematic at location (default: op)
- `schemflow.delete` — delete schematic from storage (default: op)
- `schemflow.cache` — refresh schematic cache (default: op)
- `schemflow.reload` — reload configuration and services (default: op)
- `schemflow.provision` — provision world base (default: op)
- `schemflow.groups` — list groups (default: op)
- `schemflow.group.create` — create group (default: op)

## Links
- GitHub: https://github.com/c4g7-dev/SchemFlow
- Releases: https://github.com/c4g7-dev/SchemFlow/releases
- Wiki: https://github.com/c4g7-dev/SchemFlow/wiki
- Issues: https://github.com/c4g7-dev/SchemFlow/issues
- Discord: https://discord.gg/eNNbqS4N2H

## License
- Apache-2.0 — see LICENSE and NOTICE in the jar (META-INF). (See <attachments> above for file contents. You may not need to search or read the file again.)
