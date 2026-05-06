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
- Fake gateway smoke harness for REST, pairing, notification state mutations,
  and SSE resync/reconnect paths
- Automated instrumentation smoke path for pairing, inbox/detail navigation,
  mark-read, SSE resync, and forgetting the paired host

Later MVP phases will add action mutation UI, richer agent/helper screens,
background delivery, foreground service behavior, and release hardening.

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

The checked Android snapshot is:

```text
app/src/test/resources/contracts/mobile_api_v1.json
```

The app should treat the gateway as authoritative and keep local state limited
to session continuity and offline display.

## Fake Gateway Smoke

Unit tests include a route-based fake gateway harness that serves health,
pairing, session, notifications, notification detail, read/dismiss mutations,
and SSE events while validating bearer auth and pairing payloads. The connected
smoke test uses the same gateway shape through MockWebServer:

```bash
./gradlew connectedDebugAndroidTest
```

That path launches the app unpaired, pairs with test data, refreshes the inbox,
opens detail, marks a notification read, processes a resync-required SSE event,
and forgets the paired host.

## Manual Real-Host Smoke Checklist

1. From the SASE repo, start the local mobile gateway:

   ```bash
   sase mobile gateway start
   ```

2. Confirm the gateway prints or exposes a pairing payload with gateway URL,
   pairing ID, one-time code, and optional host label.
3. Install or launch the debug Android app. For an emulator, use the host alias
   `http://10.0.2.2:<port>` if the gateway binds to host loopback.
4. Open Settings, enter the gateway URL, pairing ID, code, host label, and
   device display name, then pair the host.
5. Verify Settings shows the paired host and Check session succeeds.
6. Open Inbox and refresh. Verify notifications load from the host or the empty
   inbox state renders cleanly.
7. Open a notification detail, then mark it read or dismiss it.
8. Restart the app and verify the paired session and cached inbox state are
   restored before the next host refresh.
9. Trigger a notification-state refresh on the host, or restart the gateway
   event stream, and verify the app refreshes after reconnect/resync.
10. Return to Settings and forget the host. Verify the app returns to the
    unpaired state and no cached bearer-token state is usable.

## Known Limitations

- Plan approval, HITL, and question action controls are intentionally deferred.
- Agent/helper screens and update management are intentionally deferred.
- Background push delivery, foreground service behavior, notification
  permission UX, packaging, and release hardening are intentionally deferred.
- Attachments are displayed as metadata only; arbitrary file download/viewing is
  not part of this foundation.
- The API client is handwritten against the checked contract snapshot; generated
  client adoption is intentionally deferred.
