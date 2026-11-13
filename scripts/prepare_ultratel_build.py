#!/usr/bin/env python3
"""Prepare UltraTel's private Android build inputs inside a CI runner.

This script is intended to run after actions/checkout. It does NOT commit
credentials or the signing keystore to Git and never prints secret values.
"""
from __future__ import annotations

import base64
import binascii
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILD_VARS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
GRADLE_PROPERTIES = ROOT / "gradle.properties"
KEYSTORE = ROOT / "TMessagesProj/config/release.keystore"
ALIAS = "ultratel"


def fail(message: str) -> None:
    raise SystemExit("UltraTel build setup: " + message)


def required_env(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        fail("Missing GitHub Actions secret " + name)
    return value


def replace_exactly_once(text: str, pattern: str, replacement: str) -> str:
    result, count = re.subn(pattern, lambda _match: replacement, text)
    if count != 1:
        fail("BuildVars.java has changed; expected one " + pattern)
    return result


def escape_java_property(value: str) -> str:
    # Gradle reads these overrides as java.util.Properties.
    return (
        value.replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("=", "\\=")
        .replace(":", "\\:")
        .replace("#", "\\#")
        .replace("!", "\\!")
    )


def main() -> None:
    app_id = required_env("ULTRATEL_API_ID").strip()
    app_hash = required_env("ULTRATEL_API_HASH").strip()
    key_b64 = required_env("ULTRATEL_KEYSTORE_B64")
    store_password = required_env("ULTRATEL_KEYSTORE_PASSWORD")

    if not re.fullmatch(r"[1-9]\d{0,9}", app_id):
        fail("ULTRATEL_API_ID must be a positive decimal integer.")
    if int(app_id) >= 2**31:
        fail("ULTRATEL_API_ID is out of Android integer range.")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", app_hash):
        fail("ULTRATEL_API_HASH must be 32 hexadecimal characters.")
    if len(store_password) < 12:
        fail("ULTRATEL_KEYSTORE_PASSWORD must be at least 12 characters.")

    try:
        decoded = base64.b64decode("".join(key_b64.split()), validate=True)
    except (binascii.Error, ValueError):
        fail("ULTRATEL_KEYSTORE_B64 is not valid Base64.")
    if not (300 <= len(decoded) <= 100_000):
        fail("The signing keystore has an unexpected size.")

    KEYSTORE.parent.mkdir(parents=True, exist_ok=True)
    KEYSTORE.write_bytes(decoded)  # Replaces the upstream *sample* signing key in CI.
    KEYSTORE.chmod(0o600)

    # Verify the archive and alias before starting the expensive native build.
    test = subprocess.run(
        [
            "keytool", "-list",
            "-keystore", str(KEYSTORE),
            "-storepass", store_password,
            "-alias", ALIAS,
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if test.returncode != 0:
        fail("Cannot unlock the signing key (password or alias differs).")

    src = BUILD_VARS.read_text(encoding="utf-8")
    src = replace_exactly_once(
        src, r"public static int APP_ID = 0;",
        f"public static int APP_ID = {app_id};",
    )
    src = replace_exactly_once(
        src, r'public static String APP_HASH = "";',
        f'public static String APP_HASH = "{app_hash.lower()}";',
    )
    BUILD_VARS.write_text(src, encoding="utf-8")

    # Applied on this runner only: never commit plaintext passwords to the repo.
    with GRADLE_PROPERTIES.open("a", encoding="utf-8") as f:
        f.write("\n# UltraTel signing overrides for this CI run only\n")
        f.write("RELEASE_KEY_ALIAS=" + ALIAS + "\n")
        f.write("RELEASE_KEY_PASSWORD=" + escape_java_property(store_password) + "\n")
        f.write("RELEASE_STORE_PASSWORD=" + escape_java_property(store_password) + "\n")

    print("UltraTel: API credentials and private signing key prepared in CI.")


if __name__ == "__main__":
    main()
