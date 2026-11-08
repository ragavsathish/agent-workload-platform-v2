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

| [Deployment composition](deployment-compose.yml) | `products/voice-agent/deploy/compose.yml` | `37fe6c51c3af6e73bba6890b48de73c9f0445cfe58a23d86f5fbf7a1f953c822` |
| [Owner routes](traefik-routes.yml) | `products/voice-agent/deploy/traefik/routes.yml` | `2be2a1e95bfcfaf69e95c27a6d8798b39fe6ccfa8ef99a2beb939a927f9375d0` |
| [Tailnet login setup](tailnet-login.py) | `products/voice-agent/tools/ops/login.py` | `4ec8b693172896b614c76b4898d1166095abf165d340bbe2097cdad2cc63ccde` |
| [Tailnet policy example](tailnet-policy.hujson.example) | `products/voice-agent/deploy/tailscale-policy.hujson.example` | `d4ed0ec94457c98585f32a02652f4775495b4debee825e3aa0adc17da8045fb8` |
| [Task owner service](service-task-owner.plist) | `products/voice-agent/deploy/launchd/com.sathish.pipecat-pi-durable-owner.plist` | `7a7f4f9bc74b14d621142279b58893bf2d6ada33a38ac5aaed20287fc8b0f6d5` |
| [Task worker service](service-task-worker.plist) | `products/voice-agent/deploy/launchd/com.sathish.pipecat-worker.plist` | `71c1a4244dfb3ab400792f03a9c2e042055ec91e2decfb61c3ac7b0b13ba2587` |
| [Conversation service](service-pi-voice.plist) | `products/voice-agent/deploy/launchd/com.sathish.pipecat-pi-voice.plist` | `6e0bcd8863ddac490328736d079a73e6e76ef334ecb3ba493bf397242d88ec9f` |
| [Voice service lifecycle](voice-makefile) | `products/voice-agent/Makefile` | `0fb81fa45518b15cf037509c1b954499804f844eda90acc50c43417fc81d9977` |

