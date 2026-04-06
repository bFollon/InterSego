# App Attestation — Research Notes

> **Status: Not implemented.** This document captures the research done on app attestation so it can be picked up later if needed.

## Why This Was Researched

The server currently authenticates clients with a static API key sent as `Authorization: Bearer <token>`. This key is bundled into the app binary at build time. Since InterSego is distributed via the App Store / Google Play, the binary is publicly downloadable — meaning any determined person can extract the key and call the API directly.

This is an acceptable risk given the low sensitivity of the data (bus boarding times), but the question of "what does a proper solution look like?" was worth answering.

## The Public Client Problem

There is no way to embed a true secret in a public app binary. Any key, token, or credential baked into the binary can be extracted with standard reverse-engineering tools. This is a fundamental constraint of mobile app distribution, not a solvable problem at the code level.

The right framing is therefore not "how do I keep my secret safe?" but "how do I prove that a request came from my genuine, unmodified app running on a real device?"

That question has a real answer: **cryptographic app attestation**.

---

## How App Attestation Works

Apple and Google provide platform-level APIs that cryptographically bind a request to a genuine copy of a specific app running on a real, uncompromised device. They do this using the device's hardware security enclave.

### iOS: App Attest (DeviceCheck framework)

Available since iOS 14. The flow has two phases.

#### Phase 1 — Key Registration (once per install)

```
App                          Server                     Apple
 │                               │                          │
 │── GET /auth/challenge ───────>│                          │
 │<── { challenge: "abc123" } ──│                          │
 │                               │                          │
 │── DCAppAttestService          │                          │
 │   .generateKey() ────────────────────────────────────>  │
 │<── keyId ───────────────────────────────────────────── │
 │                               │                          │
 │── .attestKey(keyId,           │                          │
 │     SHA256(challenge)) ──────────────────────────────> │
 │<── attestationObject (CBOR) ──────────────────────── │
 │                               │                          │
 │── POST /auth/attest ─────────>│                          │
 │   { keyId, attestationObject }│                          │
 │                               │  verify CBOR:            │
 │                               │  - cert chain → Apple CA │
 │                               │  - rpId = SHA256(bundle) │
 │                               │  - aaguid known value    │
 │                               │  - extract public key    │
 │                               │  store keyId → publicKey │
 │<── 200 OK ───────────────────│                          │
```

After this, the server holds a verified public key that is cryptographically tied to a specific app + bundle ID + Apple device.

#### Phase 2 — Assertion (per session or per request)

For each API call, the app signs a hash of the request (or a server-issued challenge) with the attested private key. The server verifies the signature using the stored public key and checks a monotonically increasing counter to prevent replay attacks.

```
App                          Server
 │                               │
 │── GET /auth/challenge ───────>│  (optional: use request body as hash instead)
 │<── { challenge: "xyz789" } ──│
 │                               │
 │── .generateAssertion(keyId,   │
 │     SHA256(challenge+body)) ──│
 │<── assertionObject ──────────│
 │                               │
 │── POST /boardings ───────────>│
 │   X-Assertion: <object>       │
 │   X-Key-Id: <keyId>          │
 │                               │  verify signature with publicKey
 │                               │  check counter > stored counter
 │                               │  update stored counter
 │<── 201 Created ──────────────│
```

### Android: Play Integrity API

Google's equivalent. The app requests an integrity token from the Play Integrity API (which involves a call to Google's servers), then sends that token to your server. Your server forwards it to Google's Integrity API, which returns a verdict containing:

- `appIntegrity.appRecognitionVerdict` — is this a genuine, unmodified copy of your app?
- `deviceIntegrity.deviceRecognitionVerdict` — is this a genuine Android device?
- `accountDetails.appLicensingVerdict` — did this account obtain the app from the Play Store?

The token is short-lived (minutes) and can only be used once.

---

## Server-Side Implementation Sketch

Three new pieces would be needed.

### 1. Challenge store

