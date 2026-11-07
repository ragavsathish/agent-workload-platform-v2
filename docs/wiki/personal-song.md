# Personal song

Cites the [source song service](../../raw/v1/song-service.py), [manifest rules](../../raw/v1/song-generation.py), [lifecycle rules](../../raw/v1/song-lifecycle.py), [owner review guide](../../raw/v1/song-pr-workflow.md), and [source event walk](../../raw/v1/expanded-storming.md).

## Current behavior and boundary

An owner supplies a brief, musical style, target duration, and eligible workspace. A song generation starts lyric work and returns an identity. The lyric result is a complete, bounded manifest: title, style, lyric text with section markers, duration, tempo, key, and meter. A completed lyric task without a valid manifest is a failure, not a reviewable draft. The owner can inspect the generation and its manifest while audio is absent.

Rendering is a separate command and a separate task. It is eligible only after lyric work completes with a valid manifest. The public statuses are `writing_lyrics`, `lyrics_ready`, `generating_music`, `complete`, `failed`, and `cancelled`. A complete generation has an audio artifact; a completed render task without an audio artifact is failed. The owner can play a complete artifact through an owner-checked path. Lyric and render failures have bounded explanations. Cancellation ends the visible generation and prevents a later render.

The current owner workflow requires showing the full manifest and receiving explicit approval in a later turn. The source service checks lyric readiness but does not store or verify that approval. Thus the current review gate depends on the caller honoring the workflow. The [slice 09 decision](../plan/09-personal-song/decisions.md) makes approval durable and enforceable for the recreated product.

## Agreed song rules

- A generation belongs to one owner. An owner may query, approve, render, cancel, or play only their own generation. Foreign and unknown identities have the same public absence response.
- A stable creation request identity binds to one owner and one normalized brief, style, duration, and workspace. Replaying the same request returns the same generation and lyric task. Reusing it for different content is a conflict. A generation has one stable lyric task identity and at most one stable render task identity.
- The lyric manifest is immutable once offered for review. It contains complete lyric lines and the musical fields needed by the renderer. Its digest identifies the exact reviewed version. A changed lyric or style creates a new generation and requires new approval.
- Approval is an explicit owner decision for a generation and manifest digest, recorded with a stable decision identity. A duplicate decision is harmless; a conflicting decision identity is refused. Rendering requires an approved digest matching the manifest submitted to the renderer. A render request never infers approval from a status query, task completion, or the caller's claim.
- A rejected manifest has no render task. The owner may cancel it or start a new generation. A failed lyric task cannot be approved or rendered. A failed render does not restart through command replay; a new generation and approval are required for another attempt.
- Submission uncertainty is reconciled by stable task identities. Repeated create or render commands cannot start duplicate work. A cancellation races safely with task completion: cancellation remains the visible outcome, and late audio is not exposed as playable.
- The public generation view includes its status, bounded manifest when valid, task references, approval state, artifact reference or playback path only when complete, and bounded failure. It does not reveal workspace internals or renderer paths.

## Adjacent workflow

The [source PR guide](../../raw/v1/song-pr-workflow.md) describes a manual soundtrack for a reviewed code change: ground the brief in the diff, approve full lyrics, listen to the result, then publish a reviewer-accessible copy with the engineering summary. Automatic diff extraction, publishing, and a cross-path PR gate are proposed work, not current song behavior or this slice's acceptance boundary.
