import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:voice_agent_client/api_client.dart';
import 'package:voice_agent_client/views/pi_workspace_view.dart';

void main() {
  testWidgets('@owner-client-006 @routing-001 @routing-002 @routing-004 attaches and detaches through owner commands', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    const taskId = '4b860912-01a3-455b-9fa4-7a71c93500ee';
    String? attachedTaskId;
    var attachFailuresRemaining = 1;
    final routingRequests = <http.Request>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        if (request.url.path == '/api/pi-transcript') {
          return http.Response(jsonEncode({'revision': 0, 'entries': []}), 200);
        }
        if (request.url.path == '/api/pi-sessions') {
          return http.Response(jsonEncode({'sessions': []}), 200);
        }
        if (request.url.path == '/api/pi-context') {
          return http.Response(jsonEncode({'canCompact': false}), 200);
        }
        if (request.url.path == '/api/tasks' && request.method == 'GET') {
          return http.Response(jsonEncode([
            {
              'id': taskId,
              'instruction': 'Inspect the release notes',
              'workspace': 'scratch',
              'plan_hash': 'test-plan',
              'status': 'running',
              'created_at': '2026-01-01T00:00:00Z',
            },
          ]), 200);
        }
        if (request.url.path == '/api/pi-task-routing' &&
            request.method == 'GET') {
          return http.Response(jsonEncode({'taskId': attachedTaskId}), 200);
        }
        if (request.url.path.startsWith('/api/pi-task-routing/')) {
          routingRequests.add(request);
          if (request.url.path.endsWith('/attach')) {
            if (attachFailuresRemaining > 0) {
              attachFailuresRemaining -= 1;
              return http.Response(
                jsonEncode({'detail': 'Pi task service unavailable'}),
                502,
              );
            }
            attachedTaskId = taskId;
            return http.Response(jsonEncode({'taskId': taskId}), 200);
          }
          attachedTaskId = null;
          return http.Response(jsonEncode({'status': 'detached'}), 200);
        }
        return http.Response('{}', 404);
      }),
    );
    addTearDown(api.close);

    await tester.pumpWidget(MaterialApp(home: PiWorkspaceView(api: api)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    await tester.tap(find.byKey(const Key('workspace-section-toggle')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    expect(find.byKey(Key('task-routing-$taskId')), findsOneWidget);
    expect(find.text('Attach to conversation'), findsOneWidget);

    await tester.tap(find.byKey(Key('task-routing-$taskId')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 30));
    expect(attachedTaskId, isNull);
    expect(find.byKey(Key('task-routing-$taskId')), findsOneWidget);

    await tester.tap(find.byKey(Key('task-routing-$taskId')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 30));
    expect(attachedTaskId, taskId);
    expect(find.text('Detach from conversation'), findsOneWidget);

    await tester.tap(find.byKey(Key('task-routing-$taskId')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 30));
    expect(attachedTaskId, isNull);
    expect(find.text('Attach to conversation'), findsOneWidget);
    expect(routingRequests.map((request) => request.url.path), [
      '/api/pi-task-routing/attach',
      '/api/pi-task-routing/attach',
      '/api/pi-task-routing/detach',
    ]);
    expect(
      routingRequests[0].headers['idempotency-key'],
      routingRequests[1].headers['idempotency-key'],
    );
    expect(
      routingRequests.every(
        (request) => request.headers['idempotency-key']?.isNotEmpty == true,
      ),
      isTrue,
    );
  });

  testWidgets('@routing-003 exposes an explicit reset for uncertain routing', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var uncertain = true;
    final routingCommands = <String>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        if (request.url.path == '/api/pi-transcript') {
          return http.Response(jsonEncode({'revision': 0, 'entries': []}), 200);
        }
        if (request.url.path == '/api/pi-sessions') {
          return http.Response(jsonEncode({'sessions': []}), 200);
        }
        if (request.url.path == '/api/pi-context') {
          return http.Response(jsonEncode({'canCompact': false}), 200);
        }
        if (request.url.path == '/api/tasks') return http.Response('[]', 200);
        if (request.url.path == '/api/pi-task-routing' && request.method == 'GET') {
          return uncertain
              ? http.Response(
                  jsonEncode({'detail': 'task routing is uncertain'}),
                  409,
                )
              : http.Response(jsonEncode({'taskId': null}), 200);
        }
        if (request.url.path == '/api/pi-task-routing/detach') {
          routingCommands.add(request.headers['idempotency-key']!);
          uncertain = false;
          return http.Response(jsonEncode({'status': 'detached'}), 200);
        }
        return http.Response('{}', 404);
      }),
    );
    addTearDown(api.close);

    await tester.pumpWidget(MaterialApp(home: PiWorkspaceView(api: api)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    await tester.tap(find.byKey(const Key('workspace-section-toggle')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    expect(find.byKey(const Key('reset-task-routing')), findsOneWidget);

    await tester.tap(find.byKey(const Key('reset-task-routing')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 30));
    expect(uncertain, isFalse);
    expect(find.byKey(const Key('reset-task-routing')), findsNothing);
    expect(routingCommands, hasLength(1));
  });
}
