# Bote

**Language:** [Español](README.md) · English

Android app for managing the accounts of groups of people: create one record per
event, note who pays for each thing and how the cost is split, and at the end it
generates a report with the minimum transfers needed for everyone to be square.

## Features

- **Event records** with photo-avatar, date, location, title and description
  (the last three optional).
- **Two modes per record**: *restricted* (only the admin-creator edits and adds
  entries) or *collaborative* (everyone can).
- **Attendees** added from the phone's contacts or by hand (name, optional phone
  and email), with a deterministic colored initials avatar. They show up on the
  record as they are added.
- **Late joiner**: if there are already expenses recorded when you add someone,
  the app asks whether they take on the costs from the beginning (in solidarity)
  or start with a clean profile from that moment on.
- **Entries** (one good or service paid for by a single person) with a choosable
  icon category (restaurant, drinks, gift, supermarket, lodging, fuel,
  substances, cash, other), and budgeted (optional), spent (required) and paid
  (required to close the entry) amounts.
- **Fader-based split**: a column that toggles between `=` (equal shares) and
  `%` (percentages); each attendee has a sliding fader and a pin to lock their
  percentage, so moving a fader redistributes proportionally only among the open
  faders.
- **Lock and final account**: closing the account generates the definitive report
  of who pays how much and to whom; each user marks when they make their payment.
  When you pay your share, the event disappears from your general list; only the
  creator keeps seeing it until everyone settles up and it reaches zero.
- **Calendar button** to add the event to the Android calendar (no permissions,
  via the standard system screen).
- **Serverless sync**: the event travels as a JSON file or as a **QR code**
  (scanned from the other phone). On import it is **merged entry by entry** by
  UUID: the most recently modified version wins, payment marks are never lost, and
  deleted entries don't come back to life (deletion tombstones).
- **Receipt photo** on each entry, so you can verify the report's amounts (stored
  locally; it does not travel in the sync).
- **Category breakdown** in the report, with proportional bars.
- **Extortion mode** (optional, off by default): a "your money or your life"
  button in the report that drafts the collection message for each debtor from an
  editable template with {name}, {amount} and {event}.
- **Activity log** per event: who joins, what gets recorded, which payments get
  marked, account closings and syncs. It travels with the event on export and
  merges without duplicates across devices.
- **Settings**: white/black/system theme, choosable primary and accent colors
  (black and red by default) and notification options (day of the event, account
  closing and pending-payment reminder).

## User guide

Bote keeps a group's account when they get together for something (a weekend, a
dinner, a holiday flat): who puts in money, what they spend it on, how it splits
and, at the end, **who pays how much to whom** so everyone comes out square with
the fewest possible transfers. No account or internet required: each phone keeps
its own copy and they pass the event between them.

### The main screen ("My events")

A list of your events. Each card shows the photo, the title, the number of
attendees and the total spent, and whether it is *Pending settlement* or
*Settled*.

- **Filters**: *Active*, *Settled* or *All*.
- **Sort** (menu): by date, recent creation, number of attendees or most
  expensive.
- **`+`**: create a new event.
- **Import event**: receive one someone sends you by file or QR.

When you pay your share of an event, it disappears from your list; the creator
keeps seeing it until **everyone** has settled and it reaches zero.

### Step 1 — Create the event

| Field | What it's for |
|-------|---------------|
| **Photo** (optional) | An avatar to recognize the event at a glance. |
| **Title / Description** (optional) | "Weekend in the mountains", plus a note if needed. |
| **Date** | The day of the event (required). |
| **Location** (optional) | Address or place; can later be opened on the map. |
| **Mode** | *Restricted*: only you (the creator-admin) can modify and add entries. *Collaborative*: anyone in the group can. |
| **Sync** | How the event will travel between phones: *Local*, *Default* or *Other* (see the box below). |

**Attendees**: add them *From contacts* or *By hand* (name and, optionally, phone
and email). Each gets an avatar with their initials and a fixed color. You appear
as *Me*.

