# Decisions

Cites the [slice plan](plan.md), the [owner workspace rules](../../wiki/owner-client.md),
and the [pinned v1 client source capture](../../../raw/v1/client-source-capture.md).

## D01 — A retry is a navigation action, not a voice-start action

The retry control exists for page-load and permission-error recovery. It must
reload the configured workspace without asking the operating system for
microphone access. The trusted page requests that access later when the owner
starts spoken interaction.

## D02 — The trusted page request remains the permission boundary

The shell mediates page-originated microphone requests and denies camera or
mixed media. It does not grant media merely because OS permission is already
available. Origin validation remains a separate navigation control.
