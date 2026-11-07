# Ticket 01: public media turn

Cites the [slice plan](plan.md), [scenarios voice-001, voice-005, and voice-006](spec.feature), [deployed voice rules](../../wiki/deployed-voice-journey.md), and [source voice probe](../../../raw/v1/smallwebrtc_voice.py).

Exercise a timed audio utterance through the owner-facing live connection, recognition, conversation dispatch, response speech, and public transcript. Bind the accepted turn and selected destination to the observed reply. Keep owner and inner service authority separate. Make missing recognition or speech capability observable without reporting a successful spoken result. Record failing then passing acceptance runs for `voice-001`, `voice-005`, and `voice-006`.
