#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check_connection(base_url, model, api_key, timeout=30):
    url = urllib.parse.urlsplit(base_url)
    local = url.hostname in {"localhost", "127.0.0.1", "::1"}
    if (
        url.scheme not in {"https", "http"}
        or (url.scheme == "http" and not local)
        or not url.hostname
        or url.username
        or url.password
        or url.query
        or url.fragment
    ):
        raise ValueError("模型地址应使用 HTTPS；本地测试服务可使用 HTTP。")
    if not api_key or not api_key.strip():
        raise ValueError("请设置 TAIDE_LLM_API_KEY 环境变量。")
    if not model or not model.strip():
        raise ValueError("请设置模型名称。")
    if timeout <= 0 or timeout > 120:
        raise ValueError("超时应介于 0 到 120 秒之间。")
    payload = json.dumps(
        {
            "model": model,
            "messages": [{"role": "user", "content": "Reply exactly with TAIDE_OK."}],
            "temperature": 0,
            "max_tokens": 32,
            "stream": False,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "TAIDE-connectivity-check/0.1",
        },
        method="POST",
    )
    # 禁止跨地址重定向，密钥仅发送到用户配置的模型端点。
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(request, timeout=timeout) as response:
        data = response.read(1_048_577)
    if len(data) > 1_048_576:
        raise ValueError("模型响应超过大小上限。")
    try:
        content = json.loads(data)["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as error:
        raise ValueError("模型返回了无效的 Chat Completions 响应。") from error
    if not isinstance(content, str) or not content.strip():
        raise ValueError("模型返回了空白内容。")
    return {"status": "ok", "model": model}


def main():
    parser = argparse.ArgumentParser(description="检查 DeepSeek 或同济 AGI 兼容模型端点的连通性。")
    parser.add_argument("--base-url", default=os.environ.get("TAIDE_LLM_BASE_URL", "https://api.deepseek.com/v1"))
    parser.add_argument("--model", default=os.environ.get("TAIDE_LLM_MODEL", "deepseek-chat"))
    parser.add_argument("--timeout", type=float, default=30)
    args = parser.parse_args()
    try:
        result = check_connection(args.base_url, args.model, os.environ.get("TAIDE_LLM_API_KEY"), args.timeout)
    except urllib.error.HTTPError as error:
        print(f"模型连通性检查失败，HTTP 状态码：{error.code}。", file=sys.stderr)
        return 1
    except (urllib.error.URLError, TimeoutError):
        print("模型连接失败或超时，请检查网络和端点配置。", file=sys.stderr)
        return 1
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
