# Ticket 03: owner delivery and independent gate

Cites [slice plan](plan.md), [rules](../../wiki/task-result-delivery.md), [owner gateway](../../../raw/v1/gateway-main.py), [owner route tests](../../../raw/v1/task-gateway-test.py), and [owner client](../../../raw/v1/client-workspace.dart).

Expose the artifact through the authenticated owner route with the correct filename, content type, private no-store response, and no content sniffing. Show the download only when the expected kind is declared. Verify a fresh owner process can present the same completed answer and download. Run an independent journey through the public task path, including a lost-object read and cross-owner attempt. Cover scenarios `artifact-005` through `artifact-009`.
