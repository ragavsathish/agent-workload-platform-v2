# Pinned AWP v1 evidence

These files are byte copies from AWP v1 commit
`d8afdfc31ea3ac7d74eef625c8863f5954b75fcd` unless noted. They are
evidence, not an instruction to preserve implementation choices. The current
v1 checkout also has uncommitted work, which is excluded from this baseline.

| Snapshot | Original path | SHA-256 |
| --- | --- | --- |
| [C2 contract](c2-tasking.feature) | `docs/spec/contexts/c2-tasking/spec.feature` | `35c8bf5484ed611d15c0ba91a9179cd11e649b95d43671d4be249cacd903eaa3` |
| [Owner client contract](owner-client.feature) | `docs/spec/contexts/c2-tasking/owner-client.feature` | `a22e7da478127a8d3e2b9c91bc7b75dc1fbb184afa648cc82ac4ad77c19ce6e0` |
| [Glossary](glossary.md) | `products/voice-agent/GLOSSARY.md` | `9d023beb48c31d597fc2a9474c390572d2d2dab9eb87c74dd43d80f3d252acaf` |
| [Task service](task-service.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/application/service.py` | `a7b89c4d1e9ac0f0877de0592f1227e6d37c30936fe258b80ad7cd38129ade95` |
| [Durable owner](durable-owner.ts) | `platform/agent-execution/pi-durable-task/owner.ts` | `8366deb10d49c0984ac9ff0da100b233252ca6adba1f3778434fd533273633fd` |

The [tasking wiki](../../docs/wiki/tasking.md) cites these sources. The
[Löyly method snapshot](../loyly-method.md) is copied from the v1 checkout's
untracked `loyly/docs/wow/way-of-working.md` on 2026-11-07 and is methodological
input, not a pinned v1 product contract.
