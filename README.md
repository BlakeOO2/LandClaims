# LandClaims Plugin

A comprehensive land claiming plugin for Minecraft servers running Paper/Spigot 1.21.5+. This plugin allows players to claim and protect their land, manage permissions, and create admin-protected areas.

## Features

### Core Features
- Simple claim creation using a golden shovel
- Automatic block accumulation system
- Claim visualization
- Admin claims
- BlueMap integration
- SQLite database storage
- YAML backup system
- Flight within claims (permission-based)

### Protection Features
- PvP toggling
- Explosion protection
- Fire spread control
- Mob griefing prevention
- Redstone control
- Piston protection
- Hopper control
- Monster spawning control
- Leaf decay control
- Sign interaction control

### Trust System
Three levels of trust for other players:
- ACCESS: Basic interaction rights
- BUILD: Building permissions
- MANAGE: Advanced claim management

### Commands

#### Basic Commands
- `/lc menu` - Open the main claims menu
- `/lc trust <player> [level]` - Trust a player in your claim
- `/lc untrust <player>` - Remove trust from a player
- `/lc show` - Show claim boundaries
- `/lc show nearby` - Show nearby claims
- `/lc unclaim` - Unclaim your current claim
- `/lc info` - View claim information
- `/lc transfer <player>` - Transfer claim ownership
- `/lc stats` - View claim statistics
- `/lc modify` - Modify your claim's boundaries
- `/lc flight` - Toggle flight in your claims

#### Admin Commands
- `/lc admin giveblock <player> <amount>` - Give claim blocks
- `/lc admin setblock <player> <amount>` - Set claim blocks
- `/lc admin menu` - Open claim management
- `/lc admin delete` - Delete the claim you're standing in
- `/lc admin bypass` - Toggle admin bypass mode
- `/lc admin reload` - Reload configuration
- `/lc admin database` - Database management commands
- `/lc admin bluemap` - BlueMap integration commands

### Permissions

#### Basic Permissions
- `landclaims.use` - Basic plugin usage (default: true)
- `landclaims.flight` - Allows flight in claims (default: false)
- `landclaims.notifications` - Claim entry/exit messages (default: true)

#### Admin Permissions
- `landclaims.admin` - Access to admin commands (default: op)
- `landclaims.admin.override` - Bypass claim restrictions (default: op)
- `landclaims.admin.reload` - Reload plugin configuration (default: op)
- `landclaims.admin.unclaim` - Remove others' claims (default: op)
- `landclaims.admin.blocks` - Modify claim blocks (default: op)
- `landclaims.admin.bypass` - Bypass claim restrictions (default: op)
- `landclaims.admin.worldsettings` - Modify world settings (default: op)
- `landclaims.teleport` - Teleport to claims (default: op)
- `landclaims.signs.bypass` - See suppressed sign messages (default: false)

### Configuration

The plugin uses a comprehensive configuration system with the following main sections:

```yaml
claiming:
  tool: GOLDEN_SHOVEL
  admin-tool: GOLDEN_AXE
  default-blocks: 2000
  blocks-per-hour: 500
  max-blocks: 1000000
  minimum-size: 100
  max-claim-size: 1000000

visualization:
  corner-block: GOLD_BLOCK
  border-block: REDSTONE_BLOCK
  admin-corner-block: DIAMOND_BLOCK
  spacing: 10
  duration: 30
  nearby-radius: 50

messages:
  show-claim-enter-exit: true
  claim-enter: "§6[LandClaims] You have entered %owner%'s claim"
  claim-exit: "§6[LandClaims] You have left %owner%'s claim"
  admin-claim-enter: "§6[LandClaims] You have entered an admin protected area"
  admin-claim-exit: "§6[LandClaims] You have left an admin protected area"
