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

Every event is a flat JSON object. All events share the [common fields](#common-fields), and each event type adds its own fields. Use `eventType` to tell event types apart. Every field of an event type is always present; optional values are `null`, never left out.

| Event type | Sent when |
| --- | --- |
| [`loot`](#loot) | You receive loot: an NPC drop, a raid or reward chest, a clue casket etc. |
| [`collection_log`](#collection_log) | A new item is added to your collection log |
| [`combat_achievement`](#combat_achievement) | You complete a combat task |

New event types and new fields may be added in later versions. Ignore event types and fields you don't recognise.

### Common fields

| Field | Type | Description |
| --- | --- | --- |
| `eventType` | string | `"loot"`, `"collection_log"` or `"combat_achievement"`. |
| `timestamp` | string | When the event happened, ISO 8601 in UTC to the second, e.g. `"2026-11-12T20:35:00Z"`. |
| `rsn` | string | The player's current display name. Changes when the player changes name. |
| `accountHash` | string | Stable account identifier that survives name changes. Use this, not `rsn`, to identify a player. A string because it is a 64-bit number. |
| `playerItems` | object | The player's items at the time of the event. See [Player items](#player-items). |

### `loot`

| Field | Type | Description |
| --- | --- | --- |
| `source` | string | Where the loot came from: the NPC name for drops (e.g. `"Vorkath"`), or RuneLite's Loot Tracker name for other loot (e.g. `"Fractured Archive"`, `"Barrows"`, `"Clue Scroll (Hard)"`). |
| `duration` | number \| null | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. `null` when the game doesn't report one, which is the case for most non-boss NPCs. |
| `personalBest` | boolean | `true` when the game reported a new personal best for `duration`, otherwise `false`. |
| `drops` | array | The loot received, as a list of `{ "itemId": number, "quantity": number }`. |

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "source": "Vorkath",
  "duration": 83.4,
  "personalBest": false,
  "drops": [
    { "itemId": 536, "quantity": 2 },
    { "itemId": 995, "quantity": 15000 }
  ]
}
```

### `collection_log`

| Field | Type | Description |
| --- | --- | --- |
| `itemName` | string | The collection log item name, e.g. `"Abyssal whip"`. The name only, no item ID. |

```json
{
  "eventType": "collection_log",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "itemName": "Vorkath's head"
}
```

### `combat_achievement`

| Field | Type | Description |
| --- | --- | --- |
| `tier` | string | Combat task tier, lower case: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. |
| `task` | string | Combat task name, e.g. `"Noxious Foe"`. |

```json
{
  "eventType": "combat_achievement",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "tier": "hard",
  "task": "Why Are You Running?"
}
```

Field order in the JSON is not guaranteed.

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

## Requirements

Some events depend on RuneLite or game settings:

| Event | Requires |
| --- | --- |
| `loot` from chests, caskets and other non-NPC sources | RuneLite's **Loot Tracker** plugin enabled (on by default). NPC drops are sent either way. |
| `collection_log` | The in-game collection log notification set to show in chat. |
| `duration` | Only bosses and activities where the game posts a duration or completion time in chat. Timers added by other plugins are not used. |