| [Personal knowledge: c3-personal-knowledge.feature](c3-personal-knowledge.feature) | `docs/spec/contexts/c3-personal-knowledge/spec.feature` | `940ada77790c91bb2551011210e1d72725c16d299f3d938caa09d37e869ee84a` |
| [Personal knowledge: expanded-storming.md](expanded-storming.md) | `docs/event-model/expanded-storming.md` | `2e55f6fd27365ecd5c56595b2b0d9124d22d48499fd0c90cd5a2d0f2f08ecd87` |
| [Personal knowledge: phase-3-policies.md](phase-3-policies.md) | `docs/event-model/phase-3-policies.md` | `dbc8006f57e42c5962bc28875b855e494b7ccb7063ee0e64e21d5b74cce70f17` |
| [Personal knowledge: session-listener.ts](session-listener.ts) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/session-listener.ts` | `6688ec592a15662554d84176cdd35a77f686c013efbac33a88db2653d36cbcef` |
| [Personal knowledge: gateway-main.py](gateway-main.py) | `products/voice-agent/apps/gateway/src/voice_gateway/main.py` | `e1938eb224cd9d37a69f107ea82d950f6accfd6e3249a7d860f711e8f510eb93` |
| [Personal knowledge: personal-knowledge-regression.py](personal-knowledge-regression.py) | `products/voice-agent/tests/e2e/personal-knowledge-regression.py` | `683423253d62f059def6751a8e28ed6bf9a30c8e0c476f1df9d4bf4318e3ee78` |

The [tasking wiki](../../docs/wiki/tasking.md) and
[conversation wiki](../../docs/wiki/session-orchestration.md) cite these sources. The
[Löyly method snapshot](../loyly-method.md) is copied from the v1 checkout's
untracked `loyly/docs/wow/way-of-working.md` on 2026-11-07 and is methodological
input, not a pinned v1 product contract.

## Slice 08 evidence

| [Knowledge migration: knowledge-processor.mjs](knowledge-processor.mjs) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/image/processor.mjs` | `96d1e2f37a885593a4c8323df7a6527f28ca196040e2ec402968471cb34414ae` |
| [Knowledge migration: knowledge-task-runner.ts](knowledge-task-runner.ts) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/task-runner.ts` | `6700f546d1fa0a8666ba962a949fc052e1b481cafc5b2fe7e6a37d61fb51abb6` |
| [Knowledge migration: journal-prompt.md](journal-prompt.md) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/image/prompts/cognitive-journal/SYSTEM.md` | `ce57cb1c33808c3327df8da41b02705c02e1a49506f957f53d2d6bcc7aac73a7` |
| [Knowledge migration: candidate-prompt.md](candidate-prompt.md) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/image/prompts/candidate-extraction/SYSTEM.md` | `8903f7c2a191c9cd68a5a21f741506328d30ec8e62938ff59e969665bd62d2bf` |
| [Knowledge migration: daily-prompt.md](daily-prompt.md) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/image/prompts/daily-journal/SYSTEM.md` | `67f38f476dd431ff23364d3abcf5e0dc42fb1a3c30c1c5745bea6e12632a99c6` |
| [Knowledge migration: knowledge-ontology.ttl](knowledge-ontology.ttl) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/rdf/ontology/personal-knowledge.ttl` | `86e4af6b2ee321d40ae299f6592e6a7f5c51ed60aea3b09afc4aeec15e5347b6` |
| [Knowledge migration: knowledge-shapes.ttl](knowledge-shapes.ttl) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/rdf/shapes/personal-knowledge.shacl.ttl` | `682f11c98a0b23a60bf97bb23035e832e1d39a2532061ce9c485abeba86b91a0` |
| [Knowledge migration: knowledge-projection-migration.ru](knowledge-projection-migration.ru) | `products/voice-agent/apps/pi-agent/digital-twin/runtime/rdf/migrations/001_typed_journal_projections.ru` | `a84238ed1663b7ce2dfb621812663b28f4d6a704426a67f27317f89a21e1af10` |
| [Knowledge migration: journal-extraction-eval.mjs](journal-extraction-eval.mjs) | `products/voice-agent/apps/pi-agent/digital-twin/eval/journal-extraction.eval.mjs` | `70c8377ea551213b8406d2dfe4b918d2edfe67245836c549f36e62544d7f454a` |
| [Knowledge migration: knowledge-gateway-migration-test.py](knowledge-gateway-migration-test.py) | `products/voice-agent/apps/gateway/tests/test_digital_twin_migration.py` | `189f131a47e52711c5c78a3a5d1f92ed455826315a522ce66499dcce1e1649a5` |
| [Knowledge migration: graph operations](knowledge-graph-ops.py) | `products/voice-agent/tools/ops/digital_twin.py` | `7622ce2c205c932ef267e2c11580a6c15af9fabeb32adc015d4a227f1c6766ea` |

## Slice 09 evidence

All snapshots below are byte copies from the same pinned v1 commit named above.

