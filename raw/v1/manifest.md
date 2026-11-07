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
| [C8 execution contract](c8-agent-execution.feature) | `docs/spec/contexts/c8-agent-execution/spec.feature` | `7b161e68a53741f7660dd85bfb2430c13872f80a0070f31b66b538afcca516d2` |
| [Durable task journey](voice-to-durable.feature) | `docs/plan/01-voice-to-durable/spec.feature` | `e3ff10a16e3a993039cb378c66447fb2ce57eb0b5ad18acc3c2fa658796f8af5` |
| [Durable owner tests](durable-owner.test.ts) | `platform/agent-execution/pi-durable-task/owner.test.ts` | `84ff73948479efbb81ce1bbf0fe408abfb8b829adc85a0873c991201a922a660` |
| [Session execution](session-execution.ts) | `platform/agent-execution/pi-durable-task/session-execution.ts` | `1be23779277132ee44cbe29ff1d78c841c26af46fe934cdeec0b43eb317bcfb7` |
| [Progress narration](task-progress.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/domain/task_progress.py` | `f3f5146cb67f5dfede911ef857ef074b7d629e30985a09ba8936d4798fbd316a` |

The [tasking wiki](../../docs/wiki/tasking.md) cites these sources. The
[Löyly method snapshot](../loyly-method.md) is copied from the v1 checkout's
untracked `loyly/docs/wow/way-of-working.md` on 2026-11-07 and is methodological
input, not a pinned v1 product contract.
