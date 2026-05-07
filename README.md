# SASE Android

Android client foundation for the SASE mobile MVP. The app is a Kotlin/Jetpack
Compose client for the host-side SASE mobile gateway; it does not run agents or
embed SASE core logic on the phone.

## Scope

- Package and application ID: `org.sase.mobile`
- Single Android application module: `:app`
- Compose Material 3 shell with inbox, notification detail, launch, agents,
  helpers, update, and settings routes
- Versioned Gradle catalog for Kotlin, Compose, Navigation, serialization,
  coroutines, OkHttp, DataStore, lint, and test dependencies
- Baseline unit tests plus a Compose instrumentation smoke test
- CI gate for unit tests, lint, and debug APK assembly
- Keystore-backed bearer-token storage, persisted paired-host metadata, manual
  pairing, QR payload parsing/scanning, and settings host management
- Notification detail action controls for plan approval, HITL, and user
  question choices, including feedback/custom-answer draft preservation
- Native agent management for list, resume/wait prompts, text launch, image
  launch, kill, retry, and recent launch result display
- Native helper surfaces for ChangeSpec tags, xprompt catalog entries, bead
  lookup/detail, and SASE update start/status
- Fake gateway smoke harness for REST, pairing, notification state mutations,
  Epic 6 action/agent/helper/update routes, and SSE resync/reconnect paths
- Automated instrumentation smoke coverage for pairing, inbox/detail action UI,
  launch, agents, helpers, update, and settings navigation

Epic 7 adds notification permission UX, local hint rendering, and foreground
connected mode. Later phases will add push delivery, packaging, security
review, and release hardening.

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
notification action mutations, agent launch/lifecycle routes, helper pickers,
update start/status, attachments, and SSE events while validating bearer auth
and request payloads.

The main local fake-gateway gate is:

```bash
./gradlew testDebugUnitTest
```

The highest-value route and state coverage lives in:

- `GatewayApiClientTest`: contract-shaped request/response coverage for all
  Epic 6 routes, including stale/already-handled/ambiguous/unsupported errors.
- `ActionRepositoryTest`: detail action submission, text draft preservation,
  stale refresh, and already-handled recovery.
- `AgentRepositoryTest`: list, resume/wait options, text launch, image launch,
  kill, retry, and `agents_changed` refresh.
- `HelperRepositoryTest` and `UpdateRepositoryTest`: ChangeSpec/xprompt/bead
  helpers, partial-success rendering inputs, update polling, remembered jobs,
  already-running errors, and `helpers_changed` refresh.

Connected smoke tests run against Compose and MockWebServer when an emulator or
device is available:

```bash
./gradlew connectedDebugAndroidTest
```

Those paths cover first-screen navigation, pairing, inbox/detail navigation,
action controls, text launch, image attachment handling, agents list/kill/retry
UI, helper insertion/search, update start/status UI, SSE resync, and forgetting
the paired host.

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
7. Open a plan, HITL, or question notification detail. Verify direct actions
   submit, feedback/custom-answer text survives navigation and failed submit,
   stale/already-handled responses refresh detail, and auth/offline failures
   point to Settings or retry.
8. Open Launch. Submit a text prompt containing raw SASE syntax such as
   `%model`, `%runtime`, `#gh:...`, and `#bd/...`; verify the prompt is sent
   unchanged and recent launch results appear on Agents.
9. From Launch, select a camera/gallery image or emulator content URI. Verify
   the displayed filename/content type/size, successful image launch, oversize
   rejection, and prompt preservation after upload failure.
10. Open Agents. Verify the list loads, resume/wait prompts can prefill Launch,
    kill/retry show result states, and `agents_changed` refreshes the list.
11. Open Settings > Helpers. Verify ChangeSpec tags, xprompt entries, and beads
    load; partial-success warnings/skipped rows are visible; helper insertions
    preserve the raw prompt syntax in Launch.
12. Open Settings > Update. Start an update, verify status polling reaches a
    terminal state, restart the app with a remembered running job if practical,
    and verify already-running/failure states are rendered from structured
    fields.
13. Restart the app and verify the paired session, cached inbox, action drafts,
    and remembered update job state restore before the next host refresh.
14. Trigger notification, agent, and helper refreshes on the host, or restart
    the gateway event stream, and verify the app refreshes after
    reconnect/resync.
15. Open Settings and turn on Foreground connected mode. Background the app and
    verify Android shows the persistent SASE connected notification. Trigger a
    gateway event and verify the app refreshes after reconnect/resync when
    reopened. Stop connected mode from Settings or the notification action and
    verify the foreground notification is removed.
16. Return to Settings and forget the host. Verify the app returns to the
    unpaired state and no cached bearer-token state is usable.

## Known Limitations

- Background push delivery, packaging, security review, and release hardening
  remain Epic 7 work.
- Foreground connected mode keeps the REST/SSE path active while Android allows
  its foreground service to run. Push delivery is still required for lower-power
  background hints.
- Attachments are displayed as metadata or opened only through scoped
  authenticated download affordances; arbitrary host file browsing is not part
  of the mobile client.
- The API client is handwritten against the checked contract snapshot; generated
  client adoption is intentionally deferred.
