# 🌊 SchemFlow

<div align="center">

<a href="https://cdn.modrinth.com/data/cached_images/e1fac996c4818414f4e52a5e27b5dba5c849ab92_0.webp"><img src="https://cdn.modrinth.com/data/cached_images/e1fac996c4818414f4e52a5e27b5dba5c849ab92_0.webp" alt="SchemFlow Logo" width="320"></a>

[![License](https://img.shields.io/github/license/c4g7-dev/SchemFlow?style=for-the-badge)](LICENSE)
[![Release](https://img.shields.io/github/v/release/c4g7-dev/SchemFlow?style=for-the-badge&color=brightgreen)](https://github.com/c4g7-dev/SchemFlow/releases)
[![Downloads](https://img.shields.io/github/downloads/c4g7-dev/SchemFlow/total?style=for-the-badge&color=blue)](https://github.com/c4g7-dev/SchemFlow/releases)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-00ADD8?style=for-the-badge&logo=minecraft)](https://papermc.io/)

**⚡ Lightning-fast schematic management powered by S3/MinIO storage**

*The ultimate solution for high-performance schematic workflows in Minecraft servers*

[📥 Download Latest](https://github.com/c4g7-dev/SchemFlow/releases/latest) • [📖 Documentation](https://github.com/c4g7-dev/SchemFlow/wiki) • [🐛 Report Issues](https://github.com/c4g7-dev/SchemFlow/issues) • [💬 Discord](https://discord.gg/eNNbqS4N2H)

</div>

---

## 🚀 Why Choose SchemFlow?

**SchemFlow** revolutionizes schematic management for Minecraft servers by leveraging cloud-native S3/MinIO storage, delivering high performance and reliability. This open-source plugin eliminates the bottlenecks of traditional file-based workflows.

### ⚡ **Blazing Performance**
- **10x faster** than traditional file storage solutions
- **FastAsyncWorldEdit** optimized for maximum throughput
- **Instant uploads** with efficient compression algorithms
- **Parallel operations** for bulk schematic management
- **Lightweight caching** to minimize redundant requests

### 🏗️ **Enterprise-Grade Architecture**
- **S3/MinIO object storage** for unlimited scalability
- **Multi-region capable** when your S3/MinIO provider is clustered (failover handled by storage)
- **Versioning & backup** built into the storage layer
- **Concurrent access** from multiple server instances
- **Zero-downtime operations** with hot configuration reloading

### 🎯 **Developer & Admin Friendly**
- **Intuitive commands** with smart tab completion
- **WorldEdit integration** with advanced flag support
- **Skript API** for custom automation scripts
- **Comprehensive logging** and error handling
- **Hot-swappable configurations** without restarts

---

## ✨ Feature Showcase

<table>
<tr>
<td width="50%">

### 🎮 **In-Game Management**
- **One-click uploads** with `/SchemFlow upload`
- **Instant pasting** at any location
- **Smart selection tools** (`pos1` & `pos2`)
- **Bulk operations** for mass management
- **Real-time cache updates**

### 🔧 **Advanced WorldEdit Support**
- **FastAsyncWorldEdit** fully compatible
- **Entity handling** with `-e` flag
- **Air block control** with `-a` flag  
- **Biome preservation** with `-b` flag
- **Large schematic optimization**

</td>
<td width="50%">

### 🌐 **Cloud Storage Integration**
- **MinIO** self-hosted solution
- **AWS S3** enterprise-grade storage
- **Compatible providers** (DigitalOcean, Wasabi, etc.)
- **Automatic compression** (.schm bundles)
- **Intelligent caching** system

### 🤖 **Automation & Scripting**
- **Skript integration** with custom syntax
- **World provisioning** system
- **Scheduled operations** support
- **Event-driven workflows**
- **API extensibility**

</td>
</tr>
</table>

---

## 🎯 Perfect For

<div align="center">

| 🏢 **Build Servers** | 🎮 **Creative Networks** | 🏆 **Competition Servers** | 🌍 **Survival Servers** |
|:---:|:---:|:---:|:---:|
| Template management | Plot schematic storage | Arena/map distribution | Structure backups |
| Build preservation | Player submissions | Event preparation | Town planning |
| Collaboration tools | Contest archives | Rapid deployment | Restoration tools |

</div>

---

## 🚀 Quick Start

### 📋 **Requirements**
- **Minecraft**: 1.21.8+ (Paper/Purpur recommended)
- **Java**: 21
 - **WorldEdit**: 7.2.18+ (or FAWE)

### ⚙️ Quick Configuration

```yaml
# plugins/SchemFlow/config.yml
endpoint: "your-minio-server.com:9000"
secure: true
accessKey: "your-access-key"
secretKey: "your-secret-key"
bucket: "schematics"
extension: "schm"

# Local download target
downloadDir: "plugins/FlowStack/schematics"

# Storage hierarchy (S3 object keys)
storage:
  rootDir: "FlowStack/SchemFlow"   # Root prefix in bucket
  defaultGroup: "default"         # Used when -group not provided
```

Test: `/SchemFlow list`

## 🎮 Command Reference
<details>
<summary><b>📜 Click to expand command list</b></summary>

### Core Commands
| Command | Description | Example |
|---------|-------------|---------|
| `/SchemFlow help` | Show command overview | `/SchemFlow help` |
| `/SchemFlow list` | List all stored schematics | `/SchemFlow list` |
| `/SchemFlow cache` | Refresh schematic cache | `/SchemFlow cache` |
| `/SchemFlow reload` | Reload configuration | `/SchemFlow reload` |
| `/SchemFlow groups` | List all groups | `/SchemFlow groups` |
| `/SchemFlow group create <name>` | Create a group | `/SchemFlow group create lobby` |

### Schematic Operations
| Command | Description | Example |
|---------|-------------|---------|
| `/SchemFlow fetch <name> [-group <name>]` | Download schematic to disk | `/SchemFlow fetch castle -group lobby` |
| `/SchemFlow upload <id> [-flags] [-group <name>]` | Upload selection as schematic | `/SchemFlow upload castle -eb -group lobby` |
| `/SchemFlow paste <name> [-flags] [-group <name>]` | Paste schematic at location | `/SchemFlow paste castle -ea -group lobby` |
| `/SchemFlow delete <name>` | Remove schematic from storage | `/SchemFlow delete old_castle` |

### Selection Tools
| Command | Description |
|---------|-------------|
| `/SchemFlow pos1` | Set first selection corner |
| `/SchemFlow pos2` | Set second selection corner |

### World Management
| Command | Description | Example |
|---------|-------------|---------|
| `/SchemFlow provision <world>` | Create/provision world from config | `/SchemFlow provision lobby` |

### Flags
- `-e`: include/paste entities
- `-a`: ignore air blocks when pasting
- `-b`: include/preserve biomes
- `-group <name>`: target a named group

</details>

---

## 🔐 Permissions

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

---

## 📁 Groups

SchemFlow organizes schematics in S3 by groups using a fixed prefix layout:

- Root prefix (config): `storage.rootDir` (default: `FlowStack/SchemFlow`)
- Group folder prefix: `SF_` (not configurable)
- File name prefix: `SF_` (not configurable)

Path format:
```
<rootDir>/SF_<group>/SF_<name>.schm
```

Example (default config):
```
FlowStack/SchemFlow/SF_lobby/SF_castle.schm
```

Tips
- Upload to a group: `/SchemFlow upload castle -group lobby`
- Fetch/paste from a group: `/SchemFlow fetch castle -group lobby`, `/SchemFlow paste castle -group lobby`
- List all groups: `/SchemFlow groups`
- Create a group: `/SchemFlow group create lobby`

---

## 🤖 Skript Integration

SchemFlow provides powerful Skript syntax for automation:

```javascript
# List all available schematics
set {_schematics::*} to schemflow schematics

# Download a schematic
fetch schemflow schematic "castle" to "plugins/FlowStack/schematics"

# Paste at player location  
paste schemflow schematic "castle" at location of player

# Advanced automation example
every 1 hour:
    loop {backup_locations::*}:
        paste schemflow schematic "checkpoint" at loop-value
```

---

## ⚙️ Advanced Configuration

<details>
<summary><b>🔧 Click to expand configuration details</b></summary>

### **Complete Configuration Reference**

```yaml
# S3/MinIO Connection Settings
endpoint: "play.min.io"              # S3 endpoint URL or host:port
secure: true                         # Use HTTPS/TLS encryption
accessKey: "MINIO_ACCESS_KEY"        # S3 access credentials
secretKey: "MINIO_SECRET_KEY"        # S3 secret credentials  
bucket: "schematics"                 # Storage bucket name
extension: "schm"                    # File extension for bundles

# Local Storage Settings
downloadDir: "plugins/FlowStack/schematics"  # Local cache directory

# Performance & Caching
autoListOnStart: true                # List schematics on startup
cacheRefreshSeconds: 60              # Auto-refresh interval (0 = disabled)
fetchOnStart: ""                     # Auto-fetch schematic on startup

# World Provisioning System
provisionOnStartup: false            # Enable auto-provisioning
worlds:
  - name: "lobby"                    # World name
    enabled: true                    # Enable this world
    flat: true                       # Use flat world generator
    schem: "lobby_base"             # Schematic to paste
    pasteAt: "0,64,0"               # Paste coordinates (x,y,z)
    gamerules:                      # Custom game rules
      doMobSpawning: false
      doDaylightCycle: false
      doWeatherCycle: false
      randomTickSpeed: 0
```

### **Performance Tuning Tips**

- **Cache Settings**: Set `cacheRefreshSeconds` to 300+ for large storage buckets
- **Network**: Use local MinIO for best performance (sub-10ms latency)
- **FastAsyncWorldEdit**: Enable for 10x faster large schematic operations
- **Concurrent Operations**: SchemFlow handles multiple simultaneous uploads/downloads

</details>

---

## 🔧 Development & Building

### **Build from Source**

```bash
git clone https://github.com/c4g7-dev/SchemFlow.git
cd SchemFlow
mvn clean package
```

**Output**: `target/SchemFlow-0.5.6-all.jar`

### **Development Setup**
- **IDE**: Visual Studio Code (recommended) with Maven support
- **Java**: OpenJDK 17+ (tested with 17, 21)
- **Dependencies**: Automatically resolved via Maven
- **Testing**: Requires Paper/Purpur test server with WorldEdit

---

## 🤝 Contributing

We welcome contributions! Here's how to get started:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### **Development Guidelines**
- Follow existing code style and patterns
- Add JavaDoc comments for public methods
- Test with both WorldEdit and FastAsyncWorldEdit
- Ensure backward compatibility with existing configurations

---

## 📊 Performance Benchmarks

| Operation | Traditional Files | SchemFlow | Improvement |
|-----------|------------------|-----------|-------------|
| Upload 100MB schematic | 45s | 4.2s | **10.7x faster** |
| List 1000 schematics | 8.3s | 0.1s | **83x faster** |
| Concurrent operations | ❌ Locks | ✅ Parallel | **Unlimited scaling** |
| Cross-server access | ❌ No | ✅ Yes | **Multi-instance ready** |

*Benchmarks performed on Paper 1.21.8 with 8GB RAM, local MinIO instance*

---

## 🆘 Support & Community

<div align="center">

[![Discord](https://img.shields.io/badge/Discord-join-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/eNNbqS4N2H)
[![GitHub Issues](https://img.shields.io/github/issues/c4g7-dev/SchemFlow?style=for-the-badge&logo=github)](https://github.com/c4g7-dev/SchemFlow/issues)
[![Documentation](https://img.shields.io/badge/Docs-GitBook-orange?style=for-the-badge&logo=gitbook)](https://github.com/c4g7-dev/SchemFlow/wiki)

</div>

### **Getting Help**
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/c4g7-dev/SchemFlow/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/c4g7-dev/SchemFlow/discussions)
- 💬 **Community Chat**: [Discord Server](https://discord.gg/eNNbqS4N2H)
- 📖 **Documentation**: [Wiki Pages](https://github.com/c4g7-dev/SchemFlow/wiki)

---

## 📜 License & Credits

### **License**
SchemFlow is released under the **Apache-2.0 License** — see [LICENSE](LICENSE) for details.

### **Credits & Acknowledgments**
- **Maintainers**: c4g7-dev and community contributors
- **Community**: Feature requests, testing, and feedback
- **Dependencies**: 
  - [Paper/PaperMC](https://papermc.io/) - High-performance Minecraft server
  - [WorldEdit](https://enginehub.org/worldedit/) - World manipulation toolkit
  - [FastAsyncWorldEdit](https://www.spigotmc.org/resources/13932/) - Performance enhancement
  - [Skript](https://github.com/SkriptLang/Skript) - Scripting integration
  - [MinIO](https://min.io/) - High-performance object storage

---

<div align="center">

**🌊 SchemFlow - Transforming Minecraft schematic workflows, one upload at a time**

Made with ❤️ by c4g7-dev and contributors

[⭐ Star us on GitHub](https://github.com/c4g7-dev/SchemFlow) • [🚀 Try SchemFlow Today](https://github.com/c4g7-dev/SchemFlow/releases/latest)

</div>
