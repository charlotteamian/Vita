#!/usr/bin/env python3
"""Tests for the release identity checks; no Android SDK or Gradle required."""

from dataclasses import replace
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import verify_apk


CERTIFICATE = "71b7801a44bd4ee9227e4c0e97e40c5b4b9432db68622a18c0aa58e65c7bb048"
IDENTITY = verify_apk.DistributionIdentity("0.2.0", 2, "com.vita.healthtracker.debug", CERTIFICATE, "charlotteamian/Vita")
METADATA = verify_apk.ApkMetadata(IDENTITY.application_id, 2, "0.2.0", False)
SIGNATURE_OUTPUT = f"Signer #1 certificate DN: CN=Android Debug\nSigner #1 certificate SHA-256 digest: {CERTIFICATE}\n"
BADGING_OUTPUT = "package: name='com.vita.healthtracker.debug' versionCode='2' versionName='0.2.0' compileSdkVersion='35'\nsdkVersion:'28'\n"


class PropertiesTests(unittest.TestCase):
    def load(self, version: str = "versionCode=2\nversionName=0.2.0\n", distribution: str | None = None):
        if distribution is None:
            distribution = f"applicationId={IDENTITY.application_id}\nupdateRepository=charlotteamian/Vita\nsigningCertificateSha256={CERTIFICATE}\n"
        with tempfile.TemporaryDirectory() as directory:
            version_path, distribution_path = Path(directory) / "version.properties", Path(directory) / "distribution.properties"
            version_path.write_text(version, encoding="utf-8")
            distribution_path.write_text(distribution, encoding="utf-8")
            return verify_apk.load_identity(version_path, distribution_path)

    def test_properties_load_version_and_normalize_certificate(self):
        loaded = self.load(distribution=f"# update identity\napplicationId={IDENTITY.application_id}\nupdateRepository=charlotteamian/Vita\nsigningCertificateSha256={CERTIFICATE.upper()}\n")
        self.assertEqual(loaded, IDENTITY)

    def test_rejects_missing_required_version_properties(self):
        for version in ("versionCode=2\n", "versionName=0.2.0\n", "versionCode=2\nversionName=\n"):
            with self.subTest(version=version), self.assertRaises(verify_apk.VerificationError):
                self.load(version)

    def test_rejects_invalid_or_excessive_version_code(self):
        for code in ("0", "-1", "2.0", "02", "+2", "two", "2100000001"):
            with self.subTest(code=code), self.assertRaises(verify_apk.VerificationError):
                self.load(f"versionCode={code}\nversionName=0.2.0\n")

    def test_rejects_version_name_that_can_escape_asset_directory(self):
        for name in ("../0.2.0", "0.2.0/other", "0.2.0\\other", "0.2.0 beta", ".hidden"):
            with self.subTest(name=name), self.assertRaises(verify_apk.VerificationError):
                self.load(f"versionCode=2\nversionName={name}\n")

    def test_rejects_duplicate_properties_and_multiline_values(self):
        for text in ("versionCode=2\nversionCode=3\nversionName=0.2.0\n", "versionCode=2\nversionName=0.2.0\n continued\n"):
            with self.subTest(text=text), self.assertRaises(verify_apk.VerificationError):
                self.load(text)

    def test_rejects_invalid_distribution_identity(self):
        base = f"applicationId={IDENTITY.application_id}\nupdateRepository=charlotteamian/Vita\nsigningCertificateSha256={CERTIFICATE}\n"
        mutations = (
            base.replace(IDENTITY.application_id, "bad-package"),
            base.replace("charlotteamian/Vita", "https://github.com/charlotteamian/Vita"),
            base.replace(CERTIFICATE, "abc"),
            base.replace(f"signingCertificateSha256={CERTIFICATE}\n", ""),
        )
        for text in mutations:
            with self.subTest(text=text), self.assertRaises(verify_apk.VerificationError):
                self.load(distribution=text)


class SigningTests(unittest.TestCase):
    def test_reads_single_certificate_and_normalizes_uppercase(self):
        self.assertEqual(verify_apk.parse_signing_certificate(SIGNATURE_OUTPUT.replace(CERTIFICATE, CERTIFICATE.upper())), CERTIFICATE)

    def test_rejects_unsigned_output(self):
        with self.assertRaises(verify_apk.VerificationError):
            verify_apk.parse_signing_certificate("DOES NOT VERIFY\n")

    def test_rejects_multiple_signers_even_when_certificate_matches(self):
        for extra in (
            SIGNATURE_OUTPUT.replace("Signer #1", "Signer #2"),
            "Signer #2 certificate DN: CN=Other\n",
            "Number of signers: 2\n",
            SIGNATURE_OUTPUT,
        ):
            with self.subTest(extra=extra), self.assertRaises(verify_apk.VerificationError):
                verify_apk.parse_signing_certificate(SIGNATURE_OUTPUT + extra)

    def test_rejects_malformed_certificate_hash(self):
        for digest in ("bad", CERTIFICATE[:-1], CERTIFICATE + "0", "g" * 64):
            with self.subTest(digest=digest), self.assertRaises(verify_apk.VerificationError):
                verify_apk.parse_signing_certificate(SIGNATURE_OUTPUT.replace(CERTIFICATE, digest))


