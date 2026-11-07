# Invariants

Cites [the way of working](way-of-working.md).

1. Every behavior traces from a slice to a wiki rule to a pinned raw source or
   a recorded new decision. The documentation gate checks these references.
2. Wiki and plan files contain no implementation framework, language, storage,
   or deployment choices. `harness/check-docs.sh` enforces a vocabulary check.
3. Every acceptance test names a scenario from `spec.feature`. Tests are run
   red before the implementation, and `harness/verify.sh` ratifies green.
4. A request identity binds to at most one accepted task. The acceptance suite
   tests retries and simultaneous submissions through the public module API.
5. Owner isolation is checked at admission and query. The acceptance suite
   tests both directions with separate owners.
