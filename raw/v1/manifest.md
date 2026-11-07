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
| [Session orchestration: voice-agent.ts](voice-agent.ts) | `products/voice-agent/apps/pi-agent/voice-agent/voice-agent.ts` | `cad585e3628ff7d27b9ed6e8965791df188d92f00b8d4291ce23c2d1acd0a810` |
| [Voice service entry point](voice-agent-service.ts) | `products/voice-agent/apps/pi-agent/voice-agent-service.ts` | `22ba8cb97b149bdd923fc596edb87479be65ee770d3d2cc8e0683b8bcdcacea8` |
| [Session orchestration: voice-sessions.ts](voice-sessions.ts) | `products/voice-agent/apps/pi-agent/voice-agent/voice-sessions.ts` | `d69129356c95d78326faf3bbf735c4890eb9f674f39afd49c57f5c4391969353` |
| [Session orchestration: voice-http-server.ts](voice-http-server.ts) | `products/voice-agent/apps/pi-agent/voice-agent/voice-http-server.ts` | `4c51de48181155cd4252aadb599ae4d09750d9ce4928c80a3daaa6c4901d3e18` |
| [Session orchestration: public-transcript.ts](public-transcript.ts) | `products/voice-agent/apps/pi-agent/voice-agent/public-transcript.ts` | `49e0f173763c8058900e122a6b319ba052a8b3d94207ee61561211e91670e319` |
| [Session orchestration: conversation.ts](conversation.ts) | `products/voice-agent/apps/pi-agent/voice-agent/conversation.ts` | `931c527f5cf160cd2bf35aa967ca8277cddd4297e9534a050d895a5170b9c413` |
| [Session orchestration: serial-executor.ts](serial-executor.ts) | `products/voice-agent/apps/pi-agent/voice-agent/serial-executor.ts` | `8be42c22b5980b7569e0dbd21f3deebb20feade358496500c445d40e68638656` |
| [Session orchestration: remote-task-session.ts](remote-task-session.ts) | `products/voice-agent/apps/pi-agent/voice-agent/remote-task-session.ts` | `2a2dc390afc40fc78ad0549fa944337d8f16c0c7b9ccbfa081688e9b107059a9` |
| [Session orchestration: pi-session.ts](pi-session.ts) | `products/voice-agent/apps/pi-agent/voice-agent/pi-session.ts` | `32908b5511b877aee494b0716b9ac5a459bfb488697b43439f549cbc4a713d76` |
| [Session orchestration: voice-service.test.mjs](voice-service.test.mjs) | `products/voice-agent/apps/pi-agent/voice-agent-service.test.mjs` | `daec7b8eeca120a67e7b1cf1e71b6ff5cca5fd7ee69d51f9f5acea132361c913` |
| [Session orchestration: edge-bot.py](edge-bot.py) | `products/voice-agent/apps/edge/bot.py` | `151c65103d50e96c8541d01498205a2078b26cbda3a5620624ea7e39ea0c25d1` |
| [Session orchestration: broker-http.py](broker-http.py) | `products/voice-agent/apps/broker/src/voice_agent/speech/presentation/http.py` | `23eed33cac3fd2c8a5bf2aea5ad51028aa936dd623655c8fb2fee1a03b37bad4` |
| [Session orchestration: client-api.dart](client-api.dart) | `products/voice-agent/apps/client/lib/api_client.dart` | `cc7520894c7a2446de3f0c41336d2737163c3aba37266072fc48c6aa84798d87` |
| [Session orchestration: client-workspace.dart](client-workspace.dart) | `products/voice-agent/apps/client/lib/views/pi_workspace_view.dart` | `84f9b11bd1e369da7fa9ae51515a22afa7142dd5463f1e0a827be1fc50d10ecb` |
| [Session orchestration: voice-product-readme.md](voice-product-readme.md) | `products/voice-agent/README.md` | `c92b3cb928062485686f35abb4ad684d3288a7823341ada27506e0a50f4b6891` |
| [Session orchestration: process-model.md](process-model.md) | `docs/event-model/process-model.md` | `52e5905d578798aab770b96224d9269242e17c8b9cab4601be9ee278e44565c6` |
| [Session orchestration: owner-workday-walk.md](owner-workday-walk.md) | `docs/event-model/owner-workday-walk.md` | `22f5c3631be65d88eff7790a1bc20b4543c05bd1c83ea0274065f2f292b1f7a3` |
| [SmallWebRTC voice probe](smallwebrtc_voice.py) | `products/voice-agent/tests/e2e/smallwebrtc_voice.py` | `4057cc375a39f68a26f3153fc2b440b222e19092aec4a2c9dbb25bf6b8f83a95` |
| [Voice testing guide](voice-testing.md) | `products/voice-agent/TESTING.md` | `90996ecf1679457a6ac5bed5e9cd02a7dec67efa2db4569e59c9338d1e94dd8d` |
| [Host requirements](host-requirements.md) | `products/voice-agent/HOST_REQUIREMENTS.md` | `0a67993a85e3f7b559f71b5aba57a501d8dfce3f86ddcd5d16529fc15c19635a` |
| [Voice environment example](voice-env.example) | `products/voice-agent/deploy/.env.example` | `8d8b3f4c02c959ad7441274f63e6c886529f8c2d4128692947bf60c217670908` |

The [tasking wiki](../../docs/wiki/tasking.md) and
[conversation wiki](../../docs/wiki/session-orchestration.md) cite these sources. The
[Löyly method snapshot](../loyly-method.md) is copied from the v1 checkout's
untracked `loyly/docs/wow/way-of-working.md` on 2026-11-07 and is methodological
input, not a pinned v1 product contract.
