# What it does

## What it does today

**Gestures**
- Swipe up for the app drawer, pinch in for settings.
- Swipe down for notifications and double-tap to lock, both via an
  optional accessibility service. Android gives an ordinary app no other
  way to do either. The service subscribes to no events and is refused the
  ability to read window content by the system; it performs two global
  actions and nothing else. The launcher works fully without it.

**Home screen**
- Clock (12 or 24 hour, following the phone's own setting) and date, over
  your wallpaper.
- A grid of apps and folders you choose. Long-press carries straight into a
  drag: rearrange by dropping near an edge, or make a folder by dropping onto
  the centre of another icon. Folders open as a rounded sheet over a blurred
  home screen.
- A dock of up to five apps. Carry a grid icon down onto it to keep the app
  there, or long-press a dock icon to slide it along the dock or carry it up
  onto the grid. While you are editing, each dock icon has the same ✕ as the
  grid's. An icon moves: it is never left in both places by a drag.
- An optional "at a glance" line under the clock: next alarm and battery,
  both read from the phone with no network and no permission. No weather,
  deliberately — it would need both.
- Tapping the clock opens your clock app.
- Swipe up, or tap **Apps**, for the drawer. The sheet follows your finger
  and springs to open or closed when you let go.

**App drawer**
- Every installed, non-hidden app in a grid, alphabetical with a heading per
  letter and a fast-scroll letter bar once there are enough apps to need one.
- Fuzzy search, reordered on every keystroke. Results are ranked in tiers:
  the whole name, a prefix (`tele` finds Telegram), a word start (`maps`
  finds Google Maps), initials (`gm` finds Google Maps, `wv` finds WebView
  Browser Tester), anywhere in the name, then the letters in order with gaps.
  Within a tier the shorter name wins.
- The keyboard opens with the drawer, with a switch in Settings to stop it.
- Search finds apps. Two more things are found only after you switch them
  on in Settings: Android's own **settings screens** (Wi‑Fi, Display,
  Battery and so on, a fixed list of shortcuts that reads nothing from the
  phone) and **contacts**. See
  [Contacts, in detail](privacy.md#contacts-a-permission-you-switch-on).
- An optional **most used** row at the top, counted on the phone, stored only
  there, and erasable from Settings.
- Drag the handle at the top to pull the drawer back down.
- Hold an app and let go for its menu: add to / remove from home screen, add
  to dock, hide from drawer, **App info** (Android's own page, where you
  revoke permissions), uninstall.
- Hold an app and drag to carry it out of the drawer. The drawer closes and
  the icon stays under your finger: drop it on the grid, onto another icon
  to make a folder, or in the dock.

**Quick controls** (swipe right-to-left on the home screen)
- A side sheet of eight tiles over a blurred home screen, following the finger
  and springing to open or closed.
- **The flashlight is the only real switch**, because
  `CameraManager.setTorchMode` is the one of these Android still lets an
  ordinary app call. Wi‑Fi, Bluetooth, Location, Do Not Disturb, Auto-rotate
  and Private DNS show their state, carry a corner arrow, and open Android's
  own screen — Wi‑Fi opens an inline Settings *panel* with a working switch on
  it. A line under the grid says all of this plainly.
- **Lock down** shuts the phone so that only your PIN, pattern or password
  opens it. Fingerprint and face unlock stay off until you have typed it
  once: the point of a lock-down is that nobody can open the phone by
  pressing your finger to it.
  - It needs your OK once, on one of Android's own screens, to let the
    launcher lock the screen. Android calls that a "device admin app". The
    launcher asks for that one ability and no other; it cannot erase the
    phone, change your PIN or see anything, and Android enforces that.
  - While it is allowed, **Android will not uninstall the launcher**. That
    is Android's rule for every such app. A dialog says so before you
    agree, and Settings → Gestures → Lock down takes it back.
  - It is not all of Android's own Lockdown, the one in the power menu,
    which also hides notifications on the lock screen. An app cannot do
    that part, and the panel says so.
  - Double-tap to lock, which goes through the optional accessibility
    service, is the ordinary kind of lock: a fingerprint opens it again.
- A brightness slider with a haptic tick every twentieth of the bar. Disabled
  until you allow "Modify system settings"; the panel explains and links.
- Every state is read without a permission: the Settings provider for Wi‑Fi,
  Bluetooth, Do Not Disturb, rotation and Private DNS, and
  `LocationManager.isLocationEnabled` for location.

**Privacy dashboard** (Settings → Privacy)
- A privacy score in an animated ring, with the arithmetic shown: which
  permission group, how many apps, how much each took off.
- A card per group — Location, Microphone, SMS, Camera, Contacts, Phone,
  Files — expanding to the apps that hold it, with a tap through to Android's
  App info.
- The launcher lists itself. A privacy dashboard that leaves out the app
  showing it has answered the wrong question.
- **Trackers inside apps.** A scan looks inside each app's code for the
  advertising, analytics and profiling libraries that Exodus Privacy have
  catalogued: 428 of them in the list that ships. It runs on the phone,
  from a list inside the launcher, when you press the button and not
  before. Apps are read one at a time and each answer is remembered until
  the app is updated, so a second scan only reads what is new. Tap an app
  to see which trackers and to reach its App info page.
  - The card says what a result means. "Found" is the tracker's code being
    inside the app, not proof it runs or what it sends. "Not found" is
    nothing on the list being recognised: code can be disguised, and the
    list is a snapshot with its date shown.
  - An app that could not be read is counted as unread, never as clean.
    That is usual for apps that came with a phone, whose code is often
    stored where an ordinary app may not look.
- Everything is computed on the phone, which the screen says in so many words.
- What it deliberately does **not** do:
  - It does not ask for `QUERY_ALL_PACKAGES`, so it lists the apps it can see
    — the ones you can open — and says so rather than implying otherwise.
  - It cannot show the recent-use timeline inline. Android 12 and later keep
    one, but reading it needs `GET_APP_OPS_STATS`, which is
    `signature|privileged`. The screen says this and links to Android's own
    privacy settings. (A privileged build inside a ROM could hold it.)
  - It cannot open the permission list directly: `MANAGE_APP_PERMISSIONS`
    requires `GRANT_RUNTIME_PERMISSIONS`. App info is as close as an
    installable app gets.

**Focus mode** (Settings → Focus and quiet → Focus mode)
- Pick a handful of apps; everything else is set aside — gone from the home
  screen, the dock and the drawer, and out of search results too — until
  focus ends.
- Start it by hand for 30 minutes, an hour, two, four, or until you stop it,
  or set a daily window. The window may run overnight: 22:00 to 07:00 is the
  default.
- While it runs the interface drains of colour: the accent goes a warm grey,
  app icons are desaturated to a trace of their hue, and a veil settles over
  the wallpaper.
- The drawer carries a banner saying how many apps are set aside, with **End
  focus** on it. Ending a session the schedule started snoozes the schedule
  until that window's own end, so "end" means end rather than "end for one
  second".
- **Nothing runs in the background.** No alarm, no service, no
  `SCHEDULE_EXACT_ALARM`. Focus is a question about the clock, answered when
  something asks. The one timer involved is a coroutine `delay`, which cannot
  wake a sleeping phone; it sleeps until the next moment the answer could
  change, and never longer than an hour — a backstop in case the clock itself
  jumps for a time zone or a daylight-saving change. The state is recomputed
  whenever the launcher returns to the foreground, so a phone that dozed
  through the boundary is right again before you see it.
- The screen states the limits: focus **hides, it does not block**. A
  set-aside app is still installed, still running, and can still be opened
  from a notification, a link, or another launcher. The launcher's own
  screens are never hidden, so focus can always be switched off.
- Starting focus with nothing allowed is refused rather than obeyed: the
  button stays disabled and says why.

**Minimal mode** (Settings → Focus and quiet → Minimal mode)
- A text-only home screen: app names in large type, no icons, no grid, no
  dock. The clock shrinks to make room for the list.
- Folders are flattened, because "just app names" cannot draw a pile of
  icons, and dock apps join the end of the list so nothing becomes
  unreachable.
- Every gesture still works — swipe up for the drawer, right-to-left for
  quick controls, double-tap to lock — because both home screens share one
  gesture modifier.
- There is no reordering here; switch it off to rearrange. The drawer keeps
  its grid: it is search-first, and an icon is the fastest way to recognise
  an app you are hunting for.
- It composes with focus mode: minimal changes how the home screen looks,
  focus changes what is on it.

**Customisation**, all stored on the device
- Theme: light, dark, or follow system.
- Accent colour: six choices, each with a separate tone for light and dark so
  contrast holds either way.
- Font: system default, sans serif, serif or monospace — all families Android
  already ships, so nothing is bundled.
- Icon size: small, medium or large.
- Grid columns: 3–6, set separately for the drawer and the home screen.
- Wallpaper: opens Android's own picker.
- Hidden apps: a switch per app, with a clear note that hiding is cosmetic —
  the app stays installed, keeps running, and keeps its permissions. The list
  itself can be put behind the phone's own lock: fingerprint or face where
  they are enrolled, PIN, pattern or password otherwise. That uses the
  platform's `createConfirmDeviceCredentialIntent`, not the `androidx.biometric`
  library, which would be a sizeable dependency for one dialog.
  - The lock also stands in front of the ways round it: turning the lock
    off, and, while anything is hidden, saving a backup (the file lists the
    hidden apps) or restoring one (it replaces the list). Each prompt says
    what it is for.
  - It is not a vault. A hidden app is still listed in Android's own
    Settings, and in this launcher's privacy dashboard and focus picker,
    because those are lists of what is installed. The lock keeps one thing
    private: which apps you chose to hide. On a phone with no screen lock
    there is nothing to ask for, and the Settings screen says so.
- Search: auto keyboard, the most used row, clearing usage history, and the
  two opt-ins: Android settings and contacts.
- Focus and quiet: focus mode and its allowed apps, and minimal mode.
- Set as default home app: a shortcut into Android's home-app picker.

Settings are grouped into six sections — Appearance, Home, Drawer, Gestures,
Privacy and About — by *where you will see the change* rather than by which
part of the code owns it. Icon size sits under Home with the grid it resizes,
even though it also changes the drawer, because the home screen is where
anyone notices.

Changing icon size, the column count or the font shows a **live preview**: a
card with a few of your own apps at the chosen size and column count, and a
clock-sized sample of the chosen font. It uses real icons from the cache the
drawer has already filled, so showing it costs no extra decoding.

### First run

Four short pages: welcome, set as default launcher, choose theme and accent,
and what the app does not do. Smooth paging, and a Skip button on every page
but the last.

Nothing in it is a gate. No page asks for a permission, the home-app page can
be walked straight past, and the theme controls on page three are the real
ones — they apply as you tap, so skipping from there keeps what you chose.
This matters more than it would in another app: a launcher *is* the home
screen, so an onboarding flow you cannot get out of leaves you with no phone.

### Backup and restore

**Settings → About → Back up settings** writes a readable JSON file wherever
you point it, through Android's own file picker. No cloud, no account, and no
storage permission — the picker hands back one file, which is the only thing
the app can touch. Restore reads one back, after a dialog that says plainly
what will be replaced and that there is no undo.

Three things are deliberately left out of the file, and the screens say so:

- **Launch counts.** They are a record of what you use and when you started,
  which is the most revealing thing this app stores. Settings belong in a
  backup; a usage history does not, and writing it into a file you might mail
  to yourself would undo the care taken to keep it in private storage.
- **A running focus session.** Its end time is an instant, not a preference.
  Restoring last week's would switch focus on for no reason, or off while you
  were using it. The *schedule* is a preference and is kept.
- **Whether onboarding is done**, since restoring happens from inside the app.

Apps named in a restored layout that are not installed on this phone are
dropped when the app list next loads, rather than leaving dead icons behind.
