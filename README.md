# SASE Android

Android client foundation for the SASE mobile MVP. The app is a Kotlin/Jetpack
Compose client for the host-side SASE mobile gateway; it does not run agents or
embed SASE core logic on the phone.

## Scope

- Package and application ID: `org.sase.mobile`
- Single Android application module: `:app`
- Compose Material 3 shell with inbox, notification detail, and settings routes
- Versioned Gradle catalog for Kotlin, Compose, Navigation, serialization,
  coroutines, OkHttp, DataStore, lint, and test dependencies
- Baseline unit tests plus a Compose instrumentation smoke test
- CI gate for unit tests, lint, and debug APK assembly
- Keystore-backed bearer-token storage, persisted paired-host metadata, manual
  pairing, QR payload parsing/scanning, and settings host management

Later MVP phases will add checked gateway contract fixtures, typed API DTOs,
SSE reconnect behavior, local notification cache, and real inbox/detail data
flows.

## Pairing QR Payload

The settings screen accepts the same pairing payload from camera scans or a
pasted payload field. Payloads are data only; the app rejects arbitrary command,
path, query, and fragment fields.

JSON form:

```json
{
  "schema_version": 1,
  "type": "sase_mobile_pair",
  "base_url": "http://127.0.0.1:7629",
  "pairing_id": "pair_abc123",
  "code": "123456",
  "host_label": "workstation"
}
```

URI form:

```text
sase://pair?base_url=http%3A%2F%2F127.0.0.1%3A7629&pairing_id=pair_abc123&code=123456&host_label=workstation
```

## Local Setup

Install:

- JDK 21
- Android SDK command-line tools
- Android platform `android-35`
- Android build tools `35.0.0`

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK path if your environment
does not discover it automatically.

## Build And Test

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Run connected instrumentation tests when an emulator or device is available:

```bash
./gradlew connectedDebugAndroidTest
```

The Android app is expected to develop against the gateway contract from the
SASE core repository:

```text
../sase-core/crates/sase_gateway/contracts/api_v1/mobile_api_v1.json
```

The app should treat the gateway as authoritative and keep local state limited
to session continuity and offline display.
