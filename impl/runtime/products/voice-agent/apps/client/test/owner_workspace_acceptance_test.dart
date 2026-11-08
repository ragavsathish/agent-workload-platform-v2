import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:voice_agent_client/api_client.dart';
import 'package:voice_agent_client/views/pi_workspace_view.dart';

void main() {
  testWidgets(
    '@owner-client-002 history, resume, and fresh-session controls preserve identity',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(390, 844);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final paths = <String>[];
      final requests = <http.Request>[];
      String snapshot(String text) => jsonEncode({
        'revision': 1,
        'entries': [
          {
            'id': 'entry',
            'timestamp': 1,
            'role': 'assistant',
            'status': 'completed',
            'text': jsonEncode({
              'spoken': text,
              'presentation': {'kind': 'flow'},
            }),
          },
        ],
      });
      final api = VoiceApi(
        client: MockClient((request) async {
          requests.add(request);
          paths.add('${request.method} ${request.url.path}');
          if (request.method == 'POST') return http.Response('{}', 200);
          switch (request.url.path) {
            case '/api/pi-transcript':
              return http.Response(snapshot('Live answer'), 200);
            case '/api/pi-sessions':
              return http.Response(
                jsonEncode({
                  'sessions': [
                    {
                      'id': 'saved-1',
                      'title': 'Saved conversation',
                      'updatedAt': '2026-01-01T00:00:00Z',
                      'messageCount': 2,
                      'active': false,
                    },
                  ],
                }),
                200,
              );
            case '/api/pi-sessions/saved-1':
              return http.Response(snapshot('Saved answer'), 200);
            case '/api/tasks':
              return http.Response('[]', 200);
            case '/api/pi-context':
              return http.Response(
                jsonEncode({
                  'canCompact': false,
                  'compactionCommandId': 'persisted-compaction-29',
                  'compactionStatus': 'pending',
                }),
                200,
              );
            default:
              return http.Response('{}', 404);
          }
        }),
      );
      addTearDown(api.close);

      await tester.pumpWidget(MaterialApp(home: PiWorkspaceView(api: api)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      expect(find.text('Reconcile'), findsOneWidget);
      await tester.tap(find.byKey(const Key('compact-session')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      expect(
        requests
            .singleWhere((request) => request.url.path.endsWith('/compact'))
            .headers['x-awp-command-id'],
        'persisted-compaction-29',
      );
      await tester.tap(find.byKey(const Key('session-history')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('session-saved-1')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      expect(find.text('Saved answer'), findsOneWidget);

      await tester.tap(find.byKey(const Key('resume-session')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      expect(paths, contains('POST /api/pi-sessions/saved-1/resume'));
      expect(find.text('Saved answer'), findsOneWidget);

      await tester.tap(find.byKey(const Key('new-voice-session')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('confirm-new-voice-session')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));
      expect(paths, contains('POST /api/pi-sessions/new'));
      expect(find.text('Saved answer'), findsNothing);
    },
  );

  testWidgets(
    '@owner-client-001 renders the owner workspace from its HTTP boundary',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1180, 820);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final paths = <String>[];
      final api = VoiceApi(
        client: MockClient((request) async {
          paths.add(request.url.path);
          switch (request.url.path) {
            case '/api/pi-transcript':
              return http.Response(
                jsonEncode({
                  'revision': 1,
                  'entries': [
                    {
                      'id': 'u1',
                      'timestamp': 1,
                      'role': 'user',
                      'text': 'Where is the report?',
                    },
                    {
                      'id': 'a1',
                      'timestamp': 2,
                      'role': 'assistant',
                      'status': 'completed',
                      'text': jsonEncode({
                        'spoken': 'The report is ready.',
                        'presentation': {'kind': 'flow', 'title': 'Report'},
                      }),
                    },
                  ],
                }),
                200,
              );
            case '/api/pi-sessions':
              return http.Response(
                jsonEncode({
                  'sessions': [
                    {
                      'id': 'session-1',
                      'title': 'Release review',
                      'updatedAt': '2026-01-01T00:00:00Z',
                      'messageCount': 2,
                      'active': false,
                    },
                  ],
                }),
                200,
              );
            case '/api/tasks':
              return http.Response(
                jsonEncode([
                  {
                    'id': 'task-1',
                    'instruction': 'Review release',
                    'status': 'running',
                    'created_at': '2026-01-01T00:00:00Z',
                    'result': {'state': 'running'},
                  },
                ]),
                200,
              );
            case '/api/pi-context':
              return http.Response(
                jsonEncode({
                  'tokens': 4,
                  'contextWindow': 100,
                  'percent': 4,
                  'estimated': false,
                  'canCompact': false,
                }),
                200,
              );
            default:
              return http.Response('{}', 404);
          }
        }),
      );
      addTearDown(api.close);

      await tester.pumpWidget(MaterialApp(home: PiWorkspaceView(api: api)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 10));

      expect(find.text('Where is the report?'), findsOneWidget);
      expect(find.text('The report is ready.'), findsOneWidget);
      expect(find.text('Release review'), findsWidgets);
      expect(
        paths,
        containsAll([
          '/api/pi-transcript',
          '/api/pi-sessions',
          '/api/tasks',
          '/api/pi-context',
        ]),
      );
    },
  );

  test('@owner-client-001 owner workspace reads only server-scoped catalogs', () async {
    final requests = <http.Request>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        requests.add(request);
        if (request.url.path == '/api/pi-sessions') {
          return http.Response(
            jsonEncode({
              'sessions': [
                {
                  'id': 'owner-session',
                  'title': 'Private',
                  'updatedAt': '2026-01-01T00:00:00Z',
                  'messageCount': 2,
                  'active': false,
                },
              ],
            }),
            200,
          );
        }
        if (request.url.path == '/api/tasks') return http.Response('[]', 200);
        return http.Response('{}', 200);
      }),
      privateWorkspaceOrigin: Uri.parse('https://owner-fixture.ts.net/'),
    );
    addTearDown(api.close);

    final sessions = await api.sessions();
    final tasks = await api.tasks();

    expect(sessions.single.id, 'owner-session');
    expect(tasks, isEmpty);
    expect(
      requests.map((r) => r.url.path),
      containsAll(['/api/pi-sessions', '/api/tasks']),
    );
    // The request path stays inside the injected HTTP boundary; the private
    // Tailscale origin is applied to downloadable resources, not JSON requests.
    expect(requests.every((r) => r.url.path.startsWith('/api/')), isTrue);
  });

  test('@owner-client-002 starts a fresh session and resumes the selected identity', () async {
    final requests = <http.Request>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        requests.add(request);
        return http.Response('{}', 200);
      }),
    );
    addTearDown(api.close);

    await api.newSession();
    await api.resumeSession('saved/session');

    expect(requests[0].url.path, '/api/pi-sessions/new');
    expect(requests[0].method, 'POST');
    expect(requests[1].url.path, '/api/pi-sessions/saved%2Fsession/resume');
    expect(requests[1].method, 'POST');
  });

  test('@owner-client-003 sends typed turns and accepts only a structured safe response', () async {
    late http.Request received;
    final api = VoiceApi(
      client: MockClient((request) async {
        received = request;
        return http.Response(
          jsonEncode({
            'spoken': jsonEncode({
              'spoken': 'The answer',
              'presentation': {'kind': 'mermaid', 'content': 'graph TD; A-->B'},
            }),
          }),
          200,
        );
      }),
    );
    addTearDown(api.close);

    final response = await api.sendMessage('status please');

    expect(received.url.path, '/api/pi-message');
    expect(jsonDecode(received.body), {'message': 'status please'});
    expect(response.spoken, 'The answer');
    expect(response.presentation['kind'], 'mermaid');
  });

  test(
    '@owner-client-004 interrupts an active turn through the abort boundary',
    () async {
      late http.Request received;
      final api = VoiceApi(
        client: MockClient((request) async {
          received = request;
          return http.Response('{}', 200);
        }),
      );
      addTearDown(api.close);

      await api.stopTurn();

      expect(received.method, 'POST');
      expect(received.url.path, '/api/pi-turn/abort');
    },
  );

  test(
    '@owner-client-005 creates durable work and reads bounded progress',
    () async {
      final paths = <String>[];
      final requests = <http.Request>[];
      final api = VoiceApi(
        client: MockClient((request) async {
          paths.add(request.url.path);
          requests.add(request);
          if (request.method == 'POST') {
            return http.Response(
              jsonEncode({
                'id': 'task-1',
                'instruction': 'inspect docs',
                'status': 'pending',
                'created_at': '2026-01-01T00:00:00Z',
              }),
              200,
            );
          }
          return http.Response(
            jsonEncode([
              {'id': 4, 'spoken': 'Read README.md.'},
            ]),
            200,
          );
        }),
      );
      addTearDown(api.close);

      final task = await api.createTask(
        'inspect docs',
        idempotencyKey: 'admission-command-1',
      );
      final progress = await api.taskProgress(task.id);

      expect(task.id, 'task-1');
      expect(progress.single.spoken, 'Read README.md.');
      expect(requests.first.headers['idempotency-key'], 'admission-command-1');
      expect(paths, ['/api/tasks', '/api/tasks/task-1/progress']);
    },
  );

  test(
    '@owner-client-006 routes task action commands to the selected task',
    () async {
      final requests = <http.Request>[];
      final api = VoiceApi(
        client: MockClient((request) async {
          requests.add(request);
          return http.Response('{}', 200);
        }),
      );
      addTearDown(api.close);

      await api.taskAction('task-7', 'cancel');

      expect(requests.map((request) => request.url.path), [
        '/api/tasks/task-7/cancel',
      ]);
      expect(requests.every((request) => request.method == 'POST'), isTrue);
    },
  );

  test(
    '@owner-client-007 fetches the artifact separately from task status',
    () async {
      final requests = <http.Request>[];
      final client = MockClient((request) async {
        requests.add(request);
        if (request.url.path.endsWith('/artifacts/workspace')) {
          return http.Response('artifact unavailable', 404);
        }
        if (request.url.path == '/api/tasks') {
          return http.Response(
            jsonEncode([
              {
                'id': 'task-8',
                'status': 'completed',
                'created_at': '2026-01-01T00:00:00Z',
              },
            ]),
            200,
          );
        }
        return http.Response('{}', 404);
      });
      final api = VoiceApi(
        client: client,
        privateWorkspaceOrigin: Uri.parse('https://owner-fixture.ts.net/'),
      );
      addTearDown(api.close);

      final artifactResponse = await client.get(
        api.taskArtifactUrl('task-8', 'workspace'),
      );
      final tasks = await api.tasks();

      expect(requests.first.method, 'GET');
      expect(
        requests.first.url.toString(),
        'https://owner-fixture.ts.net/api/tasks/task-8/artifacts/workspace',
      );
      expect(artifactResponse.statusCode, 404);
      expect(tasks.single.status, 'completed');
      expect(requests.last.url.path, '/api/tasks');
    },
  );

  test('@owner-client-009 keeps presentation data separate from owner API commands', () async {
    final apiPaths = <String>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        apiPaths.add(request.url.path);
        return http.Response(
          jsonEncode({
            'spoken': 'Summary only',
            'presentation': {'kind': 'mermaid', 'content': 'graph TD; A-->B'},
          }),
          200,
        );
      }),
    );
    addTearDown(api.close);

    final result = await api.sendMessage('show the relationship');

    expect(result.presentation.keys, {'kind', 'content'});
    expect(result.presentation.keys, isNot(contains('credentials')));
    expect(result.presentation.keys, isNot(contains('tools')));
    expect(apiPaths, ['/api/pi-message']);
  });

  test('@owner-client-008 requests context maintenance explicitly without replaying a turn', () async {
    final requests = <http.Request>[];
    final api = VoiceApi(
      client: MockClient((request) async {
        requests.add(request);
        return http.Response(
          jsonEncode({
            'tokens': 10,
            'contextWindow': 100,
            'percent': 10,
            'estimated': true,
            'canCompact': true,
          }),
          200,
        );
      }),
    );
    addTearDown(api.close);

    final before = await api.context();
    final after = await api.compact();

    expect(before.canCompact, isTrue);
    expect(after.tokens, 10);
    expect(requests.map((r) => '${r.method} ${r.url.path}'), [
      'GET /api/pi-context',
      'GET /api/pi-context',
      'POST /api/pi-context/compact',
    ]);
    expect(
      requests.last.headers['x-awp-command-id'],
      matches(RegExp(r'^[a-f0-9]{48}$')),
    );
  });

  test(
    '@owner-client-009 retries and reload resumes the same compaction identity',
    () async {
      final firstRequests = <http.Request>[];
      final firstApi = VoiceApi(
        client: MockClient((request) async {
          firstRequests.add(request);
          if (request.url.path == '/api/pi-context') {
            return http.Response(jsonEncode({'canCompact': true}), 200);
          }
          return http.Response(jsonEncode({'detail': 'response lost'}), 409);
        }),
      );
      addTearDown(firstApi.close);
      await expectLater(firstApi.compact(), throwsA(isA<ApiException>()));
      final firstId = firstRequests.last.headers['x-awp-command-id'];
      await expectLater(firstApi.compact(), throwsA(isA<ApiException>()));
      expect(firstRequests.last.headers['x-awp-command-id'], firstId);

      final resumedRequests = <http.Request>[];
      final resumedApi = VoiceApi(
        client: MockClient((request) async {
          resumedRequests.add(request);
          return http.Response(
            jsonEncode(
              request.url.path == '/api/pi-context'
                  ? {
                      'canCompact': false,
                      'compactionCommandId': firstId,
                      'compactionStatus': 'pending',
                    }
                  : {
                      'canCompact': false,
                      'compactionCommandId': firstId,
                      'compactionStatus': 'completed',
                    },
            ),
            200,
          );
        }),
      );
      addTearDown(resumedApi.close);
      expect((await resumedApi.compact()).compactionStatus, 'completed');
      expect(resumedRequests.last.headers['x-awp-command-id'], firstId);
    },
  );
}
