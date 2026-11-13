# Privacy

This launcher has no account, no analytics, no advertising identifier, no crash reporting and no telemetry.
Everything it stores stays in the app's private storage on your device and is excluded from backups.

## Permissions

| Permission | Why | When it is asked |
|---|---|---|
| Precise and approximate location | Android only reveals the name of the connected Wi-Fi network to apps with precise location. The launcher uses it to switch profiles automatically. | Only when you turn on *Automatic switching*. Never in the background. |
| Approximate location (alone) | *Use my location* for the weather: the last location known to the system picks the nearest city from the built-in list. The location itself is neither stored nor sent. | Only when you tap *Use my location*. |
| Calendar | Shows your next event on the home screen. Events are read when shown and never stored or logged. | Only when you tap the button in the panel. |
| Internet | Weather from Open-Meteo (see below). | Granted at install (normal permission). |
| Network and Wi-Fi state | Notices Wi-Fi connections and whether Wi-Fi is on, without network names. | Granted at install. |
| Expand status bar | Swiping down on the home screen opens the notification shade. | Granted at install. |
| Request package deletion | *Uninstall* in an app's menu opens the system confirmation. | Granted at install. |

Without any optional permission, without network and without automatic switching, you can still open all your apps.

Saved Wi-Fi network names are stored on the device only, never sent and never written to logs.

## External service: Open-Meteo

The only external service is [Open-Meteo](https://open-meteo.com/). Weather is **off** until you choose a city.

- **Forecast**: the coordinates of the chosen city (or of the built-in city nearest to you), rounded to about 1 km, are sent to `api.open-meteo.com`. Your device's own position is never sent. Requests happen only while the home screen, the panel or the weather settings are visible, at most every 3 hours (8 a day at most), plus one request when you change city.
- **Online city search**: what you typed and your device language are sent to `geocoding-api.open-meteo.com`, only when you tap *Search online*. Typing alone searches the built-in list without sending anything.
- **What Open-Meteo sees**: your IP address and the request above. See the [Open-Meteo terms](https://open-meteo.com/en/terms) for how they handle it.
- **Without the service**: the last forecast is shown dimmed after 4 hours and hidden after 12 hours; the rest of the launcher works normally.

No key, identifier or device-specific header is sent.

## Contact

Questions and reports: [issues](https://github.com/tomlmbl/cobble-home/issues) on the project repository.
