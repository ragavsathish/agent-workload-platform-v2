# Slice 12 decisions

Cites [slice plan](plan.md), [task result rules](../../wiki/task-result-delivery.md), [source artifact store](../../../raw/v1/task-artifacts.py), [source service](../../../raw/v1/task-service.py), and [source gateway](../../../raw/v1/gateway-main.py).

## D46: completion and retrieval are separate facts

The completed task retains its outcome if a later artifact read fails. A failed capture before completion fails the execution rather than advertising an object that does not exist. The pruned branch is treating a download failure as a task retry or retroactive execution failure.

## D47: artifact authority is the owned task result

Only a declared kind for the owned task and its selected workspace may be read. A caller cannot supply an arbitrary storage address or read another task's internal execution record. The pruned branch is a general object proxy keyed by user input.

## D48: public answer is a bounded projection

Present response text and safe summaries, artifact kinds, and terminal state. Hide raw execution events, private storage addresses, and internal capability fields. The pruned branch is returning the worker result without a presentation boundary.

## D49: source handoff is a prerequisite, not a new task outcome

A repository task uses a selected, approved source snapshot, and its patch is associated with that task. The source handoff may be qualified with local fixtures while external publication remains environment dependent. The pruned branch is claiming full image or repository publishing parity from a successful artifact download.
