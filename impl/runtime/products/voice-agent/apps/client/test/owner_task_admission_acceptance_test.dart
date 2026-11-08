import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:voice_agent_client/api_client.dart';
import 'package:voice_agent_client/views/pi_workspace_view.dart';

void main() {
  testWidgets(
    '@task-admission-007 @task-admission-008 owner retries uncertain admission and can deliberately create another task',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 820);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final taskRequests = <http.Request>[];
      final acceptedByKey = <String, String>{};
      final tasks = <Map<String, Object?>>[];
      var acceptedCount = 0;
      final api = VoiceApi(
        client: MockClient((request) async {
          if (request.method == 'POST' && request.url.path == '/api/tasks') {
            taskRequests.add(request);
            final key = request.headers['idempotency-key'];
            final taskId = acceptedByKey.putIfAbsent(key ?? '', () {
              acceptedCount += 1;
              final id = 'task-$acceptedCount';
              tasks.add({
                'id': id,
                'instruction': 'Inspect the release notes',
                'status': 'pending',
                'created_at': '2026-01-01T00:00:00Z',
              });
              return id;
            });
            if (taskRequests.length == 1) {
              throw http.ClientException('accepted response was lost');
            }
            return http.Response(
              jsonEncode({
                'id': taskId,
                'instruction': 'Inspect the release notes',
                'status': 'pending',
                'created_at': '2026-01-01T00:00:00Z',
              }),
              201,
            );
          }
          if (request.url.path == '/api/tasks' && request.method == 'GET') {
            return http.Response(jsonEncode(tasks), 200);
          }
          if (request.url.path == '/api/pi-transcript') {
            return http.Response(
              jsonEncode({'revision': 0, 'entries': []}),
              200,
            );
          }
          if (request.url.path == '/api/pi-sessions') {
            return http.Response(jsonEncode({'sessions': []}), 200);
          }
          if (request.url.path == '/api/pi-context') {
            return http.Response(jsonEncode({'canCompact': false}), 200);
          }
          if (request.url.path == '/api/pi-task-routing') {
            return http.Response(jsonEncode({'taskId': null}), 200);
          }
          return http.Response('{}', 200);
        }),
      );
      addTearDown(api.close);

      Future<void> submitSameInstruction() async {
        await tester.tap(find.byKey(const Key('new-task')));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const Key('new-task-instruction')),
          'Inspect the release notes',
        );
        await tester.tap(find.byKey(const Key('submit-new-task')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 20));
      }

      await tester.pumpWidget(MaterialApp(home: PiWorkspaceView(api: api)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 20));

      await submitSameInstruction();
      expect(find.textContaining('accepted response was lost'), findsOneWidget);
      await submitSameInstruction();

      expect(taskRequests, hasLength(2));
      expect(taskRequests[0].headers['idempotency-key'], isNotEmpty);
      expect(
        taskRequests[1].headers['idempotency-key'],
        taskRequests[0].headers['idempotency-key'],
      );
      expect(acceptedCount, 1);
      expect(tasks.single['id'], 'task-1');

      await submitSameInstruction();
      expect(taskRequests, hasLength(3));
      expect(taskRequests[2].headers['idempotency-key'], isNotEmpty);
      expect(
        taskRequests[2].headers['idempotency-key'],
        isNot(taskRequests[1].headers['idempotency-key']),
      );
      expect(acceptedCount, 2);
      expect(tasks.map((task) => task['id']).toSet(), {'task-1', 'task-2'});
    },
  );
}
