# Privacy Policy

**Effective date:** May 19, 2026

Bruno Follon ("the developer") built InterSego as a free application. Its source code is publicly available for transparency, though redistribution and commercial use are not permitted. This page explains what data the app accesses, how it is used, and your choices.

## Data the App Accesses

### Bus Timetable Data

InterSego fetches bus schedule data from a self-hosted server operated by the developer. This is used to keep timetables up to date without requiring an app update. No personal data is sent during this process — only a route identifier and an ETag for cache validation.

### Location (Optional)

The app may request access to your device's location to find nearby stops and show them on a map. Location data is used **only on your device** and is never transmitted to any server. You can deny location permission and the app will still function normally.

### Live Boarding Confirmations (Optional, User-Initiated)

If you tap "Estoy en el autobús", the app posts an anonymous boarding event — containing the route, direction, and a timestamp — to a self-hosted server operated by the developer. This data is used solely to show other users a real-time ETA estimate. It is automatically deleted after 4 hours. No personal data or device identifiers are included.

## Analytics and Error Reporting (Optional, Consent-Gated)

The app includes two optional monitoring features. Both are **disabled by default** and require explicit opt-in via the consent screen shown on first launch. You can change your preferences at any time in Settings.

### Usage Analytics (Aptabase)

If you opt in, the app sends anonymous usage events to a self-hosted Aptabase instance operated by the developer. These events record in-app actions such as:

- App launch
- Route and stop selections
- Departures viewed
- Reminders set
- Boarding confirmations

No personal information, device identifiers, or location data is included in these events.

### Error Reporting (Bugsink)

If you opt in, the app sends technical error reports to a self-hosted Bugsink instance operated by the developer when a crash or unexpected error occurs. Reports contain stack traces and technical context to help diagnose and fix bugs. No personal information is included.

**Both services are self-hosted by the developer. Your data is not shared with any third-party analytics or advertising companies.**

## Data the App Does NOT Collect

- Personal information (name, email, phone number)
- Device identifiers or advertising IDs
- Precise location (location processing happens on-device only)
- Cookies or tracking technologies
- Any data shared with third-party companies

## Data Storage

Timetable data and app settings are stored locally on your device. Anonymous boarding events sent by you are stored on a self-hosted server for up to 4 hours. If you have opted into analytics or error reporting, those events are retained on self-hosted infrastructure for diagnostic purposes. Uninstalling the app removes all locally stored data.

## Children's Privacy

InterSego does not knowingly collect any personal data from anyone, including children under 13.

## Changes to This Policy

This policy may be updated from time to time. Changes will be reflected in this file with an updated effective date. Continued use of the app after changes constitutes acceptance of the revised policy.

## Contact

If you have questions about this privacy policy, you can open an issue on the [GitHub repository](https://github.com/bFollon/InterSego) or contact the developer directly.
