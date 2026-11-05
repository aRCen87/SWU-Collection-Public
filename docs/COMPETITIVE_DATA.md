# Native tournament and meta companion

The Android screen consumes `SWU_COMPETITIVE_FEED` v1 from the `competitive-data`
branch of this repository, falling back to its last validated atomic cache and
then its bundled snapshot. Network failures never clear saved decks or inventory.

Upstream: https://api.swuapi.com/docs, documented public `GET /export/:entity`
for `tournaments`, `decklists`, and `standings`. The filtered `/tournaments` API
requires authorization, whereas these bulk exports are documented public and
were verified without a key. No credentials are included in the app or workflow.
The app requests only the compact prepared snapshot; bulk exports run once daily.

Selection: completed Premier PQ/SQ/RQ/GC/LCQ events in the previous 60 days.
The frequency denominator counts only complete lists whose every reference can
be resolved against the French catalogue. Name-only rows and unavailable printings
are resolved solely through an exact full English title/subtitle in the verified
card details, and only if it maps to a unique card family; no fuzzy matching.
Equivalent known printings and reprints are normalized to that same reference
before grouping, to avoid splitting the presence of an identical leader/base. Unknown card rows reject the entire
list; leader/base must each occur exactly once and the main deck must contain
at least 50 cards. Reserve quantities are preserved separately. Excluded-list
count and partial coverage are shown in the app. Frequency is **not** win rate
or the share of all tournament participants. No online-game statistics are mixed
with physical tournament results.

A displayed placing comes exclusively from `final_placement` of an exact
`decklist_uuid` link or exact tournament + account match. Raw Swiss rank is never
substituted for final placing. Representative lists prioritize documented final
placing, then event date. No player/account profile data is included in the app
snapshot. At most 30 leader/base groups with three examples each and 30 recent
events with twelve lists each are surfaced, with metadata for all referenced events.

Copy exports the full portable JSON (leader, base, main and reserve). Save calls
the existing `CompetitiveDecks.saveCopy` function and creates an independent deck;
collection quantities and the database schema are unchanged. Already-saved lists
are detected by exact portable deck equality and cannot be repeatedly saved from
the same card. Full previews retain legality, missing-card and budget checks.

Refresh validates the entire feed before replacing the local cache. Broken
references, malformed quantities, unknown versions and older snapshots are rejected.
The daily workflow only replaces its public payload after successful generation.
