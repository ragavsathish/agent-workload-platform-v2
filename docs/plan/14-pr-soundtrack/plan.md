# Slice 14: manual pull request soundtrack qualification

Cites [pull request soundtrack rules](../../wiki/pr-soundtrack.md), [personal song rules](../../wiki/personal-song.md), [pinned source guide](../../../raw/v1/song-pr-workflow.md), [source decision wall](../../../raw/v1/expanded-storming.md), and [decisions](decisions.md).

## Goal and boundary

Make the current manual AWP pull request soundtrack path explicit and verifiable. The [scenarios](spec.feature) describe observable review and publication evidence, including held outcomes. They do not introduce a new product command or imply that a song changes repository review authority.

The slice begins with a reviewed AWP change and an engineering pull request. It ends with a verified reviewer-accessible soundtrack and complete lyrics, or a named missing or failed stage. The [personal song slice](../09-personal-song/plan.md) owns lyric and private audio behavior. The maintainer performs repository publication and pull request update. D8 owner review may lead to another human-selected action; no shared nomination selector is in this boundary.

## Tickets

- [Ticket 01](ticket-01-source-and-review.md): pin the reviewed change, preserve the engineering summary, ground the brief, and inspect exact owner-approved lyrics.
- [Ticket 02](ticket-02-audio-and-publication.md): listen to and inspect the complete audio, publish a reviewed compact asset, and verify stable links and full lyrics for a reviewer.
- [Ticket 03](ticket-03-qualification.md): record held outcomes and run a real pull request qualification without claiming automatic enforcement.

## Exit evidence

The current executable song stages are already implemented in slice 09. There is no source-backed, deployed pull request soundtrack handler to recreate. This planning cut therefore requires no new product implementation. A real qualification requires an authorized AWP pull request, its reviewed revision, owner lyric decision, rendered audio, an accessible Git repository, and an intended reviewer account. Record the inspected diff and revision, lyric version, audio checks, committed asset digest, final pull request body, and reviewer link result. Until that journey runs, mark external publication unqualified. Automatic diff extraction, pull request writes, and release policy need a separate proposed-behavior decision before implementation.