Short-lived, single-use random values to prevent replay attacks. Can be an in-memory `Map` with TTL expiry, or Redis for persistence across restarts.

```ts
// GET /auth/challenge
const challenge = crypto.randomBytes(32).toString('base64url');
await challengeStore.set(challenge, { expiresAt: Date.now() + 60_000 });
return { challenge };
```

### 2. Attestation endpoint

```ts
// POST /auth/attest  { keyId, attestationObject, platform: 'ios' | 'android' }
if (platform === 'ios') {
  const publicKey = await verifyAppleAttestation(
    keyId,
    attestationObject,
    challenge,
    process.env.IOS_BUNDLE_ID,
  );
  await keyStore.set(keyId, { publicKey, counter: 0 });
} else {
  const verdict = await verifyPlayIntegrityToken(integrityToken);
  // check verdict fields, store a device token
}
```

The Apple attestation verification is the hard part: there is no official Apple server library. It requires:

- Parsing CBOR (the attestation object format)
- Verifying an X.509 certificate chain up to Apple's App Attest root CA
- Checking `rpId` field equals `SHA256(bundleId)`
- Checking `aaguid` against Apple's known values (`appattestdevelop` or `appattest`)
- Extracting the COSE public key from the authenticator data

Community libraries exist (search npm for `apple-app-attest`), but none are official or widely maintained.

### 3. Assertion middleware (replaces `requireApiKey`)

```ts
export async function requireAttestation(request, reply) {
  const keyId = request.headers['x-key-id'];
  const assertionB64 = request.headers['x-assertion'];

  const { publicKey, counter } = await keyStore.get(keyId);
  if (!publicKey) return reply.status(401).send({ error: 'Unauthorized' });

  const clientDataHash = SHA256(request.body);
  const newCounter = await verifyAssertion(assertionB64, publicKey, counter, clientDataHash);

  await keyStore.update(keyId, { counter: newCounter });
}
```

---

## The Simpler Alternative: Firebase App Check

Firebase App Check wraps both App Attest (iOS) and Play Integrity (Android) and handles all the verification complexity. The server reduces to a single JWT check:

```ts
import { getAppCheck } from 'firebase-admin/app-check';

export async function requireAppCheck(request, reply) {
  const token = request.headers['x-firebase-appcheck'];
  try {
    await getAppCheck().verifyToken(token); // throws if invalid
  } catch {
    return reply.status(401).send({ error: 'Unauthorized' });
  }
}
```

The app sends one additional header. All the CBOR parsing, certificate verification, and Google API calls are handled by Firebase SDKs.

**Trade-off:** adds a Firebase dependency to what is currently a dependency-free backend.

---

## Complexity Assessment

| Task | Effort | Notes |
|---|---|---|
| Challenge endpoint | 1 hour | Trivial |
| iOS raw attestation verification | 3–5 days | No official library; manual CBOR + X.509 |
| Android Play Integrity | 1–2 days | Google API is documented; needs GCP credentials |
| Assertion middleware | 1 day | Once attestation works, this is straightforward |
| Firebase App Check (both platforms) | 1–2 days | Much simpler; adds Firebase dependency |

---

## Recommendation When Revisiting

Given the data sensitivity (bus boarding times, no PII), the current API key is acceptable for an MVP with a small trusted user base.

If abuse is ever observed or the data handled becomes more sensitive, the recommended path is:

1. **Firebase App Check** — fastest to implement correctly; handles iOS and Android uniformly
2. **Raw App Attest + Play Integrity** — if Firebase dependency is unacceptable

Whichever path is chosen, the existing `requireApiKey` middleware in `src/middleware/auth.ts` is the only server file that needs replacing. The routes themselves don't change.

---

## References

- [Apple: Establishing Your App's Integrity](https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity)
- [Apple: Validating Apps That Connect to Your Server](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server)
- [Google: Play Integrity API](https://developer.android.com/google/play/integrity)
- [Firebase App Check](https://firebase.google.com/docs/app-check)
