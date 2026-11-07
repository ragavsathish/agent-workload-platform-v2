# Deployed owner voice journey

Cites the [live conversation rules](live-session-integration.md), [pinned voice probe](../../raw/v1/smallwebrtc_voice.py), [voice testing guide](../../raw/v1/voice-testing.md), [host requirements](../../raw/v1/host-requirements.md), and [voice environment example](../../raw/v1/voice-env.example).

## Spoken turn

An authenticated owner opens a live voice connection. A final spoken utterance becomes one actionable turn in the currently selected conversation. The owner receives audible reply media and a safe public transcript for that same turn. A welcome or background utterance does not count as the owner's actionable turn. The [source voice probe](../../raw/v1/smallwebrtc_voice.py) sends timed audio through a live peer, while the [testing guide](../../raw/v1/voice-testing.md) distinguishes that journey from direct text injection.

The spoken turn retains one identity and destination from acceptance through response. The current selection and any temporary task attachment determine the destination before dispatch. A reconnect or retry cannot turn an uncertain effect into a second prompt. The transcript after a fresh service start identifies the same selected conversation and settled turn. See the [live conversation rules](live-session-integration.md) and [source orchestrator](../../raw/v1/voice-agent.ts).

## Availability and boundaries

The owner can use voice only when the live connection, speech recognition, conversation, and speech output capabilities are available. Missing capabilities yield an observable failure; silence or a partial reply is not success. One owner voice connection is active at a time, and disconnection releases temporary task routing without erasing the selected conversation. See the [source edge](../../raw/v1/edge-bot.py) and [host requirements](../../raw/v1/host-requirements.md).

Public owner authority is resolved before live commands and reads. A separate inner service credential cannot itself impersonate an owner. The owner-facing route exposes safe text and speech, while host tools, credentials, and private payloads stay behind its service boundaries. See the [live conversation rules](live-session-integration.md) and [voice testing guide](../../raw/v1/voice-testing.md).

## Evidence of completion

A journey report records the public entry, one final recognized utterance, the accepted turn identity, chosen destination, audible response, safe transcript, and selected history after restart. It records which provider capabilities ran and whether a durable task or tool environment actually executed. A protocol driver may replace the microphone and browser peer while preserving live media, recognition, routing, and response speech. Provider substitution must be named in the report. See the [voice testing guide](../../raw/v1/voice-testing.md) and [source probe](../../raw/v1/smallwebrtc_voice.py).
