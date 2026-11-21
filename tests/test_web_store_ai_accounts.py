import http.client
import json
import threading
from http.server import ThreadingHTTPServer

import ai_auth
import web_store


def _server():
    server = ThreadingHTTPServer(("127.0.0.1", 0), web_store.StoreHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread


def _post(server, route, body, token=None, device_id="0123456789abcdef"):
    connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=10)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
        headers["X-Ai-Device-Id"] = device_id
    connection.request("POST", route, body=json.dumps(body), headers=headers)
    response = connection.getresponse()
    payload = json.loads(response.read().decode("utf-8"))
    status = response.status
    connection.close()
    return status, payload


def test_standalone_login_chat_and_logout_do_not_use_premium(monkeypatch, tmp_path):
    ai_auth.configure_storage(tmp_path / "accounts.json")
    ai_auth.create_or_update_account("independiente", "UnaClaveSegura2026")
    monkeypatch.setenv("IASINCENSURA", "sk-notrack-test-secret-not-for-production")
    monkeypatch.setattr(web_store, "_AI_ACCOUNT_REQUESTS", {})

    class Response:
        def __enter__(self): return self
        def __exit__(self, *_args): return False
        def read(self, _limit):
            return json.dumps({"choices": [{"message": {"content": "Hola desde AiTod4S3"}}]}).encode()

    monkeypatch.setattr(web_store.urllib.request, "urlopen", lambda *_a, **_k: Response())
    server, thread = _server()
    try:
        status, payload = _post(server, "/api/ai/login", {"username": "independiente", "password": "UnaClaveSegura2026", "device_id": "0123456789abcdef"})
        assert status == 200 and payload["ok"]
        token = payload["token"]
        status, response = _post(server, "/api/ai/login", {"username": "independiente", "password": "UnaClaveSegura2026", "device_id": "fedcba9876543210"})
        assert status == 409 and response["error"] == "device_in_use"
        status, response = _post(server, "/api/ai/chat", {"messages": [{"role": "user", "content": "Hola"}]}, token)
        assert status == 200 and response["reply"] == "Hola desde AiTod4S3"
        status, response = _post(server, "/api/ai/chat", {"messages": [{"role": "user", "content": "Hola desde otro teléfono"}]}, token, device_id="fedcba9876543210")
        assert status == 401 and response["error"] == "unauthorized"
        status, response = _post(server, "/api/ai/logout", {}, token)
        assert status == 200 and response["revoked"]
        status, response = _post(server, "/api/ai/chat", {"messages": [{"role": "user", "content": "Hola"}]}, token)
        assert status == 401 and response["error"] == "unauthorized"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def test_standalone_login_rejects_bad_credentials(monkeypatch, tmp_path):
    ai_auth.configure_storage(tmp_path / "accounts.json")
    ai_auth.create_or_update_account("independiente", "UnaClaveSegura2026")
    monkeypatch.setattr(web_store, "_AI_LOGIN_ATTEMPTS", {})
    server, thread = _server()
    try:
        status, payload = _post(server, "/api/ai/login", {"username": "independiente", "password": "incorrecta123", "device_id": "0123456789abcdef"})
        assert status == 401
        assert payload == {"ok": False, "error": "invalid_credentials"}
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
