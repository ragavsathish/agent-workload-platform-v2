# Ticket 01: capture task work products

Cites [slice plan](plan.md), [rules](../../wiki/task-result-delivery.md), [workspace effect](../../../raw/v1/task-workspace-effect.py), and [artifact store](../../../raw/v1/task-artifacts.py).

Capture the selected repository's bounded patch, or the repository-free task's bounded workspace archive. Tie the captured object to task identity and workspace kind. Reject path escape, links, excessive files, excessive bytes, and incomplete capture before advertising success. Preserve one inspectable execution failure when capture cannot finish. The captured object must survive a fresh owner process. Cover scenarios `artifact-001` through `artifact-004` and `artifact-008`.
