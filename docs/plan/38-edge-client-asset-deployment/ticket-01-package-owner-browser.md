# Ticket 01: package and verify the browser owner workspace

Cites [slice 38](plan.md), the [Edge page route](../../../impl/runtime/products/voice-agent/apps/edge/bot.py), and the [candidate preflight](../../../impl/deployment/public_candidate.py).

Build the Flutter web client before building the isolated Edge image. Copy the generated web output into `client-dist`, the directory served by the Edge routes. Make preflight fail with a bounded prerequisite reason when either the generated `index.html` or the image copy rule is absent. Add named checks for the image rule, preflight refusal, and local page response. Keep the public identity and separate-device gates unchanged.

Acceptance: `candidate-012`, `candidate-013`, and `candidate-014`.
