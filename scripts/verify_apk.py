#!/usr/bin/env python3
"""Verify Vita's update identity before preparing GitHub release assets."""

from __future__ import annotations

import argparse
import configparser
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile


PROJECT_ROOT = Path(__file__).resolve().parent.parent
BUILD_TOOLS_VERSION = "35.0.0"
SHA256_PATTERN = re.compile(r"[0-9a-fA-F]{64}\Z")
APPLICATION_ID_PATTERN = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+\Z")
VERSION_NAME_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,99}\Z")
REPOSITORY_PATTERN = re.compile(r"[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*\Z")


class VerificationError(Exception):
    """The candidate cannot safely be published as this application's update."""


@dataclass(frozen=True)
class DistributionIdentity:
    version_name: str
    version_code: int
    application_id: str
    signing_certificate_sha256: str
    update_repository: str


@dataclass(frozen=True)
class ApkMetadata:
    application_id: str
    version_code: int
    version_name: str
    debuggable: bool


@dataclass(frozen=True)
class AndroidTools:
    apksigner: Path
    aapt: Path


def read_properties(path: Path) -> dict[str, str]:
    """Read the simple key=value property files shared with Gradle."""
    parser = configparser.ConfigParser(
        interpolation=None,
        delimiters=("=",),
        comment_prefixes=("#", "!"),
        strict=True,
        empty_lines_in_values=False,
    )
    parser.optionxform = str
    try:
        parser.read_string("[properties]\n" + path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, configparser.Error) as exc:
        raise VerificationError(f"Cannot read {path.name}: {exc}") from exc
    if parser.sections() != ["properties"] or parser.defaults():
        raise VerificationError(f"{path.name} must contain only key=value properties")
    properties = dict(parser.items("properties"))
    if any("\n" in value for value in properties.values()):
        raise VerificationError(f"{path.name} must use one line per property")
    return properties


def required_property(properties: dict[str, str], key: str, filename: str) -> str:
    value = properties.get(key, "").strip()
    if not value:
        raise VerificationError(f"Missing {key} in {filename}")
    return value


def parse_version_code(value: str) -> int:
    if not re.fullmatch(r"[1-9][0-9]*", value):
        raise VerificationError("versionCode must be a positive decimal integer")
    code = int(value)
    if code > 2_100_000_000:
        raise VerificationError("versionCode exceeds Android's maximum of 2100000000")
    return code


def load_identity(
    version_path: Path = PROJECT_ROOT / "version.properties",
    distribution_path: Path = PROJECT_ROOT / "distribution.properties",
) -> DistributionIdentity:
    version = read_properties(version_path)
    distribution = read_properties(distribution_path)
    name = required_property(version, "versionName", version_path.name)
    if not VERSION_NAME_PATTERN.fullmatch(name):
        raise VerificationError("versionName must be a safe release filename component")
    code = parse_version_code(required_property(version, "versionCode", version_path.name))
    application_id = required_property(distribution, "applicationId", distribution_path.name)
    if not APPLICATION_ID_PATTERN.fullmatch(application_id):
        raise VerificationError("applicationId is not a valid Android package name")
    certificate = required_property(distribution, "signingCertificateSha256", distribution_path.name)
    if not SHA256_PATTERN.fullmatch(certificate):
        raise VerificationError("signingCertificateSha256 must contain exactly 64 hexadecimal characters")
    repository = required_property(distribution, "updateRepository", distribution_path.name)
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise VerificationError("updateRepository must use GitHub's owner/repository format")
    return DistributionIdentity(name, code, application_id, certificate.lower(), repository)


def parse_signing_certificate(output: str) -> str:
    signer_numbers: set[int] = set()
    certificates: list[tuple[int, str]] = []
    for line in output.splitlines():
        line = line.strip()
        signer = re.match(r"Signer #(\d+)\b", line)
        if signer:
            signer_numbers.add(int(signer.group(1)))
        if re.match(r"Signer #\d+ certificate SHA-256 digest:", line):
            match = re.fullmatch(r"Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", line)
            if not match:
                raise VerificationError("apksigner returned a malformed SHA-256 certificate digest")
            certificates.append((int(match.group(1)), match.group(2).lower()))
        if line.startswith("Number of signers:") and line != "Number of signers: 1":
            raise VerificationError("APK must have exactly one signer")
    if signer_numbers != {1} or len(certificates) != 1 or certificates[0][0] != 1:
        raise VerificationError("APK must have exactly one signer with a SHA-256 certificate digest")
    return certificates[0][1]


