import hashlib
import json

import pytest

import ai_auth


def test_password_is_hashed_and_session_token_is_not_persisted(tmp_path):
    path = tmp_path / "accounts.json"
    ai_auth.configure_storage(path)
    password = "UnaClaveSegura2026"
    ai_auth.create_or_update_account("nikutest", password)
    raw = path.read_text(encoding="utf-8")
    data = json.loads(raw)
    row = data["users"]["nikutest"]
    assert password not in raw
    assert row["iterations"] >= 600_000
    assert ai_auth.authenticate("nikutest", password)
    assert not ai_auth.authenticate("nikutest", "clave-incorrecta")

    device = "0123456789abcdef"
    assert ai_auth.bind_or_check_device("nikutest", device)
    assert ai_auth.bind_or_check_device("nikutest", device)
    assert not ai_auth.bind_or_check_device("nikutest", "fedcba9876543210")
    token, expires = ai_auth.issue_session("nikutest", device)
    assert expires > 0
    assert token not in path.read_text(encoding="utf-8")
    assert hashlib.sha256(token.encode()).hexdigest() in path.read_text(encoding="utf-8")
    assert ai_auth.resolve_session(token, device) == "nikutest"
    assert ai_auth.resolve_session(token, "fedcba9876543210") is None
    assert ai_auth.revoke_session(token)
    assert ai_auth.resolve_session(token, device) is None


def test_replacing_or_deactivating_account_revokes_existing_sessions(tmp_path):
    ai_auth.configure_storage(tmp_path / "accounts.json")
    ai_auth.create_or_update_account("nikutest", "UnaClaveSegura2026")
    device = "0123456789abcdef"
    assert ai_auth.bind_or_check_device("nikutest", device)
    token, _ = ai_auth.issue_session("nikutest", device)
    ai_auth.create_or_update_account("nikutest", "OtraClaveSegura2026")
    assert ai_auth.resolve_session(token, device) is None
    assert not ai_auth.authenticate("nikutest", "UnaClaveSegura2026")
    assert ai_auth.authenticate("nikutest", "OtraClaveSegura2026")
    token, _ = ai_auth.issue_session("nikutest", device)
    assert ai_auth.release_account_device("nikutest")
    assert ai_auth.resolve_session(token, device) is None
    assert ai_auth.bind_or_check_device("nikutest", "fedcba9876543210")
    token, _ = ai_auth.issue_session("nikutest", "fedcba9876543210")
    assert ai_auth.deactivate_account("nikutest")
    assert ai_auth.resolve_session(token, "fedcba9876543210") is None
    assert not ai_auth.authenticate("nikutest", "OtraClaveSegura2026")


def test_credentials_are_validated(tmp_path):
    ai_auth.configure_storage(tmp_path / "accounts.json")
    with pytest.raises(ValueError):
        ai_auth.create_or_update_account("bad user", "UnaClaveSegura2026")
    with pytest.raises(ValueError):
        ai_auth.create_or_update_account("usuario", "corta")
