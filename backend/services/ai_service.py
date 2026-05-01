import json
import time
import urllib.request
import urllib.error
from config import OPENAI_API_KEY, CLAUDE_API_KEY, ZHIPU_API_KEY, TONGYI_API_KEY


class AIService:
    """大模型调用服务，支持多模型切换和降级"""

    def __init__(self):
        self._cache = {}  # 简单的响应缓存
        self._cache_ttl = 3600  # 1小时

    def generate_reply(self, model_config, messages, temperature=0.7, max_tokens=2000):
        """
        生成 AI 回复。
        model_config: dict with keys: model_type, model, api_key, api_endpoint
        messages: list of {"role": "user"/"assistant"/"system", "content": "..."}
        Returns: {"reply": str, "model_used": str, "tokens_used": int, "response_time_ms": int}
        """
        cache_key = json.dumps({"m": model_config["model"], "msg": messages, "t": temperature})
        cached = self._cache.get(cache_key)
        if cached and time.time() - cached["ts"] < self._cache_ttl:
            return cached["data"]

        start = time.time()
        model_type = model_config.get("model_type", "OPENAI").upper()
        api_key = model_config.get("api_key", "")
        model = model_config.get("model", "gpt-4o")
        endpoint = model_config.get("api_endpoint", "")

        if model_type == "OPENAI":
            result = self._call_openai(api_key, model, messages, temperature, max_tokens, endpoint)
        elif model_type == "CLAUDE":
            result = self._call_claude(api_key, model, messages, temperature, max_tokens, endpoint)
        elif model_type == "ZHIPU":
            result = self._call_zhipu(api_key, model, messages, temperature, max_tokens, endpoint)
        elif model_type == "TONGYI":
            result = self._call_tongyi(api_key, model, messages, temperature, max_tokens, endpoint)
        elif model_type == "CUSTOM":
            result = self._call_openai_compatible(api_key, model, messages, temperature, max_tokens, endpoint)
        else:
            result = self._call_openai(api_key, model, messages, temperature, max_tokens, endpoint)

        elapsed = int((time.time() - start) * 1000)
        result["response_time_ms"] = elapsed
        result["model_used"] = model

        # 缓存结果
        self._cache[cache_key] = {"ts": time.time(), "data": result}
        # 清理过期缓存
        if len(self._cache) > 500:
            now = time.time()
            self._cache = {k: v for k, v in self._cache.items() if now - v["ts"] < self._cache_ttl}

        return result

    def test_connection(self, model_config):
        """测试模型连接"""
        try:
            result = self.generate_reply(
                model_config,
                [{"role": "user", "content": "Hello"}],
                temperature=0.1,
                max_tokens=10
            )
            return {"success": True, "model": model_config.get("model", ""), "tokens": result.get("tokens_used", 0)}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def _call_openai(self, api_key, model, messages, temperature, max_tokens, endpoint=None):
        url = endpoint or "https://api.openai.com/v1/chat/completions"
        return self._openai_compatible_call(url, api_key, model, messages, temperature, max_tokens)

    def _call_claude(self, api_key, model, messages, temperature, max_tokens, endpoint=None):
        url = endpoint or "https://api.anthropic.com/v1/messages"
        data = {
            "model": model or "claude-3-5-sonnet-20241022",
            "max_tokens": max_tokens,
            "messages": messages
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode(),
            headers={
                "x-api-key": api_key,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01",
                "anthropic-dangerous-direct-browser-access": "true"
            }
        )
        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())
        content = result.get("content", [{}])
        reply = content[0].get("text", "") if content else ""
        tokens = result.get("usage", {}).get("input_tokens", 0) + result.get("usage", {}).get("output_tokens", 0)
        return {"reply": reply, "tokens_used": tokens}

    def _call_zhipu(self, api_key, model, messages, temperature, max_tokens, endpoint=None):
        url = endpoint or "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        return self._openai_compatible_call(url, api_key, model or "glm-4", messages, temperature, max_tokens)

    def _call_tongyi(self, api_key, model, messages, temperature, max_tokens, endpoint=None):
        url = endpoint or "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        data = {
            "model": model or "qwen-turbo",
            "input": {"messages": messages},
            "parameters": {"temperature": temperature, "max_tokens": max_tokens}
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode(),
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        )
        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())
        reply = result.get("output", {}).get("text", "")
        tokens = result.get("usage", {}).get("total_tokens", 0)
        return {"reply": reply, "tokens_used": tokens}

    def _call_openai_compatible(self, api_key, model, messages, temperature, max_tokens, endpoint):
        if not endpoint:
            raise ValueError("Custom model requires api_endpoint")
        return self._openai_compatible_call(endpoint, api_key, model, messages, temperature, max_tokens)

    def _openai_compatible_call(self, url, api_key, model, messages, temperature, max_tokens):
        data = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode(),
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        )
        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())
        choices = result.get("choices", [])
        reply = choices[0]["message"]["content"] if choices else ""
        tokens = result.get("usage", {}).get("total_tokens", 0)
        return {"reply": reply, "tokens_used": tokens}


# 单例
ai_service = AIService()