> 🤓 **Nerd note — adding someone who joins the party late**
>
> If you add a person when the event **already has expenses recorded**, Bote asks:
> do they take on the costs *from the beginning* or *only from now*? "From the
> beginning" (in solidarity) puts them in the split for everything so far, as if
> they'd been there from minute one. "Only from now" sets their tab to zero and
> they only enter what's recorded from that point on — handy for the one who joins
> on day two. You don't need to understand the formula: you just decide whether
> they chip in for the earlier stuff or not.

### Step 2 — Record the expenses ("entries")

An **entry** is one thing paid for by a **single person** (the meal, the lodging,
the fuel…) and later split among whoever is due to share it.

| Field | What it's for |
|-------|---------------|
| **Concept** | What it is: "Saturday dinner", "Fuel". |
| **Who pays** | The attendee who put the money down out of pocket. |
| **Category** | Restaurant, drinks, gift, supermarket, lodging, fuel, substances, cash or other. Drives the icon and the report breakdown. |
| **Budgeted** (optional) | What you expected to spend; just a guide. |
| **Spent** (required) | What it actually cost. |
| **Paid** | How much of that entry has already been settled. It must be complete to "close" the entry and, ultimately, close the account. |
| **Receipt** (photo, optional) | A photo of the receipt to check amounts. Stored only on your phone; it **does not travel** in the sync. |

**Split**: at the bottom you choose how that entry is divided.

- **Equal shares (`=`)**: mark who takes part and it splits evenly among them.
- **Percentages (`%`)**: each participant has a **fader** (slider). Move it to
  give them more or less weight. The **pin 📌** locks that person's percentage so
  it won't change: when you move another fader, Bote splits the rest only among
  the ones left open. That way you can say "Marta pays a fixed 40% and the rest
  split evenly" without doing the maths.

### Step 3 — The report and closing the account

- **Report**: at any time you can see the *provisional report* with the balance
  (who has paid, how much is due, whether they get money back or owe it), the
  breakdown **by category** with bars, and the **who pays whom** section with the
  minimum transfers to settle everything.
- **Close the account** (lock): when there will be no more expenses, tap *Close
  the account*. The **definitive report** is generated and no more entries can be
  added (it requires every entry to have its amount paid). You can *Reopen* it if
  needed.
- **Has settled**: each person marks when they make their payment. When you settle
  your share, the event leaves your list.
- **Calendar**: a button to add the event date to the Android calendar.

> 🤓 **Nerd note — "extortion mode" (optional, off by default)**
>
> In Settings you can turn on *"your money or your life"* mode. When it's on, a
> button appears in the report that **drafts the collection message on its own**
> for each person who owes you money, from a template you edit. You can use the
> placeholders `{name}`, `{amount}`, `{event}` and `{phone}` (yours, so they can
> pay you) and Bote fills them with the real data. It doesn't charge anything or
> send anything by itself: it just leaves the text ready for you to send over
> WhatsApp. It's a joke with a purpose: it saves typing "hey, you owe me €12.50"
> fifteen times.

### Sharing and syncing the event

For the group to have the same account, the event has to be passed between phones.
Bote merges it **entry by entry**, so it doesn't matter who records what on their
phone: when you bring them together, nothing is lost or duplicated.

- **By QR or file** (no server): *Send by QR or file*. The other person receives
  it by scanning the QR in person or opening the file (WhatsApp, email…). On
  import, Bote asks *who they are* in that event.
- **Automatic via the cloud** (optional): see the box and the following section.

> 🤓 **Nerd note — how two phones merge without fighting**
>
> Each entry has a unique identifier and a timestamp. When you import an event,
> Bote doesn't overwrite it: it compares entry by entry and **keeps the most
> recently modified version**. "I've paid" marks are never lost, and if someone
> deleted an entry, it doesn't "come back to life" on sync (a *tombstone* is kept
> remembering it was deleted). Each phone's activity log also merges without
> repeating lines. In practice: everyone records their stuff whenever they like,
> and when you sync, the account ends up complete and consistent with no one
> having to redo anything. Receipt photos are the only thing that doesn't travel
> (they stay on each phone).

