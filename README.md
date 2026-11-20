# Postie Pete

Collects in-game events like drops and kills and sends them to an API of your choice.

Postie Pete only gathers events. It does not store, display or analyse anything. You run and maintain the API that receives the events, and build whatever you want on top of it: a loot log, kill time charts, a clan feed, and so on.

## Setup

Set **Endpoint URL** in the plugin settings to the full URL that events should be POSTed to, for example `http://localhost:3000/events`. While it is empty, nothing is sent.

Every event is sent as:

```http
POST {Endpoint URL}
Content-Type: application/json
```

Any `2xx` response counts as success. Failed requests are not retried.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| **Endpoint URL** | empty | Full URL that events are POSTed to. Nothing is sent while it is empty. |
| **Loot** › **Send event** | on | Send `loot` events. |
| **Collection log** › **Send event** | on | Send `collection_log` events. |
| **Combat achievements** › **Send event** | on | Send `combat_achievement` events. |

## Events

Every event is a JSON object. All events share the [common fields](#common-fields), and each event type adds its own fields. Use `eventType` to tell event types apart. Every field of an event type is always present; optional values are `null`, never left out.

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

### `loot`

| Field | Type | Description |
| --- | --- | --- |
| `source` | object | What the loot came from: see [Source](#source). |
| `drops` | array | The loot received, as a list of `{ "itemId": number, "itemName": string, "quantity": number, "value": number }`. `itemName` comes from the game's item data and isn't unique, so use `itemId` as the key. `value` is the GE value of **one** item when the drop happened, from the OSRS Wiki real-time prices that RuneLite loads (regardless of the player's "Use wiki item prices" setting). Multiply by `quantity` for the stack value. Untradeable variants that RuneLite maps to a tradeable item (e.g. Sanguine Torva → Torva, noted → unnoted) get that item's value. Other untradeable items are `0`, as is any item if RuneLite hasn't loaded prices yet. Coins are `1` and platinum tokens `1000`. |

#### Source

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The boss, raid or activity. The name in the game's kill count message when one came with the loot (e.g. `"Theatre of Blood: Hard Mode"`, `"Leviathan"`, `"Barrows chest"`), otherwise RuneLite's Loot Tracker name: the NPC for drops (e.g. `"Vorkath"`), or the chest or activity for other loot (e.g. `"Reward cart (Wintertodt)"`, `"Clue Scroll (Hard)"`). |
| `lootName` | string \| null | The Loot Tracker's name when it differs from `name`, e.g. `"Theatre of Blood"` (every raid mode's chest has that name) or `"The Leviathan"` for `"Leviathan"`. `null` when the two are the same, and whenever `killCount` is `null` (`name` is then the Loot Tracker's name). |
| `npcId` | number \| null | The NPC's ID for drops, e.g. `8061` for Vorkath. Some NPCs have several IDs (different forms or locations), so group by `name` if you want them together. `null` for non-NPC loot. |
| `killCount` | number \| null | The kill count after this kill, from the game's kill count message. `null` when no kill count message came with the loot, which is the case for most non-boss NPCs, clue caskets, reward carts etc. So `killCount` is set exactly when `name` came from the kill count message. |
| `duration` | number \| null | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. `null` when the game doesn't report one, which is the case for most non-boss NPCs. Entry mode raids are sent with their time too: `name` tells the mode (e.g. `"Tombs of Amascut: Entry Mode"`), except for the Fractured Archive, whose kill count message is the same for every mode. |
| `personalBest` | number \| null | The personal best in seconds as the game reported it with `duration`: `duration` itself for a new personal best, otherwise the "Personal best:" time in the message. `null` when the game showed none. |
| `newPersonalBest` | boolean | `true` when the game's kill time message said "(new personal best)", otherwise `false`. A time that ties the personal best is not a new one: the game shows it as "Personal best:", so `duration` equals `personalBest` but `newPersonalBest` is `false`. Always `false` when `personalBest` is `null`. |

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-17T14:32:10Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "source": {
    "name": "Mad Angel",
    "lootName": null,
    "npcId": 16305,
    "killCount": 212,
    "duration": 48.6,
    "personalBest": 41.2,
    "newPersonalBest": false
  },
  "drops": [
    { "itemId": 536, "itemName": "Dragon bones", "quantity": 2, "value": 2450 },
    { "itemId": 995, "itemName": "Coins", "quantity": 15000, "value": 1 }
  ]
}
```

### `collection_log`

| Field | Type | Description |
| --- | --- | --- |
| `itemName` | string | The item's name, e.g. `"Abyssal whip"`. Not unique: several collection log items share a name (e.g. the chompy bird hats, Castle Wars items), so use `itemId` to tell them apart. |
| `itemId` | number | The exact item ID of the new collection log item, as the game records it. |

```json
{
  "eventType": "collection_log",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "itemName": "Vorkath's head",
  "itemId": 21907
}
```

### `combat_achievement`

| Field | Type | Description |
| --- | --- | --- |
| `tier` | string | Combat task tier, lower case: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. |
| `task` | string | Combat task name, e.g. `"Noxious Foe"`. |
| `taskId` | number \| null | The game's task ID, e.g. `402`. `null` if the game's message didn't include it. |

```json
{
  "eventType": "combat_achievement",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "tier": "hard",
  "task": "Why Are You Running?",
  "taskId": 402
}
```

Field order in the JSON is not guaranteed.

## Requirements

Some events depend on RuneLite or game settings:

| Event | Requires |
| --- | --- |
| `loot` | RuneLite's **Loot Tracker** plugin enabled (on by default). The plugin warns in chat when it is turned off. |
| `duration` | Only bosses and activities where the game posts a duration or completion time in chat. Timers added by other plugins are not used. |
