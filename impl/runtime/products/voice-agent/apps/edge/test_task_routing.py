"""Owner boundary checks for the browser task-routing command."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

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


if __name__ == "__main__":
    unittest.main()