class MetadataTests(unittest.TestCase):
    def test_parses_package_metadata(self):
        self.assertEqual(verify_apk.parse_apk_metadata(BADGING_OUTPUT), METADATA)

    def test_detects_debuggable_apk(self):
        self.assertTrue(verify_apk.parse_apk_metadata(BADGING_OUTPUT + "application-debuggable\n").debuggable)

    def test_rejects_missing_duplicate_or_broken_metadata(self):
        outputs = (
            "sdkVersion:'28'\n",
            BADGING_OUTPUT + BADGING_OUTPUT,
            BADGING_OUTPUT.replace(" versionName='0.2.0'", ""),
            BADGING_OUTPUT.replace("versionCode='2'", "versionCode='2.0'"),
            BADGING_OUTPUT.replace("versionCode='2'", "versionCode='2' versionCode='3'"),
            BADGING_OUTPUT.replace("versionName='0.2.0'", "versionName='0.2.0"),
        )
        for output in outputs:
            with self.subTest(output=output), self.assertRaises(verify_apk.VerificationError):
                verify_apk.parse_apk_metadata(output)

    def test_every_identity_mismatch_blocks_publication(self):
        candidates = (
            (replace(METADATA, application_id="com.vita.healthtracker"), CERTIFICATE),
            (replace(METADATA, version_code=1), CERTIFICATE),
            (replace(METADATA, version_name="0.2.0-debug"), CERTIFICATE),
            (replace(METADATA, debuggable=True), CERTIFICATE),
            (METADATA, "0" * 64),
        )
        for metadata, certificate in candidates:
            with self.subTest(metadata=metadata, certificate=certificate), self.assertRaises(verify_apk.VerificationError):
                verify_apk.validate_identity(IDENTITY, metadata, certificate)


class ReleaseAssetsTests(unittest.TestCase):
    def test_prepares_manifest_and_checksums_only_after_both_tools_validate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, output = root / "candidate.apk", root / "release"
            apk.write_bytes(b"test candidate bytes; tool responses are mocked")
            tools = verify_apk.AndroidTools(Path("apksigner"), Path("aapt"))
            with patch.object(verify_apk, "run_tool", side_effect=[SIGNATURE_OUTPUT, BADGING_OUTPUT]) as run_tool:
                manifest = verify_apk.prepare_release(apk, output, IDENTITY, tools)
            self.assertEqual(run_tool.call_args_list[0].args[0], ["apksigner", "verify", "--print-certs", str(apk)])
            self.assertEqual(run_tool.call_args_list[1].args[0], ["aapt", "dump", "badging", str(apk)])
            self.assertEqual(set(path.name for path in output.iterdir()), {"Vita-0.2.0.apk", "release-manifest.json", "SHA256SUMS"})
            saved_manifest = json.loads((output / "release-manifest.json").read_text())
            self.assertEqual(saved_manifest, manifest)
            self.assertEqual(saved_manifest, {
                "versionName": "0.2.0", "versionCode": 2, "applicationId": IDENTITY.application_id,
                "signingCertificateSha256": CERTIFICATE,
                "apk": {"name": "Vita-0.2.0.apk", "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()},
            })
            self.assertNotIn(str(root), (output / "release-manifest.json").read_text())
            for line in (output / "SHA256SUMS").read_text().splitlines():
                digest, filename = line.split("  ", 1)
                self.assertEqual(digest, hashlib.sha256((output / filename).read_bytes()).hexdigest())

    def test_identity_failure_leaves_no_release_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            apk, output = Path(directory) / "candidate.apk", Path(directory) / "release"
            apk.write_bytes(b"candidate")
            tools = verify_apk.AndroidTools(Path("apksigner"), Path("aapt"))
            with patch.object(verify_apk, "run_tool", side_effect=[SIGNATURE_OUTPUT, BADGING_OUTPUT + "application-debuggable\n"]):
                with self.assertRaises(verify_apk.VerificationError):
                    verify_apk.prepare_release(apk, output, IDENTITY, tools)
            self.assertFalse(output.exists())

    def test_tool_failure_does_not_trust_printed_certificate(self):
        completed = subprocess.CompletedProcess(["apksigner"], 1, SIGNATURE_OUTPUT, "DOES NOT VERIFY")
        with patch.object(verify_apk.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(verify_apk.VerificationError, "failed"):
                verify_apk.run_tool(["apksigner", "verify", "candidate.apk"])

    def test_apk_change_during_verification_blocks_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            apk, output = Path(directory) / "candidate.apk", Path(directory) / "release"
            apk.write_bytes(b"original candidate")
            tools = verify_apk.AndroidTools(Path("apksigner"), Path("aapt"))

            def tool_response(command):
                if command[0] == "aapt":
                    apk.write_bytes(b"different candidate")
                    return BADGING_OUTPUT
                return SIGNATURE_OUTPUT

            with patch.object(verify_apk, "run_tool", side_effect=tool_response), self.assertRaisesRegex(verify_apk.VerificationError, "changed"):
                verify_apk.prepare_release(apk, output, IDENTITY, tools)
            self.assertEqual(list(output.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
