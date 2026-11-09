# Tickets — Slice 49

- [x] Inspect live Semaphore project, repository, branch/revision, runner, and templates read-only; confirmed project 2, repository 1, `pin-37c6415b01bc177c4a91a92ebb6f1198608edc36`, runner `macos-host`, templates 1–12.
- [x] Locate broker auth and query semantics; confirmed latest-typed-turn is typed-only and the deployed route returns 404, so it cannot prove native voice quiescence.
- [x] Implement a closed-target, redacted read-only broker status helper and Semaphore playbook; restart/rollback code is intentionally withheld because native voice quiescence cannot be proven.
- [x] Add the playbook/template source for future Semaphore registration on the reviewed `macos-host` runner. Live project registration remains pending because setup_project.py would re-pin unrelated project resources.
- [x] Python syntax and Ansible `--syntax-check` passed; read-only helper run passed local and Edge authenticated health; latest-turn endpoint returned 404, so restart was withheld and host state left unchanged.
- [x] Recheck all 11 k3d pods/readiness, Prometheus and PromQL, stopped v1 Compose/retained volumes, ClusterIP Services, and localhost-only Semaphore ingress.
- [x] Owner-authenticated live trace/voice qualification remains gated on a valid authorized owner identity; none was supplied or fabricated.
