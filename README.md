# HourByHour

An Android home screen widget that shows your calendar hour by hour, over 1 to 7 days, like the day and week views of a calendar app.

- Events in the same colors as Google Calendar, in the light and dark theme, with contact birthdays as a calendar of their own
- From 1 to 7 days, starting today or from Monday
- Adjustable background transparency, hours shown and choice of calendars for every widget
- Tap an event or a day to open it in your calendar app
- Resizable: hours and titles adapt to the size of the widget

HourByHour reads the calendar stored on the phone, which Google Calendar keeps in sync. No sign-in and no internet access: your events never leave your phone ([privacy policy](https://alexmarchi28.github.io/hourbyhour-privacy/)).

## Build

Requires JDK 21 and the Android SDK (`local.properties` with `sdk.dir`), Android 12 or later on the phone.

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests
./gradlew installDebug         # install on a connected phone
```

## License

Copyright (C) 2026 Alex Marchi

HourByHour is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The license covers the code, not the name "HourByHour" or its icon: please do not use them for modified versions you distribute.
