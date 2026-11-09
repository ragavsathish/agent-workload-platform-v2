#!/usr/bin/env python3
"""Term only the exact orphaned Semaphore task process tree from task 2147483642."""
from __future__ import annotations

import argparse
import os
import signal
import subprocess
import time

TASK_ID = "2147483640"
REPO = "/Users/sathish.narayanan/Documents/git/agent-workload-platform-v2"
STATE = "/private/tmp/awp-v2-state"
ENV = "/private/tmp/awp-v2-deploy-config/service.env"


def process_table() -> dict[int, tuple[int, int, str]]:
    output = subprocess.check_output(["ps", "-axo", "pid=,ppid=,pgid=,command="], text=True)
    result = {}
    for line in output.splitlines():
        fields = line.strip().split(None, 3)
        if len(fields) == 4:
            try:
                pid, ppid, pgid = map(int, fields[:3])
            except ValueError:
                continue
            result[pid] = (ppid, pgid, fields[3])
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ansible-pid", type=int, required=True)
    parser.add_argument("--module-pid", type=int, required=True)
    parser.add_argument("--preflight-pid", type=int, required=True)
    args = parser.parse_args()
    table = process_table()
    ansible = table.get(args.ansible_pid)
    module = table.get(args.module_pid)
    preflight = table.get(args.preflight_pid)
    if not ansible or not module or not preflight:
        raise SystemExit("cleanup refused: one or more expected task processes are absent")
    appid, apgid, acmd = ansible
    mpid, mpgid, mcmd = module
    ppid, pgid, pcmd = preflight
    valid = (
        appid == 1 and apgid == args.ansible_pid
        and "/opt/homebrew/bin/ansible-playbook" in acmd
        and "/Library/Application Support/AWPv2/k3d/semaphore/tmp/project_2/inventory_1.yml" in acmd
        and f'"id":{TASK_ID}' in acmd
        and f'"awp_repo_root":"{REPO}"' in acmd
        and f'"awp_state_root":"{STATE}"' in acmd
        and acmd.endswith("preflight.yml")
        and mpid == args.ansible_pid and mpgid == args.ansible_pid
        and "AnsiballZ_command.py" in mcmd
        and ppid == args.module_pid and pgid == args.ansible_pid
        and "repository_1_template_2/preflight.py" in pcmd
        and f"--env {ENV}" in pcmd
        and f"--state {STATE}" in pcmd
        and f"--repo {REPO}" in pcmd
    )
    if not valid:
        raise SystemExit("cleanup refused: current full command lines or parent chain do not match task 2147483642")

    for pid in (args.preflight_pid, args.module_pid, args.ansible_pid):
        os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        current = process_table()
        if not any(pid in current for pid in (args.ansible_pid, args.module_pid, args.preflight_pid)):
            print("cleanup=complete signalled=3 verified_exited=true")
            return
        time.sleep(0.2)
    raise SystemExit("cleanup incomplete: one or more validated task processes did not exit after TERM")


if __name__ == "__main__":
    main()