def parse_apk_metadata(output: str) -> ApkMetadata:
    package_lines = [line.strip() for line in output.splitlines() if line.strip().startswith("package:")]
    if len(package_lines) != 1:
        raise VerificationError("aapt must return exactly one package metadata line")
    try:
        fields: dict[str, str] = {}
        for token in shlex.split(package_lines[0][len("package:"):]):
            key, separator, value = token.partition("=")
            if not separator or key in fields:
                raise VerificationError("aapt returned malformed or duplicate package metadata")
            fields[key] = value
    except ValueError as exc:
        raise VerificationError("aapt returned invalid quoted package metadata") from exc
    application_id = fields.get("name", "")
    name = fields.get("versionName", "")
    if not APPLICATION_ID_PATTERN.fullmatch(application_id) or not name:
        raise VerificationError("aapt package metadata is missing a valid name or versionName")
    code = parse_version_code(fields.get("versionCode", ""))
    debuggable = any(line.strip().startswith("application-debuggable") for line in output.splitlines())
    return ApkMetadata(application_id, code, name, debuggable)


def validate_identity(identity: DistributionIdentity, metadata: ApkMetadata, certificate: str) -> None:
    if certificate != identity.signing_certificate_sha256:
        raise VerificationError("APK signing certificate does not match distribution.properties")
    if metadata.application_id != identity.application_id:
        raise VerificationError(f"APK package is {metadata.application_id}, expected {identity.application_id}")
    if metadata.version_code != identity.version_code:
        raise VerificationError(f"APK versionCode is {metadata.version_code}, expected {identity.version_code}")
    if metadata.version_name != identity.version_name:
        raise VerificationError(f"APK versionName is {metadata.version_name}, expected {identity.version_name}")
    if metadata.debuggable:
        raise VerificationError("APK is debuggable; only a non-debuggable update may be published")


def find_android_tools() -> AndroidTools:
    roots = [Path(value).expanduser() for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT") if (value := os.environ.get(key))]
    for root in roots:
        # Match the SDK version installed by our workflows, rather than an unrelated
        # newer SDK preinstalled on GitHub runners with a different CLI format.
        directory = root / "build-tools" / BUILD_TOOLS_VERSION
        apksigner = directory / ("apksigner.bat" if os.name == "nt" else "apksigner")
        aapt = directory / ("aapt.exe" if os.name == "nt" else "aapt")
        if apksigner.is_file() and aapt.is_file():
            return AndroidTools(apksigner, aapt)
    raise VerificationError(
        f"Set ANDROID_HOME or ANDROID_SDK_ROOT to an SDK containing build-tools {BUILD_TOOLS_VERSION}"
    )


def run_tool(command: list[str]) -> str:
    try:
        result = subprocess.run(command, check=False, capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise VerificationError(f"Cannot run {Path(command[0]).name}: {exc}") from exc
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise VerificationError(f"{Path(command[0]).name} failed (exit {result.returncode}): {detail[:2000]}")
    return result.stdout


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_release(apk: Path, output_dir: Path, identity: DistributionIdentity, android_tools: AndroidTools) -> dict:
    if not apk.is_file():
        raise VerificationError("APK input does not exist or is not a regular file")
    original_sha256 = sha256_file(apk)
    certificate = parse_signing_certificate(run_tool([str(android_tools.apksigner), "verify", "--print-certs", str(apk)]))
    metadata = parse_apk_metadata(run_tool([str(android_tools.aapt), "dump", "badging", str(apk)]))
    validate_identity(identity, metadata, certificate)

    asset_name = f"Vita-{identity.version_name}.apk"
    manifest = {
        "versionName": identity.version_name,
        "versionCode": identity.version_code,
        "applicationId": identity.application_id,
        "signingCertificateSha256": certificate,
        "apk": {"name": asset_name, "sha256": original_sha256},
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".vita-release-", dir=output_dir) as staging_dir:
        staging = Path(staging_dir)
        copied_apk = staging / asset_name
        shutil.copyfile(apk, copied_apk)
        if sha256_file(copied_apk) != original_sha256:
            raise VerificationError("APK changed during verification; refusing to publish it")
        manifest_path = staging / "release-manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
        checksum_path = staging / "SHA256SUMS"
        checksum_path.write_text(
            f"{original_sha256}  {asset_name}\n{sha256_file(manifest_path)}  release-manifest.json\n",
            encoding="utf-8",
        )
        for filename in (asset_name, "release-manifest.json", "SHA256SUMS"):
            os.replace(staging / filename, output_dir / filename)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path, help="Signed APK to verify")
    parser.add_argument("--output-dir", required=True, type=Path, help="Directory for verified release assets")
    args = parser.parse_args()
    try:
        android_tools = find_android_tools()
        print(f"Verifying with Android build-tools {android_tools.apksigner.parent.name}")
        manifest = prepare_release(args.apk.resolve(), args.output_dir.resolve(), load_identity(), android_tools)
    except (VerificationError, OSError) as exc:
        print(f"Release verification failed: {exc}", file=sys.stderr)
        return 1
    print(f"Verified {manifest['apk']['name']}: {manifest['applicationId']} ({manifest['versionCode']})")
    print(f"APK SHA-256: {manifest['apk']['sha256']}")
    print("Prepared APK, release-manifest.json and SHA256SUMS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
