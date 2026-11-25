# Postie Pete

Sends your drops, kills and account progress to an API of your choice, so you can keep a loot log, chart your kill times, feed a clan dashboard and so on.

Postie Pete only collects and sends. It doesn't store, display or analyse anything itself: you need an API to send to, either your own or one run by someone you trust, such as your clan.

## What it sends

- **Loot**: every NPC drop, raid or reward chest, clue casket and so on, with each item's GE value. Boss kills also come with:
  - your kill count, kill time and personal best
  - wave, room and boss times for the Fortis Colosseum, Doom of Mokhaiotl, Fight Caves, Inferno, Chambers of Xeric, Theatre of Blood, Tombs of Amascut and Fractured Archive
  - raid points (Chambers of Xeric and Tombs of Amascut) and invocation level (Tombs of Amascut)
  - the gear, inventory, rune pouch and quiver you brought on the trip
  - how many players were in the fight (the number only, never their names)
- **Collection log**: every new item added to your collection log.
- **Combat achievements**: every combat task you complete.
- **Your whole collection log and combat achievements**: each time you open them in game, so what you got before installing the plugin is included too. A collection log opened from an adventure log in a player-owned house isn't sent, since it may be someone else's.

Kills that give no loot at the kill, like the Inferno, Fight Caves, Wintertodt and Tempoross, are still sent with their kill count and time.

Everything sent includes your display name and your account hash, an ID that stays the same when you change name.

## Setup

Set **Endpoint URLs** to the address of the API you want to send to, for example `https://example.com/osrs/events`. To send to more than one API, separate the addresses with commas.

Nothing is sent while **Endpoint URLs** is empty.

## Requirements

- Loot is only sent while RuneLite's **Loot Tracker** plugin is enabled (it is on by default). Postie Pete warns you in chat when it is turned off.
- Kill times are sent for bosses and activities where the game shows a time in chat. For bosses that have none, like the Corporeal Beast, the God Wars Dungeon generals and Barrows, Postie Pete times the kill itself from your first hit. Timers from other plugins aren't used.

## For developers

Building an API to receive these events? See [DEVELOPMENT.md](https://github.com/osynkad/postie-pete/blob/master/DEVELOPMENT.md) for the request format, every event's fields and the snapshot sync.
