# Slice 13 decisions

Cites [the slice plan](plan.md), [task execution and owner delivery](../../wiki/task-execution-delivery.md), and [the previous result-delivery decisions](../12-task-result-delivery/decisions.md).

## D50: one selected source per task attempt

The worker and capture step use one task-specific prepared workspace tied to the selected source. The pruned branch is capturing a shared mutable workspace that may contain another task's changes.

## D51: completion authority remains with the task record

The worker reports effects and bounded progress; the task authority accepts one terminal transition only after capture. The pruned branch is treating a successful worker exit or an artifact file alone as public completion.

## D52: conditional image preparation precedes isolated execution

A declared execution image must be resolved from the selected source before the isolated attempt starts. The pruned branch is silently falling back to an unrelated default image after a declared image fails preparation.

## D53: qualification names its exercised boundary

A local process journey proves process wiring and owner delivery; an external source or image build is separately qualified with its actual dependency. The pruned branch is claiming deployed image handoff from a mocked prerequisite.
