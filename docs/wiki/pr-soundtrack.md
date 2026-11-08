# Pull request soundtrack

Cites the [pinned manual guide](../../raw/v1/song-pr-workflow.md), [decision wall](../../raw/v1/expanded-storming.md), [process model](../../raw/v1/process-model.md), [root README](../../raw/v1/pr-soundtrack-root-readme.md), [product README](../../raw/v1/voice-product-readme.md), and [personal song rules](personal-song.md).

## Current behavior

For an AWP self-evolution pull request, a maintainer reviews the change and prepares an engineering summary first. The reviewed diff grounds a lyric brief. The owner reviews the complete lyric manifest and explicitly approves it before requesting a render. The maintainer checks the delivered audio by listening and inspecting its format, duration, and accessibility. A reviewed compact copy can be committed in the repository, linked from the appropriate documentation, and linked from the pull request with complete lyrics. The technical summary stays readable without playing audio. Other changes may omit a soundtrack.

The song generation, review, render, failure, cancellation, and private playback stages are covered by [personal song](personal-song.md). A private playback path is not a durable reviewer-accessible pull request asset. The two pinned repository audio examples and relative README links show manual publication in the source tree. They do not prove that any specific pull request was updated or that a reviewer opened its link.

If lyrics change, the owner reviews the new full manifest. A style alternate is a separate generation whose lyric lines are compared with the approved version. A failed render does not become a playable soundtrack. The maintainer repairs the music service or starts a fresh generation and review. Private payloads and credentials do not enter the lyric brief, public asset, or pull request.

## Authority and limits

The maintainer owns the reviewed diff, repository asset, and pull request body. The owner owns lyric approval. The song service owns generation and private artifact status. Repository review remains a separate decision. A finished private song alone does not establish pull request publication or approval.

The v1 source has owner-facing song routes and a written manual procedure. Its decision wall explicitly says automatic diff extraction, pull request editing, soundtrack-completeness derivation, and a cross-path release gate are proposed. The rule for whether a missing soundtrack blocks an AWP review or permits an exception remains open. D8 owner review is current as a human decision; a shared next-action coordinator is proposed. No deployed pull request soundtrack command or event store has been found in the pinned source.

## Qualification boundary

For a real pull request, inspect the selected diff and engineering summary, compare the submitted brief with permitted facts, record the exact lyric version the owner approved, verify the generated audio, commit only the reviewed compact asset, and inspect the resulting pull request body and links as a reviewer. A missing or failed stage is reported as held or unverified in the qualification record; it does not produce an automated release decision.
