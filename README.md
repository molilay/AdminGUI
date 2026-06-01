# AdvancedModeratorGUI

<img src="https://img.shields.io/badge/Paper-1.21.4+-blue" alt="Paper"> <img src="https://img.shields.io/badge/LuckPerms-required-orange" alt="LuckPerms"> <img src="https://img.shields.io/badge/license-MIT-green" alt="License">

> Fully GUI-driven moderation plugin for Paper servers with LuckPerms integration. One command — `/mod` — opens the entire control panel.

---

## Features

### 🔧 Moderation
- **Kick, Ban, TempBan, IP-Ban, Unban** with reason selection from configurable presets
- **Freeze / Unfreeze** — blindness + slowness effects, title overlay
- **Mute / Unmute** — blocks chat, `/msg`, `/tell`, `/w` (with duration presets)
- **Warn** — automatic ban on configurable threshold (default 3 warns), Vault fine support
- **Alt detection** — find all accounts sharing an IP, mass-ban them in one click
- **IP-Ban** — bans IP, nickname, and all detected alts simultaneously

### 👁️ Inventory
- **View / Edit** online player inventory and ender chest
- **Offline inventory cache** — inventories saved on quit, browsable when offline
- **Inventory rollback** — snapshot on kick/ban, restore to any saved point
- **Item transfer** — hand item to player with one click

### 👥 Player Management
- **Player card** — full info panel (health, hunger, ping, world, IP, group, warns, mute status)
- **Online / Offline player list** with pagination (up to 1000 offline players)
- **Player search** by name
- **Notes** — per-player moderator notes
- **History** — full punishment history GUI with type filters

### 🛡️ Groups (LuckPerms)
- **Create, rename, delete** groups
- **Edit prefix, suffix, weight, inheritance**
- **Permission editor** — add/remove permissions with pagination
- **Parent group editor** — add/remove inheritance
- **Assign groups** to players (permanent or temporary with duration presets)

### 📋 Logs
- **Punishment log viewer** with type filters (ban, kick, freeze)
- **Player search** within logs
- **Export** to text file
- **Sort** by date (newest/oldest)

### 💬 StaffChat
- Toggle StaffChat via GUI or `/sc` command
- Join/leave notifications for staff

### 🔔 Discord Webhook
- Real-time notifications for all punishments
- Configurable webhook URL in `config.yml`

### 📊 Dashboard
- Server TPS, memory usage
- Today's bans, total bans, active bans, total mutes, warns
- Online staff counter

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/mod` | Open main control panel | `amgui.use` |
| `/mod player <name>` | Open player card directly | `amgui.player` |
| `/mod history <name>` | View punishment history in chat | `amgui.logs` |
| `/mod online` | Open online player list | `amgui.use` |
| `/mod groups` | Open group editor | `amgui.groups` |
| `/sc` / `/staffchat` | Toggle StaffChat | `amgui.staffchat` |
| `/amgui reload` | Reload config | `amgui.admin` |
| `/amgui check` | Check plugin status | `amgui.admin` |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `amgui.*` | OP | All permissions |
| `amgui.use` | OP | Access to `/mod` and main menu |
| `amgui.admin` | OP | Admin commands |
| `amgui.player` | OP | Kick, freeze, teleport |
| `amgui.ban` | OP | Ban, tempban, unban, IP-ban |
| `amgui.freeze` | OP | Freeze/unfreeze players |
| `amgui.mute` | OP | Mute/unmute players |
| `amgui.warn` | OP | Warn players |
| `amgui.groups` | OP | View group list |
| `amgui.groups.edit` | OP | Edit groups |
| `amgui.groups.assign` | OP | Assign groups to players |
| `amgui.inventory.view` | OP | View inventories |
| `amgui.inventory.edit` | OP | Edit inventories |
| `amgui.logs` | OP | View punishment logs |
| `amgui.staffchat` | OP | StaffChat access |
| `amgui.alts` | OP | Alt account detection |
| `amgui.rollback` | OP | Inventory rollback |
| `amgui.teleport` | OP | Teleport to/here |
| `amgui.notes` | OP | Player notes |
| `amgui.viewip` | OP | View player IP addresses |

---

## Dependencies

- **Required:** [LuckPerms](https://luckperms.net/)
- **Optional:** [Vault](https://www.spigotmc.org/resources/vault.34315/) (economic fines for warns)
- **Server:** Paper 1.21.4+ (uses Paper API)

---

## Configuration

All messages, durations, reasons, GUI titles, and toggleable features are configurable in `config.yml`:

```yaml
# Example: custom kick reasons
reasons:
  kick:
    - "Нарушение правил"
    - "Оскорбление"

# Example: ban durations
durations:
  tempban:
    - "1ч"
    - "1д"
    - "7д"
    - "30д"
```

### Discord setup
```yaml
discord:
  webhook-url: "https://discord.com/api/webhooks/..."
```

### Database (SQLite by default)
```yaml
database:
  type: sqlite   # or "mysql"
  mysql:
    host: localhost
    port: 3306
    database: adminmoderator
    username: root
    password: ""
```

---

## Installation

1. Place the JAR in `plugins/`
2. Restart the server
3. Configure `plugins/AdvancedModeratorGUI/config.yml`
4. Grant permissions (e.g. `lp group admin permission amgui.* set true`)
5. Use `/mod` to open the panel

---

## Building from source

```bash
mvn clean package shade:shade
```

Requires JDK 21+ to compile (cross-compiles to Java 21 bytecode). The Paper API 26.1.2 build 66 dependency is fetched automatically.

---

## License

MIT