> 🤓 **Nerd note — automatic cloud sync (optional)**
>
> Besides the QR and the file, Bote can sync on its own over the internet, but
> **you provide the server**: there's no central Bote account and no one who sees
> your data. Someone in the group creates a free project on
> [Supabase](https://supabase.com) (or uses any PostgREST of their own), pastes in
> a table, and copies two things (a URL and a key). From then on the event uploads
> and downloads on its own, but **the merge is still local** (the same logic as
> the QR); the server is just a mailbox to drop off and pick up the copy.
>
> The convenient part: **only the event's creator configures it**, and that
> configuration **travels inside the event** when shared. Everyone else, on
> importing by QR or file, ends up connected **without typing anything**. Each
> group can have its own server, so you can be in several groups at once without
> confusion. Honest warning: whoever has the key can read that table, so only
> share it with your group. The exact technical steps are further down, in
> *Automatic cloud sync*.

> 🤓 **Nerd note — payment detection from notifications (optional)**
>
> In Settings → *Payment detector* you can give Bote access to read the phone's
> notifications. What for? When a notification arrives from your bank that looks
> like a purchase (a charge with an amount and a merchant), Bote offers to
> **record it as an expense with the amount and merchant already filled in**: you
> just choose which event it goes to and confirm. It's optional and **off by
> default**. Important for your peace of mind: **everything is processed inside
> your phone**, nothing is sent to any server; it simply saves you typing in the
> amount of what you just paid by card. It requires granting Android's notification
> access, which you can revoke whenever you want.

### Settings (summary)

- **Theme** (white / black / system) and primary and accent **colors** (black and
  red by default).
- **Notifications**: reminder on the day of the event, alert when an account is
  closed, and pending-payment reminder.
- **Extortion mode** and its message template.
- **Payment detector** (notification access).
- **Default sync server** (URL and key for your usual group).

## Automatic cloud sync (optional)

Besides the QR and the file, Bote can sync on its own against a
[Supabase](https://supabase.com) table (its free tier is more than enough for
personal use) or any PostgREST of your own. The merge is still local (same logic
as the QR); the server is only a mailbox.

1. Create a free project at supabase.com.
2. In *SQL Editor*, run:

```sql
create table if not exists eventos_sync (
  uuid text primary key,
  datos jsonb not null,
  actualizado timestamptz not null default now()
);
alter table eventos_sync enable row level security;
create policy "acceso anon" on eventos_sync
  for all using (true) with check (true);
```

3. Copy the **Project URL** and the **`anon` key** (Settings → API).

**The server is per event**, not per app: each group can have its own, so the same
user can be in several groups on different servers. When you create an event you
choose in the *Sync* section:

- **Local** — no cloud (QR/file only).
- **Default** — uses the server you have saved in Settings (for your usual group).
- **Other** — you paste the URL and key of that specific group.

**Only the event's creator sets this up.** The server config **travels inside the
event** when shared (QR or file), so the other attendees, on importing it, are
connected automatically without typing anything. From then on, opening the event
downloads the remote copy, merges it and uploads the result.

Honest warning: anyone with the anon key can read the whole table, so only share
it with your group (the event's uuid acts as the secret, but the key is the key to
the mailbox). Photos are not uploaded.

## Building

Standard Android project (Kotlin, Material Components, Room). Requires JDK 17 and
the Android SDK (compileSdk 34):

```
gradle assembleDebug
```

On GitHub Actions there are two flows: `build.yml` builds the debug APK on every
push to `main`, and `release.yml` builds the signed APK when tagging `v*`,
publishes it to the personal F-Droid repository
(https://tizzenn.github.io/fdroid/repo) and creates the GitHub release.

Secrets needed to publish: `BOTE_KEYSTORE_B64`, `BOTE_KEYSTORE_PASS`,
`FDROID_DEPLOY_KEY`, `FDROID_KEYSTORE_B64` and `FDROID_KEYSTORE_PASS`.

## License

GNU GPL v3: any redistribution or derivative must publish its source code under
the same license. See [LICENSE](LICENSE).