| Snapshot | Original path | SHA-256 |
| --- | --- | --- |
| [Personal song: song-service.py](song-service.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/application/song_service.py` | `7498038a9f6a047c9b006e82560576f945fb7389c5a5ed99ef34cd74911e593e` |
| [Personal song: song-generation.py](song-generation.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/domain/song_generation.py` | `11d9bfa7df6bd20b524607f943ecda8993638f49a3cd7242da789ffb2d88dcc8` |
| [Personal song: song-lifecycle.py](song-lifecycle.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/domain/song_lifecycle.py` | `4ae345e8153e81b269021f7f732f0ea6b60177938fa40c1f583904391e685193` |
| [Personal song: song-worker.py](song-worker.py) | `products/voice-agent/apps/worker/src/voice_worker/main.py` | `5b84c112d615bcc489604534e35c67d548ca698540b5ef659d62df0ccb2e5f84` |
| [Personal song: song-workspace-effect.py](song-workspace-effect.py) | `products/voice-agent/apps/worker/src/voice_worker/workspace_effect.py` | `c475eba44d3e11b7a73dec7d3cc63429aaadb0b3b1f7af2b1b4be3e98d50edf1` |
| [Personal song: song-ace-step.py](song-ace-step.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/infrastructure/ace_step.py` | `3ea94870985179341bf390ad3c7cad755ba6e8e42ba56c88024c1bd285649ef5` |
| [Personal song: song-pr-workflow.md](song-pr-workflow.md) | `docs/wow/pull-request-soundtrack-workflow.md` | `704aa42d7a6c6a3a902f98f2c0baf3395c70fe87730dc895e84d56c7a5b1942f` |
| [Personal song: song-prompt.md](song-prompt.md) | `products/voice-agent/apps/pi-agent/voice-agent/prompts/music/APPEND_SYSTEM.md` | `02d973ccf9f20c62e190af35dd5422e4cb1b3e66ff1722bd5d65c87aef302953` |
| [Personal song: song-gateway.py](song-gateway.py) | `products/voice-agent/apps/gateway/src/voice_gateway/main.py` | `e1938eb224cd9d37a69f107ea82d950f6accfd6e3249a7d860f711e8f510eb93` |

## Slice 11 evidence

All snapshots below are byte copies from the same pinned v1 commit named above.

| Snapshot | Original path | SHA-256 |
| --- | --- | --- |
| [Incident monitor](stack-alert-monitor.py) | `products/voice-agent/tools/ops/stack_alert_monitor.py` | `90fc289408f6b5f2c0d212e56b44553a3437d404a5ae86eae0d186b2e204ef25` |
| [Incident monitor tests](stack-alert-monitor-test.py) | `products/voice-agent/tests/tools/test_stack_alert_monitor.py` | `030cd018315509e8de55b2175d555c62150e09d5152eaa9f7f34d2d80836fab1` |
| [Managed alert rules](stack-alerts.json) | `products/voice-agent/deploy/observability/stack-alerts.json` | `7b3da8c847184b80ddafe9f5a7b8322a188c137552b0d89e14e8bf171524a287` |
| [Monitor service](stack-alert-monitor-service.plist) | `products/voice-agent/deploy/launchd/com.sathish.pipecat-stack-alert-monitor.plist` | `2ae55f0ba2b4360440522ee0b118ed9c6cc6ed0b127deb9a0b3ca29f1ca93420` |
| [C4 incident contract](c4-operational-incidents.feature) | `docs/spec/contexts/c4-operational-incidents/spec.feature` | `72257eac54cf7ba55d56f6b678e2d43e250a035d2ee6612c199fdcafa687eaa8` |

## Slice 12 evidence

All snapshots below are byte copies from the pinned v1 commit named above. The already pinned C2 contract, task service, gateway, and owner client snapshots also inform this slice.

| Snapshot | Original path | SHA-256 |
| --- | --- | --- |
| [Task artifact store](task-artifacts.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/infrastructure/artifacts.py` | `652b1c0fd776d8d0ee9af23c4570f4a3549f36000821af4d2a67f061a4af2bff` |
| [Task workspace effect](task-workspace-effect.py) | `products/voice-agent/apps/worker/src/voice_worker/workspace_effect.py` | `c475eba44d3e11b7a73dec7d3cc63429aaadb0b3b1f7af2b1b4be3e98d50edf1` |
| [Artifact store tests](task-artifacts-test.py) | `products/voice-agent/packages/tasking/tests/infrastructure/test_artifacts.py` | `5e6959b03d3b947bc4b1ebf372027d92881501780049f2bca654dbf9178bc0f6` |
| [Task service tests](task-service-test.py) | `products/voice-agent/packages/tasking/tests/application/test_service.py` | `01bca528522ff69c74e4cac75efda114c51ad6c18433dbe7d3de33e144a66ad2` |
| [Owner route tests](task-gateway-test.py) | `products/voice-agent/apps/gateway/tests/test_gateway.py` | `1500d8e584945c82f4af27f05c6c62ae9c8094f5b8ba86da48ade6105d6ac24b` |

## Slice 13 task execution evidence

The following two files are byte copies from the same pinned v1 commit named above. They describe the conditional repository execution image handoff; an actual image build and registry are environment-dependent effects.

| Snapshot | Original path | SHA-256 |
| --- | --- | --- |
| [Task image ensure](task-image-ensure.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/infrastructure/ensure_execution_image.py` | `e57c51cf3f136c240c58d641990849fadd16ff6ad59d0d9f1f4e44cce1251bb2` |
| [Task execution images](task-execution-images.py) | `products/voice-agent/packages/tasking/src/voice_agent/tasking/domain/execution_images.py` | `9e9a36d0e3f3dc3a0d196385ae8b55fb7a5d9349394478ae499d8f177ec5753c` |
