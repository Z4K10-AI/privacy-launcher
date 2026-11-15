# Security

Privacy Launcher's whole point is what it does not let out, so a security
problem here matters. Thank you for taking the time to report one.

## How to report a vulnerability

**Please do not open a public issue.** Report it privately through GitHub:

1. Go to this repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the problem.

Only the maintainers can see the report. Please include:

- the launcher's version (Settings, About) and where you got it;
- the phone or emulator, and its Android version;
- what an attacker could do, and what they would need first (another app
  installed? the phone unlocked in their hands?);
- the steps to make it happen, or a proof of concept.

This is a small project, so please allow a few days for a reply. You will
hear back when the report has been read, and again when there is a fix.
Once a fix is released, a security advisory is published that credits you,
unless you would rather not be named.

## What counts

- Any way for data from the launcher to leave the phone.
- Any way round the hidden-apps lock, or round Lock down.
- Another app on the phone getting something from the launcher it should
  not: its settings, its list of hidden apps, its tracker scan results, or
  control of anything it can do.
- An app crafted so that the tracker scan crashes or hangs the launcher, or
  worse.
- A library inside the app that is vulnerable in a way that reaches the
  launcher.

## What does not

- Problems that need the phone to be rooted or Android itself to be
  compromised first.
- Limits of Android that the app already states. For example, hiding an app
  keeps it out of the launcher, not off the phone, and the launcher cannot
  read Android's record of recent permission use. These are explained in
  [docs/privacy.md](docs/privacy.md) and [docs/features.md](docs/features.md).

## Supported versions

Fixes go into the latest release only.
