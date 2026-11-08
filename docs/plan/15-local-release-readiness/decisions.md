# Slice 15 decisions

Cites the [plan](plan.md), [local release rules](../../wiki/local-release-readiness.md),
[source decision wall](../../../raw/v1/expanded-storming.md), and
[public deployment decisions](../06-public-deployment/decisions.md).

## D58: qualify one selected local release with explicit state identity

The report binds evidence to a version and private state identity. The pruned
branch is inferring release readiness from unrelated previous runs or from a
status response alone.

## D59: require owner-visible effects for each claimed journey

Each current path is exercised through its actual service boundary and owner
read. A journey that cannot run is marked unavailable or unqualified. The
pruned branch is promoting a fixture, isolated component test, or synthetic
source into whole-product parity.

## D60: keep restart and recovery conservative

A settled turn and claimed worker attempt retain their identities across
restart. Uncertain work needs owner retry before another effect. The pruned
branch is replaying an attempt just because a process restarted.

## D61: report maturity at the same scope as evidence

Local supervision, distinct-device access, external source and image handoff,
private knowledge cutover, model quality, and manual pull request publication
have separate results. The pruned branch is treating a local pass as proof of
all deployment and workflow boundaries.

## D62: correct public status from observed implementation

The status document names the built slices and points to their actual reports,
while naming open gates. The pruned branch is leaving outdated “planned”
claims in place after implementation or replacing them with an unsupported
“complete rewrite” claim.
