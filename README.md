# VolunteerDash

VolunteerDash is an Android prototype for finding volunteer opportunities and fitting shifts around your schedule. The Android app includes the Shift Fit update.

## Android

Open `android/` in Android Studio with JDK 17 or newer and Android SDK 37 installed. To build a debug APK from Windows PowerShell:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

## Backend

The Fastify API uses SQLite and requires Node.js 24 or newer.

```sh
cd backend
npm ci
npm start
```

See [the API notes](docs/API.md) for details.
