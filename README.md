# prn-tracker

An Android tracker for *pro re nata* medication — the kind you take when you need it
rather than on a schedule. The whole app is one screen: a log of every dose you have
taken, and a plus button.

* **Plus** — a blank form. Fill it in and the medication is created and the first dose
  logged in the same gesture.
* **Tap an entry** — the same form, already filled in from that medication. Confirm and
  another dose is logged.
* **Long press an entry** — edit it: the medication's settings, the time the dose was
  taken, or delete the entry. Long press a medication card instead and the delete
  removes the medication and its whole log.

## The model

Everything lives in two Room tables: a medication and the intakes that reference it.

| Field | Optional | What it does |
| --- | --- | --- |
| Name | no | Row title and notification title |
| Every N hours | yes | Reminder N hours after the *last logged dose*, never on a fixed clock |
| Allowed hours | yes | Reminders are held back until the window opens |
| Dose, mg | no | Shown on every row; also the unit the tolerance multiplier counts in |
| Doses in stock | yes | Warns at three left, again at zero; each logged dose spends one |
| Tolerance, days | yes | Time constant of the tolerance multiplier |
| Colour | no | The bar down the left of the log |

Leave an optional number blank and the feature it drives is simply off.

## Tolerance

Every medication with a tolerance window carries a multiplier, always visible: on each
log row as it stood at that moment, on the medication card as it stands now, and in the
editor as it will stand *after* the dose you are about to log.

A dose contributes `1.0` the moment it is taken and decays exponentially with a time
constant of the configured window:

```
load(t) = Σ (doseᵢ / referenceDose) · e^(−(t − tᵢ) / window)
```

The exponential is the only smooth curve where a single fresh dose reads exactly `×1`
*and* a steady habit of N doses per window averages exactly `×N`. A linear ramp — the
obvious first guess — quietly halves the number, so a once-a-day user of a fortnight
tolerance drug would see `×7` where the honest answer is `×14`. It also gives a closed
form for the break that would undo it: `window · ln(load)` days, which is what the
warning in the editor promises.

Tolerance never becomes a notification. It is a state, not an event, and pushing it
would mean nagging somebody who has already stopped — the number drains back to
baseline on its own the moment you leave the drug alone. It is structurally impossible
for tolerance to reach the shade: it is not one of the alert kinds the notifier accepts.

## Reminders

One alarm drives the whole app. When it fires, every medication is recomputed, the
notification shade is brought in line with the result, and the next alarm is armed.

* Reminders are anchored on the **last logged dose**. Nothing was taken, so nothing
  fires — that is the whole PRN semantic in one sentence.
* An overdue dose re-notifies after **15, 35, 65 and 110 minutes, then hourly**, for as
  long as it stays overdue. The floor is not a taste call: `setExactAndAllowWhileIdle`
  refuses to fire more than once per nine minutes per app, and Android quietens anything
  that repeats inside two minutes, so a shorter loop would be *less* noticeable.
* A due dose is treated as an alarm — alarm-stream sound, so it survives a silenced
  ringer, a heads-up that appears over a locked screen, a running count of how late it
  is, and a **Taken** action that logs the dose without opening the app.
* Eligibility and delivery are separate clocks. A dose that becomes allowed at 03:00
  reads as ready the second you open the app, and still waits for the window to open
  before it makes any noise.
* Alarms are re-armed after a reboot, a package update, a clock or time-zone change, and
  whenever the exact-alarm permission is granted.

The app asks for `SCHEDULE_EXACT_ALARM` rather than the automatically granted
`USE_EXACT_ALARM`, which Play policy reserves for alarm clocks, timers and calendars.
Refuse it and reminders degrade to inexact alarms instead of breaking; the screen says
so. Notification content is hidden on the lock screen — drug names are nobody else's
business.

## Building

Requires [just](https://github.com/casey/just), a JDK 17+ and the Android SDK
(platform 37, build-tools 37.0.0).

| Command | Result |
| --- | --- |
| `just build` | signed release APK in `android/app/build/outputs/apk/release` |
| `just debug` | debug APK |
| `just test` | unit tests for the scheduling and tolerance maths |
| `just lint` | ktlint and detekt with the shared [qa-kotlin](https://github.com/wprhvso/qa-kotlin) config |
| `just fix` | same, with formatting applied |
| `just install` | install the built APK over adb and launch it |

A release is a merged version bump, not a tag pushed by hand.
[version-update-helper](https://github.com/Greewil/version-update-helper) reads
`.vuh` and moves the version on your branch: `vuh sv` shows what this branch
should be versioned as and `vuh uv` writes it into `android/app/build.gradle`
(`just version 0.2.0` does the same by hand).

`.github/workflows/cd.yml` runs on every push to `main`. It compares that
`versionName` against the versions already released here and does nothing unless
the declared one is strictly higher and its tag is still free. When it is, the
workflow builds the APK, publishes a GitHub Release and tags the commit that was
checked.

## Notes on the stack

AGP 9.3.1 with built-in Kotlin (2.4.10), KSP 2.3.11, Room 3 (`androidx.room3`) on the
`AndroidSQLiteDriver`, Compose from BOM 2026.06.01, `minSdk 31`, `targetSdk 37`.

Two deliberate omissions:

* **Material 3 Expressive** ships only in `compose-material3:1.5.0-alpha*`, which has
  reverted promotions and renamed public API inside the last four months. The expressive
  feel here is hand-rolled on stable Material 3 instead — springy motion, large rounded
  shapes, dynamic colour, tonal surfaces.
* **`material-icons-extended`** is frozen and no longer recommended; the handful of icons
  the app needs are Material Symbols shipped as vector drawables.
