# Slice 14 decisions

Cites the [plan](plan.md), [domain rules](../../wiki/pr-soundtrack.md), [source guide](../../../raw/v1/song-pr-workflow.md), and [source decision wall](../../../raw/v1/expanded-storming.md).

## D54: keep publication with the maintainer

The source process uses a maintainer to review the diff, commit the compact audio, update documentation, and edit the pull request. No deployed software handler performs those steps. The pruned branch is inventing automatic pull request writes as v1 parity.

## D55: distinguish private completion from public publication

The song service may report complete with private playback. Publication is verified only from a reviewed repository asset, full lyrics, and a reviewer-accessible pull request link. The pruned branch is counting a private owner playback path as a published soundtrack.

## D56: hold uncertainty instead of deriving a release decision

A failed render, changed lyrics, unverified link, or missing publication remains a named held stage. The source decision wall leaves soundtrack exceptions and the combined release gate open. The pruned branch is treating absence of evidence as an approved exception or automatically blocking every pull request.

## D57: no new runtime for a manual qualification slice

The v2 personal song flow already implements the source-backed generation stages with a stronger recorded approval decision. This slice documents and qualifies external maintainer behavior. A new process authority would have no deployed counterpart to recreate and needs its own future decision.
