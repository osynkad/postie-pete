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

Snapshots of your whole collection log and combat achievements aren't events, so they go to their own paths next to each endpoint URL instead: see [Sync](#sync).

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| **Endpoint URLs** | empty | Full URLs that events are POSTed to, separated by commas. Nothing is sent while it is empty. |
| **Loot** › **Send event** | on | Send `loot` events. |
| **Loot** › **Send gear** | on | Send your equipment, inventory, rune pouch and quiver from the start of the trip with boss kills, as [`playerItems`](#player-items). When off, `playerItems` is always `null`. |
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
| `source` | object | What the loot came from and how the kill went: see [Source](#source). |
| `drops` | array | The loot received, as a list of `{ "itemId": number, "itemName": string, "quantity": number, "value": number }`. `itemName` comes from the game's item data and isn't unique, so use `itemId` as the key. `value` is the GE value of **one** item when the drop happened, from the OSRS Wiki real-time prices that RuneLite loads (regardless of the player's "Use wiki item prices" setting). Multiply by `quantity` for the stack value. Untradeable variants that RuneLite maps to a tradeable item (e.g. Sanguine Torva → Torva, noted → unnoted) get that item's value. Other untradeable items are `0`, as is any item if RuneLite hasn't loaded prices yet. Coins are `1` and platinum tokens `1000`. |
| `playerItems` | object \| null | Boss kills only: your items at the start of the trip. See [Player items](#player-items). `null` for other loot, and when **Send gear** is off. |
| `team` | object \| null | Boss kills only: how many players were in the fight. See [Team](#team). `null` for other loot and for Wintertodt, Tempoross and Zalcano. |

#### Source

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The boss, raid or activity. The name in the game's kill count message when one came with the loot (e.g. `"Theatre of Blood: Hard Mode"`, `"Leviathan"`, `"Barrows chest"`), otherwise RuneLite's Loot Tracker name: the NPC for drops (e.g. `"Vorkath"`), or the chest or activity for other loot (e.g. `"Reward cart (Wintertodt)"`, `"Clue Scroll (Hard)"`). |
| `lootName` | string \| null | The Loot Tracker's name when it differs from `name`, e.g. `"Theatre of Blood"` (every raid mode's chest has that name) or `"The Leviathan"` for `"Leviathan"`. `null` when the two are the same, and whenever `killCount` is `null` (`name` is then the Loot Tracker's name). |
| `npcId` | number \| null | The NPC's ID for drops, e.g. `8061` for Vorkath. Some NPCs have several IDs (different forms or locations), so group by `name` if you want them together. `null` for non-NPC loot. |
| `killCount` | number \| null | The kill count after this kill, from the game's kill count message. `null` when no kill count message came with the loot, which is the case for most non-boss NPCs, clue caskets, reward carts etc. An NPC drop only takes a kill count message that came on the same game tick (a tick of slack allowed), so another activity's count (e.g. an agility lap count) is never attached to it. So `killCount` is set exactly when `name` came from the kill count message, with one exception: a full **Fortis Colosseum** run's chest gets Sol Heredit's kill count but keeps the name `"Fortis Colosseum"`, so full and partial runs stay one source. |
| `duration` | number \| null | Kill or completion time in seconds, e.g. `83.4`, as reported by the game. For boss kills the game prints no time for (e.g. Corporeal Beast, the God Wars Dungeon generals, the Barrows chest), the plugin's own time instead: game ticks (0.6 seconds each) from your first landed hit until the loot, so a multiple of 0.6; for Barrows, from your first hit on the first brother until the chest. `null` when neither is known, which is the case for most non-boss NPCs, and always for clue caskets, reward carts and other loot that isn't a kill: a kill time is only used if it came with the loot (within 30 game ticks), except for raid chests and the Colosseum and Doom chests, which are opened later and take the time printed up to 10 minutes before. A raid's completion time only goes on its own chest, never on loot that comes in between (e.g. the Patient Record Verzik drops to one player in the Theatre of Blood). Entry mode raids are sent with their time too: `name` tells the mode (e.g. `"Tombs of Amascut: Entry Mode"`), except for the Fractured Archive, whose kill count message is the same for every mode. |
| `personalBest` | number \| null | The personal best in seconds as the game reported it with `duration`: `duration` itself for a new personal best, otherwise the "Personal best:" time in the message. `null` when the game showed none. For a full Fortis Colosseum run it is the personal best from the game's "Colosseum duration:" message. Always `null` for Doom of Mokhaiotl, because the game only reports personal bests per delve level there (work out run personal bests from `duration` instead). |
| `newPersonalBest` | boolean | `true` when the game's kill time message said "(new personal best)", otherwise `false`. A time that ties the personal best is not a new one: the game shows it as "Personal best:", so `duration` equals `personalBest` but `newPersonalBest` is `false`. Always `false` when `personalBest` is `null`. |
| `wave` | number \| null | How far the run got, for wave activities: the **Fortis Colosseum** wave when the loot was claimed (1–12), the **Doom of Mokhaiotl** delve level (can go past 8), and the **Fight Caves** / **Inferno** wave at the kill (63 / 69 for a full run). Doom's comes from the game's loot data, so it is always known. The others come from the last wave message the plugin saw, which can be later than the last split (a wave whose time couldn't be read has no split), and are `null` if no wave message was seen. `null` for all other loot. Use this rather than counting splits. |
| `splits` | array \| null | Fortis Colosseum, Doom of Mokhaiotl, Fight Caves, Inferno, Chambers of Xeric, Theatre of Blood, Tombs of Amascut and Fractured Archive only: the run's waves, delve levels, levels, rooms or bosses with their times. See [Splits](#splits). `null` for all other loot, and when none were seen. |
| `points` | object \| null | Chambers of Xeric (Challenge Mode included) and Tombs of Amascut (every mode) only: the raid's points when it was completed, which set the chance of a unique, as `{ "total": number \| null, "personal": number }`. `total` is the whole party's points, `personal` your own. The game doesn't send the Tombs of Amascut's party total, so its `total` is always `null`; its `personal` is what the game sends at the end of the raid. `null` for all other loot, and when the raid's completion wasn't seen. |
| `invocation` | number \| null | Tombs of Amascut only (every mode): the raid's invocation level, e.g. `300`, as the game keeps it when the Wardens are defeated. `null` for all other loot, and when the raid's completion wasn't seen. |

For Fortis Colosseum and Doom of Mokhaiotl, `duration` is only set for a full run: waves 1–12 for the Colosseum, delve levels 1–8 for Doom. The Colosseum's is the game's "Colosseum duration:" time, or if that message was missed, the run time the game keeps (then without a personal best). A Doom run that continues past level 8 still gets the time at the end of level 8. Loot claimed earlier has `duration: null` but is otherwise a normal `loot` event.

A few kills come with no loot at the kill: the **Inferno** and the **Fight Caves** (their cape and Tokkul are a reward, not a drop), and **Wintertodt** and **Tempoross** (rewards are claimed separately and aren't one per kill). When no loot comes with their kill count message, they are still sent as a `loot` event with the kill count's name as `name` (e.g. `"TzKal-Zuk"`, `"Wintertodt"`), `lootName: null`, `npcId: null`, the kill's `killCount` and `duration`, and an empty `drops` list. The Fight Caves and the Inferno also get their `wave` and `splits`.

#### Splits

The parts of a run in the order played, as a list of `{ "name": string, "duration": number }`. Sent with the Fortis Colosseum, Doom of Mokhaiotl, Chambers of Xeric, Theatre of Blood, Tombs of Amascut and Fractured Archive chests, whenever they are claimed, and with the `"TzTok-Jad"` (Fight Caves) and `"TzKal-Zuk"` (Inferno) kills. `null` for all other loot, and also when none were seen (e.g. the plugin was turned on mid-run): use `source.name` to tell the two apart.

Parts the plugin missed are left out, so the list can have gaps. Don't count splits to find how far a run got: use `wave`.

| Field | Type | Description |
| --- | --- | --- |
| `name` | string | The part of the run. Waves are `"Wave <n>"` (e.g. `"Wave 3"`) and Doom delve levels `"Level <n>"` (e.g. `"Level 22"`; levels past 8 are printed as e.g. "8+ (22)" and the level in brackets is used), so the number can be parsed from the name. Bosses, Theatre of Blood rooms and Tombs of Amascut challenges are named as the game names them (e.g. `"Bork"`, `"The Pestilent Bloat"`, `"Path of Het"`). |
| `duration` | number | The part's time in seconds. What it covers depends on the content, see below. |

What `duration` covers, and whether the splits add up to `source.duration`:

| Content | `name` | `duration` | Adds up to `source.duration` |
| --- | --- | --- | --- |
| Fortis Colosseum | `"Wave 1"` – `"Wave 12"` | The time the game printed for the wave. Wave 12's message shows the whole run's time instead, so wave 12 is that time less waves 1–11, and is left out if any of them is missing. | Yes, for a full run with every wave seen, since wave 12 is worked out from the run time. |
| Doom of Mokhaiotl | `"Level 1"`, `"Level 2"` … | The time the game printed for the delve level. | No: `source.duration` stops at the end of level 8, while splits go on to the deepest level reached. |
| Fight Caves, Inferno | `"Wave 1"` – `"Wave 63"` / `"Wave 69"` | Counted in game ticks (0.6 seconds each) from the wave's start message to the next one's, so the time between waves is included. The last wave ends at the kill count message. | Close to it, when every wave was seen. |
| Chambers of Xeric | the rooms (e.g. `"Tekton"`, `"Ice demon"`, see [below](#chambers-of-xeric-rooms)) and levels (e.g. `"Upper level"`, `"Lower level"`), then `"Great Olm"`, in the order completed: each level after its rooms | The game prints the raid's time so far at the end of each level, so a level's time is that less the level before's, and the Great Olm's is the raid's time less the last level's. If the raid's start wasn't seen, the first level seen is left out, and the Great Olm if no level or no raid time was. Challenge Mode is the same. | The levels and `"Great Olm"` do, when the whole raid was seen. The rooms overlap their level, so leave them out when adding up. |
| Theatre of Blood | the room: `"The Maiden of Sugadinti"`, `"The Pestilent Bloat"`, `"The Nylocas"`, `"Sotetseg"`, `"Xarpus"`, `"The Final Challenge"` (Verzik) | The room's time as the game printed it, in every mode. | Yes, when every room was seen: `source.duration` is the game's completion time, which leaves out the time between rooms (not its total completion time, which includes it). |
| Tombs of Amascut | the challenge, in the order done: the paths `"Path of Apmeken"`, `"Path of Crondis"`, `"Path of Het"`, `"Path of Scabaras"`, their bosses `"Ba-Ba"`, `"Zebak"`, `"Akkha"`, `"Kephri"`, then `"The Wardens"` | The challenge's time as the game printed it, in every mode. The raid has no start message, so a new raid is noticed from its first challenge, whose total is its own time. | Yes, when every challenge was seen: `source.duration` is the game's challenge completion time, which leaves out the time between challenges (not its total completion time, which includes it). |
| Fractured Archive | the boss, e.g. `"Archive Guardian"`, `"Bork"` | The boss's time as the game printed it. The 2nd to 4th boss are the path picked. | No: the time between bosses isn't included. |

#### Chambers of Xeric rooms

The game doesn't print room times, so the plugin works them out the way the CoX Timers hub plugin does: a room is complete when its crystal or barrier stops blocking the way on. Rooms and levels come in one list, in the order they were completed, so each level's split comes right after its rooms:

```json
"splits": [
  { "name": "Tekton", "duration": 218.4 },
  { "name": "Ice demon", "duration": 240.0 },
  { "name": "Upper level", "duration": 520.2 },
  { "name": "Vasa", "duration": 180.0 },
  { "name": "Middle level", "duration": 280.2 },
  ...
  { "name": "Great Olm", "duration": 199.8 }
]
```

Levels end in `" level"` and the last split is `"Great Olm"`; every other split is a room.

- **Names:** `"Tekton"`, `"Muttadiles"`, `"Vanguards"`, `"Vasa"`, `"Vespula"`, `"Guardians"`, `"Mystics"`, `"Shamans"` (combat), and `"Ice demon"`, `"Thieving"`, `"Tightrope"`, `"Crabs"` (puzzles). Scavengers and farming rooms have nothing to complete, so they never appear.
- **`duration`:** counted with the raid's timer, from the last room's completion (or the level's start) to this room's, so it includes getting to the room and any scavenging on the way. A room whose crystal opened while it wasn't in view (e.g. a teammate cleared it on the other side of the level) is left out, and its time goes to the next room. If the raid's start wasn't seen, the first room seen is left out too.
- **Adding up:** a level's rooms add up to less than the level's `duration`, since the time from its last room to the level's end isn't in any room. The levels and `"Great Olm"` add up to `source.duration`.

#### Player items

Sent with boss kills: loot or a kill with no loot that came with a kill count message, every Fortis Colosseum and Doom of Mokhaiotl chest, and every raid chest (Chambers of Xeric, Theatre of Blood, Tombs of Amascut, Fractured Archive), even when its kill count message was missed. Regular NPC drops, clue caskets, reward carts etc. have `playerItems: null`.

The items are a single snapshot of the gear setup you brought, taken on your first landed hit on any NPC in the trip, so supplies used during the fight are still there. Every boss kill in the same trip (e.g. several Vorkath kills, a whole Barrows run, a raid, or a Colosseum or Inferno run) is sent with that same snapshot. A trip ends when you open the bank, log out or hop, or go 30 minutes without landing a hit. The next hit after that starts a new trip with a new snapshot. A boss kill with no trip going on (e.g. you landed no hit since banking) has `playerItems: null`.

| Field | Type | Description |
| --- | --- | --- |
| `equipment` | object | Worn items keyed by slot: `"HEAD"`, `"CAPE"`, `"AMULET"`, `"WEAPON"`, `"BODY"`, `"SHIELD"`, `"LEGS"`, `"GLOVES"`, `"BOOTS"`, `"RING"`, `"AMMO"`. Each value is `{ "id": number, "quantity": number }`. Empty slots are left out. |
| `inventory` | object | Inventory items keyed by slot index, `"0"` to `"27"`, as `{ "id": number, "quantity": number }`. Empty slots are left out. |
| `runePouch` | array \| null | The runes in the rune pouch, as `{ "id": number, "quantity": number }`, or `null` if it is empty. The game keeps this even when no pouch is carried, so check the inventory for a rune pouch. |
| `quiver` | object \| null | The ammo in Dizana's quiver, as `{ "id": number, "quantity": number }`, or `null` if empty. The game keeps this even when no quiver is worn, so check the cape. |

#### Team

Sent with the same boss kills as [Player items](#player-items), except Wintertodt, Tempoross and Zalcano, where the players around are a crowd rather than a team. Only the number of players is sent, never their names.

| Field | Type | Description |
| --- | --- | --- |
| `size` | number | Players in the fight, including you (`1` = solo). For the **Chambers of Xeric**, **Theatre of Blood**, **Tombs of Amascut** and **Fractured Archive**, the number of players in the raid's party when the raid was completed. For every other boss, you plus each other player who was within 15 tiles of you at some point between the start of the fight and the kill, including players who died or left before the end. In a public area, someone passing close by during the fight counts too. Solo-only content is always `1`. |
| `scaling` | number \| null | **Chambers of Xeric** only: the players the party added to the raid's scale with its scaling setting, read when the raid is completed. The raid scales as `size + scaling` players, e.g. `size: 2, scaling: 3` is two players in a 5-player raid. `null` for everything else. |

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
    "wave": null,
    "splits": null,
    "points": null,
    "invocation": null
  },
  "drops": [
    { "itemId": 536, "itemName": "Dragon bones", "quantity": 2, "value": 2450 },
    { "itemId": 995, "itemName": "Coins", "quantity": 15000, "value": 1 }
  ],
  "playerItems": {
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
    "runePouch": [{ "id": 560, "quantity": 3000 }, { "id": 565, "quantity": 1500 }],
    "quiver": null
  },
  "team": { "size": 1, "scaling": null }
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
    "wave": 3,
    "splits": [
      { "name": "Wave 1", "duration": 34.2 },
      { "name": "Wave 2", "duration": 69.6 },
      { "name": "Wave 3", "duration": 81.0 }
    ],
    "points": null,
    "invocation": null
  },
  "drops": [
    { "itemId": 995, "itemName": "Coins", "quantity": 40000, "value": 1 }
  ],
  "playerItems": {
    "equipment": { "WEAPON": { "id": 20997, "quantity": 1 } },
    "inventory": { "0": { "id": 12926, "quantity": 1 } },
    "runePouch": null,
    "quiver": null
  },
  "team": { "size": 1, "scaling": null }
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
    "wave": null,
    "splits": [
      { "name": "Archive Guardian", "duration": 95.4 },
      { "name": "Bork", "duration": 141.0 },
      { "name": "Moia", "duration": 163.2 },
      { "name": "Xarth", "duration": 152.7 },
      { "name": "Ocellus", "duration": 210.5 }
    ],
    "points": null,
    "invocation": null
  },
  "drops": [
    { "itemId": 995, "itemName": "Coins", "quantity": 50000, "value": 1 }
  ],
  "playerItems": {
    "equipment": { "WEAPON": { "id": 4151, "quantity": 1 } },
    "inventory": {},
    "runePouch": null,
    "quiver": null
  },
  "team": { "size": 3, "scaling": null }
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

## Sync

Snapshots of everything in your collection log and combat achievements, so what you got before installing the plugin can be backfilled. A snapshot is sent every time the player opens the matching interface in game, so expect the same one many times. It is the player's current state, not something that happened: store the latest one per `accountHash`, replacing the previous one, rather than appending it to your events.

Snapshots aren't sent to the endpoint URLs themselves but to a path next to each one: the path replaces the last part of the URL, and any query string is kept.

| Snapshot | Sent to | For endpoint URL `http://localhost:3000/events` |
| --- | --- | --- |
| [`collection_log_snapshot`](#collection_log_snapshot) | `sync/clogs` | `POST http://localhost:3000/sync/clogs` |
| [`combat_achievement_snapshot`](#combat_achievement_snapshot) | `sync/cas` | `POST http://localhost:3000/sync/cas` |

An endpoint URL of `https://example.com/osrs/events?key=abc` sends to `https://example.com/osrs/sync/cas?key=abc`. Snapshots carry the same [common fields](#common-fields) as events, with their own `eventType`. An API that doesn't handle these paths can ignore them: the plugin doesn't mind a `404`.

### `collection_log_snapshot`

Every item in the collection log. Sent every time you open the collection log in game, and only then, whether or not anything changed. The plugin briefly opens and closes the log's search to list every item (the same approach as the [Valiance clan plugin](https://github.com/Sicryption/ValianceClanPlugin)). Opening the log is enough: you don't need to click through the pages.

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

### `combat_achievement_snapshot`

Every completed combat task with your points and tier (the example below uses made-up totals and thresholds). Sent every time you open the combat achievements interface in game, and only then, whether or not anything changed. Switching between its tabs doesn't send it again. Everything is read from values the game already has, so nothing is clicked. Task completion is read the same way as the [Valiance clan plugin](https://github.com/Sicryption/ValianceClanPlugin).

| Field | Type | Description |
| --- | --- | --- |
| `completedTaskIds` | array of numbers | The game's internal ID of every completed task, ascending. |
| `completedTasks` | number | Number of completed tasks (the length of `completedTaskIds`). |
| `points` | number | Combat achievement points, from the game's own counter. |
| `tier` | string \| null | Highest tier whose points threshold you've reached: `"easy"`, `"medium"`, `"hard"`, `"elite"`, `"master"` or `"grandmaster"`. `null` before reaching easy. |
| `tiers` | array | Progress per tier, easy first, as `{ "tier": string, "completed": number, "total": number, "pointsThreshold": number \| null }`. `pointsThreshold` is the points needed for the tier, `null` if the game didn't report it. |

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

## Migrating from earlier versions

Changes to the `loot` payload that need a receiver to be updated:

- **`raid` is removed.** The Fractured Archive's boss splits are now in [`source.splits`](#splits), and each split's `boss` is now `name`. `source.splits` also holds the waves of the Fortis Colosseum, Doom of Mokhaiotl, the Fight Caves and the Inferno.
- **`playerItems` is back** (an earlier version stopped sending it), with `rune_pouch` renamed to `runePouch`.

## Requirements

Some events depend on RuneLite or game settings:

| Event | Requires |
| --- | --- |
| `loot` | RuneLite's **Loot Tracker** plugin enabled (on by default). The plugin warns in chat when it is turned off. |
| `duration` | Bosses and activities where the game posts a duration or completion time in chat, or bosses you landed a hit on (timed by the plugin). Timers added by other plugins are not used. |
