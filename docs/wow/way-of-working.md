# Way of working

The specification is the durable asset. Implementation is a build product. This
repository follows the source-to-slice method in the [Löyly method](../../raw/loyly-method.md).

Read `raw/` as immutable evidence, distill domain rules into `docs/wiki/`, and
specify one vertical slice in `docs/plan/NN-name/` before building it. Each slice
has a plan, acceptance scenarios, tickets, and a decision log. Every internal
document cites its input. Domain names remain consistent across layers.

`docs/wiki/` and `docs/plan/` describe behavior without implementation choices.
Implementation choices and topology live under `impl/`. Acceptance tests derive
from the scenarios while the implementation is red. An independent gate checks
the result. A later generation can remove `impl/` and rebuild from the durable
documents through a clean-room fence.

The current planned slice is [owner typed-turn reload recovery](../plan/36-owner-turn-reload-recovery/plan.md).
