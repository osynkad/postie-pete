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

Older versions had an **API URL** setting that `/events` was appended to. It is converted to **Endpoint URL** automatically, so events keep going to the same URL.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| **Endpoint URL** | empty | Full URL that events are POSTed to. Nothing is sent while it is empty. |
| **Include player items** | on | Send the player's equipment, inventory, rune pouch and quiver with loot, collection log and combat achievement events (snapshots never include them). When off, `playerItems` is `null`. |
| **Loot** › **Send event** | on | Send `loot` events. |
| **Collection log** › **Send event** | on | Send `collection_log` events. |
| **Collection log** › **Send full log when opened** | off | Send a `collection_log_snapshot` event each time you open the collection log in game. |
| **Combat achievements** › **Send event** | on | Send `combat_achievement` events. |
| **Combat achievements** › **Send all completed tasks** | off | Send a `combat_achievement_snapshot` event after you log in, and again when you open the combat achievements interface. |

## Events

Every event is a JSON object. All events share the [common fields](#common-fields), and each event type adds its own fields. Events about something that just happened (`loot`, `collection_log`, `combat_achievement`) also carry the player's items; snapshots don't. Use `eventType` to tell event types apart. Every field of an event type is always present; optional values are `null`, never left out.

| Event type | Sent when |
| --- | --- |
| [`loot`](#loot) | You receive loot: an NPC drop, a raid or reward chest, a clue casket etc. |
| [`collection_log`](#collection_log) | A new item is added to your collection log |
| [`collection_log_snapshot`](#collection_log_snapshot) | You open the collection log (opt-in, off by default) |
| [`combat_achievement`](#combat_achievement) | You complete a combat task |
| [`combat_achievement_snapshot`](#combat_achievement_snapshot) | You log in, or open the combat achievements interface (opt-in, off by default) |

New event types and new fields may be added in later versions. Ignore event types and fields you don't recognise.

### Common fields

| Field | Type | Description |
| --- | --- | --- |
| `eventType` | string | `"loot"`, `"collection_log"`, `"collection_log_snapshot"`, `"combat_achievement"` or `"combat_achievement_snapshot"`. |
| `timestamp` | string | When the event happened, ISO 8601 in UTC to the second, e.g. `"2026-11-12T20:35:00Z"`. |
| `rsn` | string | The player's current display name. Changes when the player changes name. |
| `accountHash` | string | Stable account identifier that survives name changes. Use this, not `rsn`, to identify a player. A string because it is a 64-bit number. |
| `playerItems` | object \| null | `loot`, `collection_log` and `combat_achievement` only: the player's items at the time of the event. See [Player items](#player-items). `null` when **Include player items** is off. Snapshots don't have this field. |

### `loot`

| Field | Type | Description |
| --- | --- | --- |
| `source` | object | What the loot came from: see [Source](#source). |
| `raid` | object \| null | Raid reward chests only (currently the Fractured Archive): see [Raid](#raid). `null` for all other loot. |
| `drops` | array | The loot received, as a list of `{ "itemId": number, "itemName": string, "quantity": number, "value": number }`. `itemName` comes from the game's item data and isn't unique, so use `itemId` as the key. `value` is the GE value of **one** item when the drop happened, from the OSRS Wiki real-time prices that RuneLite loads (regardless of the player's "Use wiki item prices" setting). Multiply by `quantity` for the stack value. Untradeable variants that RuneLite maps to a tradeable item (e.g. Sanguine Torva → Torva, noted → unnoted) get that item's value. Other untradeable items are `0`, as is any item if RuneLite hasn't loaded prices yet. Coins are `1` and platinum tokens `1000`. |

#### Source

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The boss, raid or activity. The name in the game's kill count message when one came with the loot (e.g. `"Theatre of Blood: Hard Mode"`, `"Leviathan"`, `"Barrows chest"`), otherwise RuneLite's Loot Tracker name: the NPC for drops (e.g. `"Vorkath"`), or the chest or activity for other loot (e.g. `"Reward cart (Wintertodt)"`, `"Clue Scroll (Hard)"`). |
| `lootName` | string \| null | The Loot Tracker's name when it differs from `name`, e.g. `"Theatre of Blood"` (every raid mode's chest has that name) or `"The Leviathan"` for `"Leviathan"`. `null` when the two are the same, and whenever `killCount` is `null` (`name` is then the Loot Tracker's name). |
| `npcId` | number \| null | The NPC's ID for drops, e.g. `8061` for Vorkath. Some NPCs have several IDs (different forms or locations), so group by `name` if you want them together. `null` for non-NPC loot. |
| `killCount` | number \| null | The kill count after this kill, from the game's kill count message. `null` when no kill count message came with the loot, which is the case for most non-boss NPCs, clue caskets, reward carts etc. So `killCount` is set exactly when `name` came from the kill count message. |
| `duration` | number \| null | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. `null` when the game doesn't report one, which is the case for most non-boss NPCs. Entry mode raids are sent with their time too: `name` tells the mode (e.g. `"Tombs of Amascut: Entry Mode"`), except for the Fractured Archive, whose kill count message is the same for every mode. |
| `personalBest` | number \| null | The personal best in seconds as the game reported it with `duration`: `duration` itself for a new personal best, otherwise the "Personal best:" time in the message. `null` when the game showed none, and always for Fortis Colosseum and Doom of Mokhaiotl, because the game only reports personal bests per wave or level there (work out run personal bests from `duration` instead). |
| `newPersonalBest` | boolean | `true` when the game's kill time message said "(new personal best)", otherwise `false`. A time that ties the personal best is not a new one: the game shows it as "Personal best:", so `duration` equals `personalBest` but `newPersonalBest` is `false`. Always `false` when `personalBest` is `null`. |
| `wave` | number \| null | How far the run got when the loot was claimed, for activities where loot can be claimed before the end: the **Fortis Colosseum** wave (1–12) or the **Doom of Mokhaiotl** delve level (can go past 8). `null` for all other loot. |

For Fortis Colosseum and Doom of Mokhaiotl, `duration` is only set for a full run: waves 1–12 for the Colosseum, delve levels 1–8 for Doom. A Doom run that continues past level 8 still gets the time at the end of level 8. Loot claimed earlier has `duration: null` but is otherwise a normal `loot` event.

A few kills come with no loot at the kill: the **Inferno** and the **Fight Caves** (their cape and Tokkul are a reward, not a drop), and **Wintertodt** and **Tempoross** (rewards are claimed separately and aren't one per kill). When no loot comes with their kill count message, they are still sent as a `loot` event with the kill count's name as `name` (e.g. `"TzKal-Zuk"`, `"Wintertodt"`), `lootName: null`, `npcId: null`, the kill's `killCount` and `duration`, and an empty `drops` list.

#### Raid

| Field | Type | Description |
| --- | --- | --- |
| `teamSize` | number \| null | Players in the raid when it was completed, including you. `null` when the game didn't report the party. |
| `team` | array \| null | Everyone's display name in the raid, including yours, in party slot order. `null` when the game didn't report the party. |
| `splits` | array | Each boss's time in the order fought, as `{ "boss": string, "duration": number }` with the boss as the game names it (e.g. `"Bork"`) and seconds. For the Fractured Archive the 2nd to 4th boss are the path picked. Empty when none were seen (e.g. the plugin was turned on mid-raid). |
| `purples` | array | Every purple announced in the raid ("found something special"), yours included, in order, as `{ "rsn": string, "item": string }`. Every team member's event has the same list. Empty when nobody got one. |

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-17T14:32:10Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "source": {
    "name": "Mad Angel",
    "lootName": null,
    "npcId": 16305,
    "killCount": 212,
    "duration": 48.6,
    "personalBest": 41.2,
    "newPersonalBest": false,
    "wave": null
  },
  "raid": null,
  "drops": [
    { "itemId": 536, "itemName": "Dragon bones", "quantity": 2, "value": 2450 },
    { "itemId": 995, "itemName": "Coins", "quantity": 15000, "value": 1 }
  ]
}
```

A Fractured Archive chest:

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-17T19:05:44Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": null,
  "source": {
    "name": "Fractured Archive",
    "lootName": null,
    "npcId": null,
    "killCount": 75,
    "duration": 1085.8,
    "personalBest": 1025.2,
    "newPersonalBest": false,
    "wave": null
  },
  "raid": {
    "teamSize": 2,
    "team": ["ExamplePlayer", "Teammate"],
    "splits": [
      { "boss": "Archive Guardian", "duration": 95.4 },
      { "boss": "Bork", "duration": 141.0 },
      { "boss": "Moia", "duration": 163.2 },
      { "boss": "Xarth", "duration": 152.7 },
      { "boss": "Ocellus", "duration": 210.5 }
    ],
    "purples": [
      { "rsn": "Teammate", "item": "Horn of Bran" }
    ]
  },
  "drops": [
    { "itemId": 995, "itemName": "Coins", "quantity": 50000, "value": 1 }
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
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "itemName": "Vorkath's head",
  "itemId": 21907
}
```

### `collection_log_snapshot`

Every item in the collection log, so slots obtained before the plugin was installed can be backfilled. Sent when you open the collection log in game, at most once per opening, and only if the log changed since the last snapshot sent in this session. The plugin briefly opens and closes the log's search to list every item (the same approach as the [Valiance clan plugin](https://github.com/Sicryption/ValianceClanPlugin)). Opening the log is enough: you don't need to click through the pages.

| Field | Type | Description |
| --- | --- | --- |
| `items` | array | Every obtained item, as a list of `{ "itemId": number, "quantity": number }`. `quantity` is the obtained count shown in the collection log. In no particular order. |
| `obtainedSlots` | number | Unique slots obtained, from the game's own counter. A few items are listed under two item IDs on different pages (e.g. the Prospector outfit), so this can be lower than the length of `items`. |

```json
{
  "eventType": "collection_log_snapshot",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "items": [
    { "itemId": 4151, "quantity": 3 },
    { "itemId": 13262, "quantity": 1 }
  ],
  "obtainedSlots": 2
}
```

### `combat_achievement`

| Field | Type | Description |
| --- | --- | --- |
| `tier` | string | Combat task tier, lower case: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. |
| `task` | string | Combat task name, e.g. `"Noxious Foe"`. |
| `taskId` | number \| null | The game's task ID, e.g. `402`: the same IDs as `completedTaskIds` in [`combat_achievement_snapshot`](#combat_achievement_snapshot), so a task can be matched against a snapshot. `null` if the game's message didn't include it. |

```json
{
  "eventType": "combat_achievement",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "playerItems": { "equipment": { "...": "..." }, "inventory": { "...": "..." }, "rune_pouch": null, "quiver": null },
  "tier": "hard",
  "task": "Why Are You Running?",
  "taskId": 402
}
```

### `combat_achievement_snapshot`

Every completed combat task with your points and tier (the example below uses made-up totals and thresholds), so tasks completed before the plugin was installed can be backfilled. Sent a few seconds after you log in and again when you open any combat achievements interface, only if your completed tasks changed since the last snapshot sent in this session. It isn't sent while you have 0 points. Everything is read from values the game sends at login, so no interface is opened or clicked. Task completion is read the same way as the [Valiance clan plugin](https://github.com/Sicryption/ValianceClanPlugin).

| Field | Type | Description |
| --- | --- | --- |
| `completedTaskIds` | array of numbers | The game's internal ID of every completed task, ascending. |
| `completedTasks` | number | Number of completed tasks (the length of `completedTaskIds`). |
| `points` | number | Combat achievement points, from the game's own counter. |
| `tier` | string or null | Highest tier whose points threshold you've reached: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. `null` before reaching easy. |
| `tiers` | array | Progress per tier, easy first, as `{ "tier": string, "completed": number, "total": number, "pointsThreshold": number or null }`. `pointsThreshold` is the points needed for the tier, `null` if the game didn't report it. |

```json
{
  "eventType": "combat_achievement_snapshot",
  "timestamp": "2026-11-12T20:35:00Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "completedTaskIds": [0, 1, 4, 17, 402],
  "completedTasks": 5,
  "points": 7,
  "tier": null,
  "tiers": [
    { "tier": "easy", "completed": 4, "total": 33, "pointsThreshold": 33 },
    { "tier": "medium", "completed": 0, "total": 41, "pointsThreshold": 115 },
    { "tier": "hard", "completed": 1, "total": 66, "pointsThreshold": 304 },
    { "tier": "elite", "completed": 0, "total": 140, "pointsThreshold": 820 },
    { "tier": "master", "completed": 0, "total": 172, "pointsThreshold": 1465 },
    { "tier": "grandmaster", "completed": 0, "total": 147, "pointsThreshold": 2005 }
  ]
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
| `duration` | Only bosses and activities where the game posts a duration or completion time in chat. Timers added by other plugins are not used. |
