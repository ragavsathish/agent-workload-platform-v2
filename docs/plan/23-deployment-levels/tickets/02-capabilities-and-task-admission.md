# Capabilities and task admission

Map each level to its available capabilities and task executor. Keep persistent
voice isolation independent from the task executor. At `voice`, refuse durable
task submission before acceptance; at `durable`, use the host executor; at
`full`, use the isolated executor. A transition that interrupts an accepted
task preserves its identity and requires explicit recovery.

Done when the current task lifecycle and voice privacy boundaries remain
enforced across every level.
