# Ticket 01: permission timing

Cites the [slice plan](plan.md), its [acceptance scenarios](spec.feature), the
[owner workspace rules](../../wiki/owner-client.md), and the [pinned v1 client
source capture](../../../raw/v1/client-source-capture.md).

Remove eager microphone permission requests from generic workspace retry. Keep
OS permission mediation attached only to an allowed page microphone request.
Cover the request gate and retry behavior, preserving the configured private
HTTPS origin and rejecting camera or mixed media.
