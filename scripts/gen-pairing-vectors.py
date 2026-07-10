#!/usr/bin/env python3
"""Deterministic generator for the Bocan pairing golden vectors.

The pairing code and the confirm proof are defined by sync-protocol.md section 4.
Both the Android client (:core:sync PairingCode) and the macOS server implement
exactly this derivation, and both test suites load the byte-identical vectors
this script emits. Never hand-edit the vectors file: regenerate it here and copy
it to the Mac repo when phase-mac-1 is built.

Derivation (protocol section 4 step 3), given two lowercase hex SHA-256
fingerprints and two 32-byte nonces:

    fpLo = min(fpMac, fpPhone)            lexicographic on the hex strings
    fpHi = max(fpMac, fpPhone)
    key  = noncePhone || nonceMac         raw bytes, phone nonce first
    msg  = b"bocan-pair-v1" || fpLo || fpHi   ASCII of the literal and hex strings
    code = int(first 8 bytes of HMAC-SHA256(key, msg), big-endian, unsigned)
           mod 1_000_000, rendered zero-padded to six digits

    proof = base64(HMAC-SHA256(key = code ASCII, msg = sessionId ASCII))

Everything here is seeded from fixed strings so the output is reproducible on any
machine with a stock Python 3. Run: python3 scripts/gen-pairing-vectors.py
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
from pathlib import Path

LABEL = b"bocan-pair-v1"
MODULUS = 1_000_000
OUT_PATH = Path(__file__).resolve().parent.parent / (
    "core/sync/src/test/resources/fixtures/pairing-vectors.json"
)


def fingerprint(seed: str) -> str:
    """A plausible lowercase hex SHA-256 fingerprint, deterministic from seed."""
    return hashlib.sha256(f"cert:{seed}".encode()).hexdigest()


def nonce(seed: str) -> bytes:
    """A deterministic 32-byte nonce standing in for a random one."""
    return hashlib.sha256(f"nonce:{seed}".encode()).digest()


def derive_code(fp_mac: str, fp_phone: str, nonce_phone: bytes, nonce_mac: bytes) -> str:
    fp_lo, fp_hi = sorted((fp_mac, fp_phone))
    key = nonce_phone + nonce_mac
    msg = LABEL + fp_lo.encode("ascii") + fp_hi.encode("ascii")
    digest = hmac.new(key, msg, hashlib.sha256).digest()
    value = int.from_bytes(digest[:8], "big")
    return f"{value % MODULUS:06d}"


def derive_proof(code: str, session_id: str) -> str:
    mac = hmac.new(code.encode("ascii"), session_id.encode("ascii"), hashlib.sha256)
    return base64.b64encode(mac.digest()).decode("ascii")


# (mac seed, phone seed, phone-nonce seed, mac-nonce seed, sessionId). The seeds
# are chosen so the set exercises both fpMac < fpPhone and fpMac > fpPhone.
CASES = [
    ("mac-alpha", "phone-alpha", "np-alpha", "nm-alpha", "11111111-1111-4111-8111-111111111111"),
    ("mac-bravo", "phone-bravo", "np-bravo", "nm-bravo", "22222222-2222-4222-8222-222222222222"),
    ("mac-charlie", "phone-charlie", "np-charlie", "nm-charlie", "33333333-3333-4333-8333-333333333333"),
    ("mac-delta", "phone-delta", "np-delta", "nm-delta", "44444444-4444-4444-8444-444444444444"),
    ("mac-echo", "phone-echo", "np-echo", "nm-echo", "55555555-5555-4555-8555-555555555555"),
]


def build() -> dict:
    vectors = []
    for mac_seed, phone_seed, np_seed, nm_seed, session_id in CASES:
        fp_mac = fingerprint(mac_seed)
        fp_phone = fingerprint(phone_seed)
        nonce_phone = nonce(np_seed)
        nonce_mac = nonce(nm_seed)
        code = derive_code(fp_mac, fp_phone, nonce_phone, nonce_mac)
        vectors.append(
            {
                "fpMac": fp_mac,
                "fpPhone": fp_phone,
                "noncePhoneBase64": base64.b64encode(nonce_phone).decode("ascii"),
                "nonceMacBase64": base64.b64encode(nonce_mac).decode("ascii"),
                "expectedCode": code,
                "sessionId": session_id,
                "expectedProofBase64": derive_proof(code, session_id),
            }
        )
    return {
        "comment": (
            "Golden vectors for sync-protocol.md section 4. Shared byte-for-byte "
            "with the Mac repo. Regenerate with scripts/gen-pairing-vectors.py; "
            "never hand-edit."
        ),
        "label": LABEL.decode("ascii"),
        "vectors": vectors,
    }


def main() -> None:
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    payload = build()
    OUT_PATH.write_text(json.dumps(payload, indent=2) + "\n")
    print(f"Wrote {len(payload['vectors'])} vectors to {OUT_PATH}")


if __name__ == "__main__":
    main()
