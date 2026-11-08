"""Owner boundary checks for the browser task-routing command."""

from __future__ import annotations

import json
import os
import unittest
from unittest.mock import AsyncMock, patch

import httpx
from fastapi import HTTPException

import bot


class TaskRoutingBoundaryTest(unittest.IsolatedAsyncioTestCase):
    """@routing-003 Refuse foreign, unknown, and terminal targets safely."""

    async def test_foreign_and_unknown_tasks_are_indistinguishable(self) -> None:
        details = []
        for status, detail in ((403, "foreign task"), (404, "missing task")):
            with patch.object(
                bot,
                "owner_gateway_request",
                AsyncMock(side_effect=HTTPException(status_code=status, detail=detail)),
            ):
                with self.assertRaises(HTTPException) as raised:
                    await bot.require_attachable_owner_task(object(), "task-id")
                details.append((raised.exception.status_code, raised.exception.detail))
        self.assertEqual(details, [(404, "task is not available")] * 2)

    async def test_terminal_task_is_refused_before_pi_command(self) -> None:
        with patch.object(
            bot,
            "owner_gateway_request",
            AsyncMock(return_value={"id": "owned-task", "status": "completed"}),
        ) as lookup:
            with self.assertRaises(HTTPException) as raised:
                await bot.require_attachable_owner_task(object(), "owned-task")
        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.detail, "task is not eligible for attachment")
        lookup.assert_awaited_once()

    async def test_active_owned_task_is_eligible(self) -> None:
        expected = {"id": "owned-task", "status": "running"}
        with patch.object(bot, "owner_gateway_request", AsyncMock(return_value=expected)):
            self.assertEqual(
                await bot.require_attachable_owner_task(object(), "owned-task"), expected
            )


class TypedTurnIdentityTest(unittest.IsolatedAsyncioTestCase):
    """@owner-turn-001 The owner command identity reaches the broker unchanged."""

    async def test_typed_turn_forwards_stable_identity_to_broker(self) -> None:
        forwarded: list[httpx.Request] = []

        def respond(request: httpx.Request) -> httpx.Response:
            forwarded.append(request)
            return httpx.Response(
                200,
                json={"choices": [{"message": {"content": json.dumps({
                    "spoken": "answer",
                    "presentation": {"kind": "text"},
                })}}]},
            )

        client = httpx.AsyncClient(transport=httpx.MockTransport(respond))
        with patch.dict(os.environ, {
            "INFERENCE_BROKER_TOKEN": "test-token",
            "INFERENCE_BROKER_BASE_URL": "https://broker.test",
            "VOICE_DEFAULT_WORKSPACE": "/tmp/workspace",
        }), patch.object(bot.httpx, "AsyncClient", return_value=client):
            await bot.broker_pi_message("hello", "stable-owner-turn-id")
        await client.aclose()

        self.assertEqual(len(forwarded), 1)
        self.assertEqual(forwarded[0].headers["x-awp-turn-id"], "stable-owner-turn-id")
        self.assertEqual(forwarded[0].headers["x-awp-origin"], "typed")


class TypedTurnRecoveryTest(unittest.IsolatedAsyncioTestCase):
    """@owner-turn-recovery-003 Keep the public receipt minimal."""

    async def test_latest_receipt_is_bounded_and_drops_unrecognized_fields(self) -> None:
        def response(_request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={
                "turnId": "receipt-1", "status": "uncertain", "prompt": "private",
                "fingerprint": "private",
            })

        client = httpx.AsyncClient(transport=httpx.MockTransport(response))
        with patch.dict(os.environ, {
            "INFERENCE_BROKER_TOKEN": "test-token",
            "INFERENCE_BROKER_BASE_URL": "https://broker.test/v1",
        }), patch.object(bot.httpx, "AsyncClient", return_value=client):
            receipt = await bot.broker_latest_typed_turn_request()
        await client.aclose()
        self.assertEqual(receipt, {"turnId": "receipt-1", "status": "uncertain"})


if __name__ == "__main__":
    unittest.main()
