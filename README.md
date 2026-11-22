# Postie Pete

Collects in-game events like drops and kills and sends them to an API of your choice.

Postie Pete only gathers events. It does not store, display or analyse anything. You run and maintain the API that receives the events, and build whatever you want on top of it: a loot log, kill time charts, a clan feed, and so on.

## Setup

Set **Endpoint URLs** in the plugin settings to the full URL that events should be POSTed to, for example `http://localhost:3000/events`. To send events to more than one API, separate the URLs with commas, for example `http://localhost:3000/events, https://example.com/osrs`. While it is empty, nothing is sent.

Every event is sent to each URL as:

```http
POST {Endpoint URL}
Content-Type: application/json
```

Any `2xx` response counts as success. Failed requests are not retried.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| **Endpoint URLs** | empty | Full URLs that events are POSTed to, separated by commas. Nothing is sent while it is empty. |
| **Loot** › **Send event** | on | Send `loot` events. |
| **Loot** › **Send gear** | on | Send your equipment, inventory, rune pouch and quiver from the start of the fight with boss kills, as [`playerItems`](#player-items). When off, `playerItems` is always `null`. |
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
| `raid` | object \| null | Raid reward chests only (currently the Fractured Archive): see [Raid](#raid). `null` for all other loot. |
| `drops` | array | The loot received, as a list of `{ "itemId": number, "itemName": string, "quantity": number, "value": number }`. `itemName` comes from the game's item data and isn't unique, so use `itemId` as the key. `value` is the GE value of **one** item when the drop happened, from the OSRS Wiki real-time prices that RuneLite loads (regardless of the player's "Use wiki item prices" setting). Multiply by `quantity` for the stack value. Untradeable variants that RuneLite maps to a tradeable item (e.g. Sanguine Torva → Torva, noted → unnoted) get that item's value. Other untradeable items are `0`, as is any item if RuneLite hasn't loaded prices yet. Coins are `1` and platinum tokens `1000`. |
| `playerItems` | object \| null | Boss kills only: your items when the fight started. See [Player items](#player-items). `null` for other loot, and when **Send gear** is off. |
| `waves` | array \| null | Fortis Colosseum, Doom of Mokhaiotl, Fight Caves and Inferno only: each wave's time. See [Waves](#waves). `null` for all other loot. |
| `team` | object \| null | Boss kills only: how many players were in the fight. See [Team](#team). `null` for other loot and for Wintertodt, Tempoross and Zalcano. |

#### Source

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The boss, raid or activity. The name in the game's kill count message when one came with the loot (e.g. `"Theatre of Blood: Hard Mode"`, `"Leviathan"`, `"Barrows chest"`), otherwise RuneLite's Loot Tracker name: the NPC for drops (e.g. `"Vorkath"`), or the chest or activity for other loot (e.g. `"Reward cart (Wintertodt)"`, `"Clue Scroll (Hard)"`). |
| `lootName` | string \| null | The Loot Tracker's name when it differs from `name`, e.g. `"Theatre of Blood"` (every raid mode's chest has that name) or `"The Leviathan"` for `"Leviathan"`. `null` when the two are the same, and whenever `killCount` is `null` (`name` is then the Loot Tracker's name). |
| `npcId` | number \| null | The NPC's ID for drops, e.g. `8061` for Vorkath. Some NPCs have several IDs (different forms or locations), so group by `name` if you want them together. `null` for non-NPC loot. |
| `killCount` | number \| null | The kill count after this kill, from the game's kill count message. `null` when no kill count message came with the loot, which is the case for most non-boss NPCs, clue caskets, reward carts etc. So `killCount` is set exactly when `name` came from the kill count message, with one exception: a full **Fortis Colosseum** run's chest gets Sol Heredit's kill count but keeps the name `"Fortis Colosseum"`, so full and partial runs stay one source. |
| `duration` | number \| null | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. For boss kills the game prints no time for (e.g. Corporeal Beast, the God Wars Dungeon generals, the Barrows chest), the plugin's own time instead: game ticks (0.6 seconds each) from your first landed hit until the loot, so a multiple of 0.6; for Barrows, from your first hit on the first brother until the chest. `null` when neither is known, which is the case for most non-boss NPCs. Entry mode raids are sent with their time too: `name` tells the mode (e.g. `"Tombs of Amascut: Entry Mode"`), except for the Fractured Archive, whose kill count message is the same for every mode. |
| `personalBest` | number \| null | The personal best in seconds as the game reported it with `duration`: `duration` itself for a new personal best, otherwise the "Personal best:" time in the message. `null` when the game showed none. For a full Fortis Colosseum run it is the personal best from the game's "Colosseum duration:" message. Always `null` for Doom of Mokhaiotl, because the game only reports personal bests per delve level there (work out run personal bests from `duration` instead). |
| `newPersonalBest` | boolean | `true` when the game's kill time message said "(new personal best)", otherwise `false`. A time that ties the personal best is not a new one: the game shows it as "Personal best:", so `duration` equals `personalBest` but `newPersonalBest` is `false`. Always `false` when `personalBest` is `null`. |
| `wave` | number \| null | How far the run got when the loot was claimed, for activities where loot can be claimed before the end: the **Fortis Colosseum** wave (1–12) or the **Doom of Mokhaiotl** delve level (can go past 8). `null` for all other loot. |

For Fortis Colosseum and Doom of Mokhaiotl, `duration` is only set for a full run: waves 1–12 for the Colosseum, delve levels 1–8 for Doom. The Colosseum's is the game's "Colosseum duration:" time, or if that message was missed, the run time the game keeps (then without a personal best). A Doom run that continues past level 8 still gets the time at the end of level 8. Loot claimed earlier has `duration: null` but is otherwise a normal `loot` event.

A few kills come with no loot at the kill: the **Inferno** and the **Fight Caves** (their cape and Tokkul are a reward, not a drop), and **Wintertodt** and **Tempoross** (rewards are claimed separately and aren't one per kill). When no loot comes with their kill count message, they are still sent as a `loot` event with the kill count's name as `name` (e.g. `"TzKal-Zuk"`, `"Wintertodt"`), `lootName: null`, `npcId: null`, the kill's `killCount` and `duration`, and an empty `drops` list.

#### Raid

| Field | Type | Description |
| --- | --- | --- |
| `splits` | array | Each boss's time in the order fought, as `{ "boss": string, "duration": number }` with the boss as the game names it (e.g. `"Bork"`) and seconds. For the Fractured Archive the 2nd to 4th boss are the path picked. Empty when none were seen (e.g. the plugin was turned on mid-raid). |

#### Player items

Sent with boss kills: loot or a kill with no loot that came with a kill count message, and every Fortis Colosseum, Doom of Mokhaiotl and Fractured Archive chest. Regular NPC drops, clue caskets, reward carts etc. have `playerItems: null`.

The items are the ones you had when the fight started, not when the loot arrived, so supplies used during the fight are still there. Each kill of a respawning boss (e.g. Vorkath, GWD generals) has its own start.

| Field | Type | Description |
| --- | --- | --- |
| `capturedAt` | string | How the fight's start was found, tried in this order: `"runStart"` (the start of a Colosseum, Doom, Fight Caves, Inferno, Chambers of Xeric or Fractured Archive run: wave 1, delve level 1, the raid's start or the first boss), `"fightTime"` (the kill time the game printed, counted back from the kill), `"firstHit"` (your first landed hit on the boss, for bosses the game prints no time for; for Barrows, your first hit on the first brother) or `"kill"` (none of these was known, e.g. the plugin was turned on mid-fight, so the items are from when the loot arrived). |
| `equipment` | object | Worn items keyed by slot: `"HEAD"`, `"CAPE"`, `"AMULET"`, `"WEAPON"`, `"BODY"`, `"SHIELD"`, `"LEGS"`, `"GLOVES"`, `"BOOTS"`, `"RING"`, `"AMMO"`. Each value is `{ "id": number, "quantity": number }`. Empty slots are left out. |
| `inventory` | object | Inventory items keyed by slot index, `"0"` to `"27"`, as `{ "id": number, "quantity": number }`. Empty slots are left out. |
| `rune_pouch` | array \| null | The runes in the rune pouch, as `{ "id": number, "quantity": number }`, or `null` if it is empty. The game keeps this even when no pouch is carried, so check the inventory for a rune pouch. |
| `quiver` | object \| null | The ammo in Dizana's quiver, as `{ "id": number, "quantity": number }`, or `null` if empty. The game keeps this even when no quiver is worn, so check the cape. |

#### Waves

Sent with the Fortis Colosseum and Doom of Mokhaiotl chests, whenever they are claimed, and with the `"TzTok-Jad"` (Fight Caves) and `"TzKal-Zuk"` (Inferno) kills. A list of `{ "wave": number, "duration": number }` in the order played. Empty when none were seen (e.g. the plugin was turned on mid-run).

| Field | Type | Description |
| --- | --- | --- |
| `wave` | number | The wave, or the delve level for Doom. Doom levels past 8 are printed as e.g. "8+ (22)"; the level in brackets is used. |
| `duration` | number | The wave's time in seconds. For the Colosseum and Doom, the time the game printed. The Colosseum's wave 12 message shows the whole run's time instead, so wave 12 is that time less waves 1–11, and is left out if any of them is missing. For the Fight Caves and the Inferno, counted in game ticks (0.6 seconds each) from the wave's start message to the next one's, so the time between waves is included. The last wave ends at the kill count message. |

#### Team

Sent with the same boss kills as [Player items](#player-items), except Wintertodt, Tempoross and Zalcano, where the players around are a crowd rather than a team. Only the number of players is sent, never their names.

| Field | Type | Description |
| --- | --- | --- |
| `size` | number | Players in the fight, including you (`1` = solo). For the **Theatre of Blood**, **Tombs of Amascut** and **Fractured Archive**, the number of players in the raid's party at the raid's start, the same moment `playerItems` is from (or, if that isn't known, when the raid was completed). For the **Chambers of Xeric**, the team size in the game's completion message. For every other boss, you plus each other player who was within 15 tiles of you at some point between the start of the fight (see `capturedAt`) and the kill, including players who died or left before the end. In a public area, someone passing close by during the fight counts too. Solo-only content is always `1`. |

A raid chest whose completion wasn't seen (e.g. the plugin was turned on mid-raid) has `team: null`.

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
    "newPersonalBest": false,
    "wave": null
  },
  "raid": null,
  "drops": [
    { "itemId": 536, "itemName": "Dragon bones", "quantity": 2, "value": 2450 },
    { "itemId": 995, "itemName": "Coins", "quantity": 15000, "value": 1 }
  ],
  "playerItems": {
    "capturedAt": "fightTime",
    "equipment": {
      "HEAD": { "id": 11826, "quantity": 1 },
      "WEAPON": { "id": 20997, "quantity": 1 },
      "AMMO": { "id": 11212, "quantity": 1200 }
    },
    "inventory": {
      "0": { "id": 12926, "quantity": 1 },
      "1": { "id": 12791, "quantity": 1 },
      "2": { "id": 6685, "quantity": 1 }
    },
    "rune_pouch": [{ "id": 560, "quantity": 3000 }, { "id": 565, "quantity": 1500 }],
    "quiver": null
  },
  "waves": null,
  "team": { "size": 1 }
}
```

A Fortis Colosseum chest claimed after wave 3:

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-21T19:42:08Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
  "source": {
    "name": "Fortis Colosseum",
    "lootName": null,
    "npcId": null,
    "killCount": null,
    "duration": null,
    "personalBest": null,
    "newPersonalBest": false,
    "wave": 3
  },
  "raid": null,
  "drops": [
    { "itemId": 995, "itemName": "Coins", "quantity": 40000, "value": 1 }
  ],
  "playerItems": {
    "capturedAt": "runStart",
    "equipment": { "WEAPON": { "id": 20997, "quantity": 1 } },
    "inventory": { "0": { "id": 12926, "quantity": 1 } },
    "rune_pouch": null,
    "quiver": null
  },
  "waves": [
    { "wave": 1, "duration": 34.2 },
    { "wave": 2, "duration": 69.6 },
    { "wave": 3, "duration": 81.0 }
  ],
  "team": { "size": 1 }
}
```

A Fractured Archive chest:

```json
{
  "eventType": "loot",
  "timestamp": "2026-11-17T19:05:44Z",
  "rsn": "ExamplePlayer",
  "accountHash": "-3412345678901234567",
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
    "splits": [
      { "boss": "Archive Guardian", "duration": 95.4 },
      { "boss": "Bork", "duration": 141.0 },
      { "boss": "Moia", "duration": 163.2 },
      { "boss": "Xarth", "duration": 152.7 },
      { "boss": "Ocellus", "duration": 210.5 }
    ]
  },
  "drops": [
    { "itemId": 995, "itemName": "Coins", "quantity": 50000, "value": 1 }
  ],
  "playerItems": {
    "capturedAt": "runStart",
    "equipment": { "WEAPON": { "id": 4151, "quantity": 1 } },
    "inventory": {},
    "rune_pouch": null,
    "quiver": null
  },
  "waves": null,
  "team": { "size": 3 }
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
| `duration` | Bosses and activities where the game posts a duration or completion time in chat, or bosses you landed a hit on (timed by the plugin). Timers added by other plugins are not used. |
