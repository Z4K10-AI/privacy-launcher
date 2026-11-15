# Privacy

What the launcher can reach, what it cannot, and how each claim is checked.
The short version is in the [README](../README.md#the-privacy-promise).

## The permissions, exactly

The launcher **cannot reach the network**, keeps its settings inside its own
private storage, and declares **two** Android permissions, neither of which
does anything until you switch on the feature that needs it.

- `READ_CONTACTS`, for the optional "search contacts" row in the app drawer.
  Off by default; nothing is requested or read until you turn it on yourself.
  [Contacts, in detail](#contacts-a-permission-you-switch-on).
- `WRITE_SETTINGS`, for the brightness slider in the quick controls panel. An
  app-op permission: declaring it grants nothing and no dialog can grant it —
  you allow it in Android's own "Modify system settings" screen, or the slider
  stays disabled.

**`INTERNET` appears nowhere in the merged release manifest** — checked
directly, not inferred from the source manifest. The merged manifest holds
three `uses-permission` entries rather than the two the README once claimed:

| Permission | Where from |
|---|---|
| `READ_CONTACTS` | ours, inert until contact search is switched on |
| `WRITE_SETTINGS` | ours, inert until the brightness slider is used |
| `com.privacylauncher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | generated |

The third is not a capability the app gains. AndroidX core generates it when
a receiver is registered at runtime, declares it `android:protectionLevel="signature"`,
and has the app hold its own permission — so only something signed with the
same key could satisfy it, which means nothing else on the phone. Its whole
job is to stop other apps delivering broadcasts to ours. It reaches nothing
of yours, but it is in the manifest, and a privacy claim that quietly
rounds three down to two is not one worth making.

Check it yourself:

```bash
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

---

## Privacy properties

| Property | How it is enforced |
|---|---|
| Two permissions, declared | `AndroidManifest.xml` declares `READ_CONTACTS` and `WRITE_SETTINGS` and nothing else. Declaring either grants nothing; see below |
| Nothing asked for unprompted | The only `requestPermission` call is reached from the contact-search row in the drawer and the switch in Settings. `WRITE_SETTINGS` cannot be requested at all — only allowed by the user in Android's own screen, reached from the disabled brightness slider |
| Cannot reach the network | `INTERNET` and `ACCESS_NETWORK_STATE` are removed from the merged manifest with `tools:node="remove"`, so no dependency can reintroduce them |
| No advertising ID | `com.google.android.gms.permission.AD_ID` removed the same way |
| No Google Play Services | Not a dependency. See the note below |
| No analytics, ads or crash reporting | No such dependency exists in the build |
| Settings stay on the phone | `android:allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain from cloud backup and device transfer |
| Narrow app visibility | The manifest's `<queries>` block asks only for apps with a launcher icon, not `QUERY_ALL_PACKAGES`. The privacy dashboard works within that limit and states it on screen |
| Two optional abilities, each off until you turn it on in Android's settings | An accessibility service that can open the notification shade and lock the screen and is refused everything else, and a device admin right that can lock the screen and nothing else (`res/xml/lock_down_admin.xml`). Neither is a permission the app requests, and `PermissionPromiseTest` reads both lists back from the installed app |
| No tracker code inside it | The launcher's own tracker scan is run against its own installed file in `TrackerScanDeviceTest`, and must find none of the 428 trackers on the list |

### Contacts: a permission you switch on

Drawer search can include the phone's contacts. That needs `READ_CONTACTS`,
so it is worth being exact about what the feature does and does not do.

- **Off by default.** The launcher is fully usable without it, and in that
  state no contact query is ever made.
- **Nothing asks for it unprompted.** Not at startup, not on first run, not
  when the drawer opens. The permission request happens when you tap "Search
  contacts too" in the drawer or the switch in Settings, and at no other time.
- **Matching happens in the provider, not here.** The query goes through
  `ContactsContract.Contacts.CONTENT_FILTER_URI`, so this app never reads the
  contact list — only the few rows that match what was typed.
- **Two columns.** A display name and a lookup key. No numbers, no email
  addresses, no postal addresses, no photos, no groups, no call history.
- **Nothing is kept.** Results are not cached, counted, indexed or written
  anywhere. They exist for as long as the search text does.
- **Tapping a result hands off.** The lookup URI goes to whichever contacts
  app is installed; this launcher does not display contact details itself.
- **Reversible.** The switch in Settings stops every query. Only Android can
  take the grant itself back, so Settings links to App info where you can.

There is still no network permission of any kind, so nothing read here can
leave the phone.

The alternative design — no permission, and a result that opens the contacts
app's own search — was considered and rejected: it cannot show a matching name
in the drawer, which is the whole point of the feature. The compromise chosen
instead is to declare the permission and make the asking entirely
user-initiated.

### A note on "no Google"

Jetpack Compose, AndroidX and Kotlin are developed by Google and JetBrains, but
they are open source under Apache 2.0 and ship as ordinary libraries compiled
into the APK. They are **not** Google Play Services: they contain no Google
account integration, no network clients, and no telemetry. Play Services is a
separate, closed-source system app, and this project does not depend on it.
The launcher therefore runs on a de-Googled ROM.
