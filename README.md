# Postie Pete

Collects in-game events like drops and kills and sends them to an API of your choice.

Postie Pete only gathers events. It does not store, display or analyse anything. You run and maintain the API that receives the events, and build whatever you want on top of it: a loot log, kill time charts, a clan feed, and so on.

## Setup

Set **API URL** in the plugin settings to the base URL of your API, for example `http://localhost:3000`. While it is empty, nothing is sent.

Every event is sent as:

```http
POST {API URL}/events
Content-Type: application/json
```

Any `2xx` response counts as success. Failed requests are not retried.

## Events

Every event has **the same structure**, whatever its type. All fields are always present; fields that don't apply to an event type are `null`. Use `eventType` to tell events apart.

| Event type | Sent when |
| --- | --- |
| `loot` | You receive loot: an NPC drop, a raid or reward chest, a clue casket etc. |
| `collection_log` | A new item is added to your collection log |
| `combat_achievement` | You complete a combat task |

### Schema

| Field | Type | `loot` | `collection_log` | `combat_achievement` |
| --- | --- | --- | --- | --- |
| `eventType` | string | `"loot"` | `"collection_log"` | `"combat_achievement"` |
| `timestamp` | string | ✓ | ✓ | ✓ |
| `rsn` | string | ✓ | ✓ | ✓ |
| `accountHash` | string | ✓ | ✓ | ✓ |
| `source` | string \| null | ✓ | `null` | `null` |
| `duration` | number \| null | ✓ or `null` | `null` | `null` |
| `personalBest` | boolean \| null | ✓ | `null` | `null` |
| `drops` | array \| null | ✓ | `null` | `null` |
| `playerItems` | object | ✓ | ✓ | ✓ |
| `itemName` | string \| null | `null` | ✓ | `null` |
| `tier` | string \| null | `null` | `null` | ✓ |
| `task` | string \| null | `null` | `null` | ✓ |

### Fields

| Field | Description |
| --- | --- |
| `eventType` | `"loot"`, `"collection_log"` or `"combat_achievement"`. |
| `timestamp` | When the event happened, ISO 8601 in UTC to the second, e.g. `"2026-11-12T20:35:00Z"`. |
| `rsn` | The player's current display name. Changes when the player changes name. |
| `accountHash` | Stable account identifier that survives name changes. Use this, not `rsn`, to identify a player. |
| `source` | Where the loot came from: the NPC name for drops (e.g. `"Vorkath"`), or RuneLite's Loot Tracker name for other loot (e.g. `"Fractured Archive"`, `"Barrows"`, `"Clue Scroll (Hard)"`). |
| `duration` | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. `null` when the game doesn't report one, which is the case for most non-boss NPCs. |
| `personalBest` | `true` when the game reported a new personal best for `duration`, otherwise `false`. |
| `drops` | The loot received, as a list of `{ "itemId": number, "quantity": number }`. |
| `playerItems` | The player's items at the time of the event. See [Player items](#player-items). |
| `itemName` | The collection log item name, e.g. `"Abyssal whip"`. The name only, no item ID. |
| `tier` | Combat task tier, lower case: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. |
| `task` | Combat task name, e.g. `"Noxious Foe"`. |

### Player items

```json
{
  "equipment": { "WEAPON": { "id": 4151, "quantity": 1 }, "AMMO": { "id": 11212, "quantity": 120 } },
  "inventory": { "0": { "id": 4151, "quantity": 1 }, "5": { "id": 995, "quantity": 1000 } },
  "rune_pouch": [{ "id": 554, "quantity": 15000 }, { "id": 560, "quantity": 8000 }],
  "quiver": { "id": 21944, "quantity": 1617 }
}
```

| Field | Description |
| --- | --- |
| `equipment` | Worn items, keyed by slot: `HEAD`, `CAPE`, `AMULET`, `WEAPON`, `BODY`, `SHIELD`, `LEGS`, `GLOVES`, `BOOTS`, `RING`, `AMMO`. Empty slots are left out. |
| `inventory` | Inventory items, keyed by slot index `"0"` to `"27"`, left to right and top to bottom. Empty slots are left out. |
| `rune_pouch` | Runes in the rune pouch (3 slots, or 4 for the divine rune pouch), or `null` if empty. |
| `quiver` | The extra ammo slot from Dizana's quiver or the bolt pouch, or `null` if empty. |

## Examples

### `loot`

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "source": "Vorkath",
  "duration": 83.4,
  "personalBest": false,
  "drops": [
    { "itemId": 536, "quantity": 2 },
    { "itemId": 995, "quantity": 15000 }
  ],
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "itemName": null,
  "tier": null,
  "task": null
}
```

### `collection_log`

```json
{
  "eventType": "collection_log",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "source": null,
  "duration": null,
  "personalBest": null,
  "drops": null,
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "itemName": "Vorkath's head",
  "tier": null,
  "task": null
}
```

### `combat_achievement`

```json
{
  "eventType": "combat_achievement",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "source": null,
  "duration": null,
  "personalBest": null,
  "drops": null,
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "itemName": null,
  "tier": "hard",
  "task": "Why Are You Running?"
}
```

## Requirements

Some events depend on RuneLite or game settings:

| Event | Requires |
| --- | --- |
| `loot` from chests, caskets and other non-NPC sources | RuneLite's **Loot Tracker** plugin enabled (on by default). NPC drops are sent either way. |
| `collection_log` | The in-game collection log notification set to show in chat. |
| `duration` | Only bosses and activities where the game posts a duration or completion time in chat. Timers added by other plugins are not used. |
