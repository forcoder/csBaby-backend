"""Tests for AIService domain service."""
import json
import pytest
from unittest.mock import patch, MagicMock
from domain.services.ai_service import AIService


class TestAIServiceCallModel:
    def _make_ai_service(self):
        return AIService()

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_call_openai_model(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "Hello from GPT!"}}],
            "usage": {"total_tokens": 25}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "OPENAI", "model": "gpt-4o", "api_key": "sk-test"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["reply"] == "Hello from GPT!"
        assert result["tokens_used"] == 25
        assert result["model_used"] == "gpt-4o"
        assert "response_time_ms" in result

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_call_claude_model(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "content": [{"text": "Hello from Claude!"}],
            "usage": {"input_tokens": 10, "output_tokens": 20}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "CLAUDE", "model": "claude-3-5-sonnet", "api_key": "sk-ant-test"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.5,
            max_tokens=1000,
        )
        assert result["reply"] == "Hello from Claude!"
        assert result["tokens_used"] == 30

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_call_zhipu_model(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "Hello from GLM!"}}],
            "usage": {"total_tokens": 15}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "ZHIPU", "model": "glm-4", "api_key": "zhipu-key"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["reply"] == "Hello from GLM!"

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_call_tongyi_model(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "Hello from Qwen!"}}],
            "usage": {"total_tokens": 20}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "TONGYI", "model": "qwen-turbo", "api_key": "tongyi-key"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["reply"] == "Hello from Qwen!"

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_call_model_with_custom_endpoint(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "Custom endpoint!"}}],
            "usage": {"total_tokens": 10}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "OPENAI", "model": "gpt-4", "api_key": "k",
                          "api_endpoint": "https://custom.api.com/v1/chat/completions"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["reply"] == "Custom endpoint!"
        # Verify custom endpoint was used
        call_args = mock_urlopen.call_args
        request_obj = call_args[0][0]
        assert request_obj.full_url == "https://custom.api.com/v1/chat/completions"

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_openai_default_model(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "reply"}}],
            "usage": {"total_tokens": 5}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "OPENAI", "api_key": "k"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["reply"] == "reply"
        assert result["model_used"] == "gpt-4o"

    @patch("domain.services.ai_service.urllib.request.urlopen")
    def test_response_time_is_positive(self, mock_urlopen):
        mock_response = MagicMock()
        mock_response.read.return_value = json.dumps({
            "choices": [{"message": {"content": "fast"}}],
            "usage": {"total_tokens": 5}
        }).encode()
        mock_urlopen.return_value = mock_response

        service = self._make_ai_service()
        result = service.call_model(
            model_config={"model_type": "OPENAI", "model": "gpt-4o", "api_key": "k"},
            messages=[{"role": "user", "content": "Hi"}],
            temperature=0.7,
            max_tokens=2000,
        )
        assert result["response_time_ms"] >= 0
