# Slice 09 decisions

Cites the [plan](plan.md), [personal song rules](../../wiki/personal-song.md), [source song service](../../../raw/v1/song-service.py), [source lifecycle](../../../raw/v1/song-lifecycle.py), and [owner review guide](../../../raw/v1/song-pr-workflow.md).

## D35: owner approval becomes a recorded decision

The source service accepts render when valid lyrics are ready. Its caller is expected to obtain later-turn approval, but the service cannot prove it. The recreated product records approval or rejection against the exact immutable manifest digest and requires recorded approval before render. The pruned branch is retaining only an informal caller promise.

## D36: one generation is one review and render attempt

Creation has a stable request identity. Its lyric and render task references derive from the generation identity. Render submission is idempotent and has one durable task. After rejection, changed lyrics, or render failure, another attempt uses a new generation and new review. The pruned branch is silently restarting a failed render through replay of its command.

## D37: private song and PR soundtrack are separate boundaries

The personal song ends with owner-checked audio. The manual PR workflow may later copy a reviewed result into a reviewer-accessible asset. Automatic diff extraction, publication, and release gating need their own slice and decisions. The pruned branch is treating a private playback reference as a public PR artifact.
