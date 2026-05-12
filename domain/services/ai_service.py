import json
import time
import urllib.request
import urllib.error
from typing import Dict, Any, List


class AIService:
    def call_model(
        self,
        model_config: Dict[str, Any],
        messages: List[Dict[str, str]],
        temperature: float,
        max_tokens: int,
    ) -> Dict[str, Any]:
        start = time.time()
        model_type = model_config.get("model_type", "OPENAI").upper()
        api_key = model_config.get("api_key", "")
        model = model_config.get("model", "gpt-4o")
        endpoint = model_config.get("api_endpoint", "")

        if model_type == "CLAUDE":
            url = endpoint or "https://api.anthropic.com/v1/messages"
            data = {"model": model or "claude-3-5-sonnet-20241022", "max_tokens": max_tokens, "messages": messages}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"x-api-key": api_key, "Content-Type": "application/json",
                         "anthropic-version": "2023-06-01",
                         "anthropic-dangerous-direct-browser-access": "true"},
            )
        elif model_type == "ZHIPU":
            url = endpoint or "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            data = {"model": model or "glm-4", "messages": messages, "temperature": temperature, "max_tokens": max_tokens}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )
        elif model_type == "TONGYI":
            url = endpoint or "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            data = {"model": model or "qwen-turbo", "input": {"messages": messages},
                    "parameters": {"temperature": temperature, "max_tokens": max_tokens}}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )
        else:
            url = endpoint or "https://api.openai.com/v1/chat/completions"
            data = {"model": model, "messages": messages, "temperature": temperature, "max_tokens": max_tokens}
            req = urllib.request.Request(
                url, data=json.dumps(data).encode(),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            )

        resp = urllib.request.urlopen(req, timeout=60)
        result = json.loads(resp.read())

        if model_type == "CLAUDE":
            content = result.get("content", [{}])
            reply = content[0].get("text", "") if content else ""
            tokens = result.get("usage", {}).get("input_tokens", 0) + result.get("usage", {}).get("output_tokens", 0)
        else:
            choices = result.get("choices", [])
            reply = choices[0]["message"]["content"] if choices else ""
            tokens = result.get("usage", {}).get("total_tokens", 0)

        elapsed = int((time.time() - start) * 1000)
        return {"reply": reply, "tokens_used": tokens, "response_time_ms": elapsed, "model_used": model}
