# Contributing

Thank you for wanting to help. Privacy Launcher is a small project, so
every report and every change is read by a person.

## What helps most right now

- **Trying it on a real phone.** Everything so far has run on emulators. An
  issue that says which phone, which Android version, what worked and what
  did not is worth more than almost any code. Screenshots help, but check
  them for anything personal first.
- **Bug reports** that say how to make the bug happen, on which phone and
  which Android version, and what you expected instead.
- **Listening with TalkBack.** The accessibility tests check what a screen
  reader is told; nobody has yet listened to the result on a phone.

For anything larger than a small fix, please open an issue first, so we can
agree on the approach before you spend time on it.

## The rules every change keeps

These are why the project exists, so a change that breaks one will not be
merged, however useful it is otherwise.

1. **No internet.** The app never gets the internet permission, and has no
   code that tries to reach a network. `PermissionPromiseTest` checks the
   installed app.
2. **No tracking and nothing proprietary.** No analytics, crash reporting,
   ads, Google Play Services or closed-source libraries. Every library is
   open source and listed with its licence in the README;
   `tools/licences.py` checks every library that ends up in the app.
3. **Nothing leaves the phone.** Data stays in the app's private storage.
   Backups are files the person chooses.
4. **A fix comes with a test that fails without it.** Write the test, watch
   it fail, then fix the code. The CHANGELOG has many examples.
5. **Honest wording.** When Android does not let the launcher do something,
   the screen says so, rather than showing a control that does nothing.
6. **Keep to the design system.** Spacing, type, colours, motion and haptics
   come from `designsystem/`; see [docs/design.md](docs/design.md). Nothing
   should look cheap.
7. **Measure, don't guess.** A claim about speed or memory names the device
   or emulator it was measured on.

## Building and testing

You need a JDK 17 and the Android SDK with platform 37. Android Studio is
optional.

```bash
./gradlew testDebugUnitTest lintDebug   # the tests that need no device
./gradlew connectedDebugAndroidTest     # the device tests, on any emulator
```

The scripts in `tools/` run the whole suite on emulators and print a
summary; `tools/check.sh` is the one to start with. They were written on a
Mac and use bash and Python 3. `tools/env.sh` uses your `JAVA_HOME` and
`ANDROID_HOME`. [docs/testing.md](docs/testing.md) explains every kind of
test, and [docs/building.md](docs/building.md) the scripts.

The scripts will only drive an emulator, never a phone that happens to be
plugged in.

## Proposing a change

1. Fork the repository and make a branch for your change.
2. Keep each commit to one step, with a message that says what changed and
   why, in plain words.
3. Add a line to `CHANGELOG.md` under "Unreleased".
4. Run the tests above, and say in the pull request which ones you ran and
   on what.
5. Open the pull request. GitHub runs the build, lint and the tests that
   need no device on every pull request.

## Licence of contributions

The project is under the [Apache License 2.0](LICENSE). As section 5 of that
licence says, anything you submit for inclusion is offered under the same
licence, unless you clearly say otherwise. There is no separate agreement to
sign.

## Conduct

Everyone taking part is expected to follow the
[code of conduct](CODE_OF_CONDUCT.md). Security problems go through
[SECURITY.md](SECURITY.md), not public issues.
