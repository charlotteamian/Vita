#!/usr/bin/env python3
import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))


def load_server(**env):
    keys = {
        "VITA_AI_ALLOW_LAN",
        "VITA_AI_TOKEN",
        "VITA_AI_PORT",
        "VITA_AI_BACKEND",
        "VITA_AI_MODEL",
        "VITA_CODEX_MODEL",
        "VITA_CLAUDE_BIN",
        "VITA_CODEX_BIN",
    }
    patched = {key: env[key] for key in keys if key in env}
    with patch.dict(os.environ, patched, clear=False):
        for key in keys - patched.keys():
            os.environ.pop(key, None)
        sys.modules.pop("vita_ai_server", None)
        return importlib.import_module("vita_ai_server")


class VitaAiServerSecurityTest(unittest.TestCase):
    def test_default_bind_host_is_loopback(self):
        server = load_server()

        self.assertEqual("127.0.0.1", server.bind_host())

    def test_lan_mode_requires_token(self):
        server = load_server(VITA_AI_ALLOW_LAN="1")

        with self.assertRaisesRegex(RuntimeError, "VITA_AI_TOKEN"):
            server.validate_security_config()

    def test_bearer_token_is_required_when_configured(self):
        server = load_server(VITA_AI_ALLOW_LAN="1", VITA_AI_TOKEN="a-very-long-random-token")

        self.assertFalse(server.is_authorized({"Authorization": "Bearer wrong"}))
        self.assertTrue(server.is_authorized({"Authorization": "Bearer a-very-long-random-token"}))

    def test_server_source_does_not_start_mdns_broadcast(self):
        source = Path(__file__).with_name("vita_ai_server.py").read_text(encoding="utf-8")

        self.assertNotIn("dns-sd", source)
        self.assertNotIn("register_bonjour", source)

    def test_prompt_describes_long_term_context(self):
        server = load_server()

        self.assertIn("longTerm", server.FIELD_GUIDE)
        self.assertIn("多年", server.PROMPT_TEMPLATE)
        self.assertIn("longTermFindings", server.PROMPT_TEMPLATE)
        self.assertIn("recentPatterns", server.PROMPT_TEMPLATE)

    def test_today_prompt_focuses_on_sleep_and_short_term(self):
        server = load_server()

        # 今日模板必须能无 KeyError 地格式化 (双花括号转义正确)
        rendered = server.TODAY_PROMPT_TEMPLATE.format(
            field_guide=server.FIELD_GUIDE, data_json="{}"
        )
        self.assertIn("昨晚睡眠", rendered)
        self.assertIn("近 3 天", rendered)
        self.assertIn('longTermFindings 必须返回空数组', rendered)

    def test_run_analysis_routes_today_focus_to_today_template(self):
        server = load_server()
        captured = {}

        def fake_backends():
            return [("claude", "/bin/claude")]

        def fake_run_claude(binary, prompt):
            captured["prompt"] = prompt
            return '{"overall": "ok"}', "test-model"

        with patch.object(server, "backend_candidates", fake_backends), patch.object(
            server, "run_claude", fake_run_claude
        ):
            server.run_analysis({"focus": "today", "daily": []})
            self.assertIn("今日状态", captured["prompt"])
            server.run_analysis({"daily": []})
            self.assertIn("多年层", captured["prompt"])


class VitaAndroidClientSecurityTest(unittest.TestCase):
    def test_client_does_not_use_nsd_auto_discovery(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / "app/src/main/java/com/vita/healthtracker/data/ai/AiInsightProvider.kt").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("NsdManager", source)
        self.assertNotIn("discoverViaNsd", source)

    def test_client_sends_bearer_token(self):
        root = Path(__file__).resolve().parents[1]
        source = (root / "app/src/main/java/com/vita/healthtracker/data/ai/AiInsightProvider.kt").read_text(
            encoding="utf-8"
        )

        self.assertIn('header("Authorization"', source)
        self.assertIn("Bearer", source)


if __name__ == "__main__":
    unittest.main()
