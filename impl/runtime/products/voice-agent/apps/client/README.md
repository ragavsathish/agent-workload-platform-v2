# Pi Voice owner client

This Flutter client provides the private owner workspace for the v2 session and
task APIs. The browser uses its current same-origin service. Native shells open
the same private browser workspace and restrict navigation to that origin.

Install the pinned SDK and dependencies, then run the local checks:

```sh
./tool/flutterw pub get
./tool/flutterw analyze
./tool/flutterw test
```

Build the browser workspace with the owner-private HTTPS origin used by native
shells:

```sh
./tool/flutterw build web --wasm --release \
  --dart-define=VOICE_WEB_URL=https://<private-tailnet-host>.ts.net/
```

The configured origin is required for native shells and artifact downloads. The
application stores no owner credential. The server checks the owner's access
for session, task, progress, and artifact reads. Artifact links are restricted
to the configured HTTPS tailnet origin.

Spoken interaction requests microphone permission only when the owner starts a
voice session. Camera access is not requested. Interactive response
presentations receive bounded content in a restricted frame and do not receive
workspace tools or credentials.
