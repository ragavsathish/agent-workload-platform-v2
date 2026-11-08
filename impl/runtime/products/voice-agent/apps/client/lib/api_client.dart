import 'dart:convert';
import 'dart:math';

import 'package:http/http.dart' as http;

import 'models.dart';

class ApiException implements Exception {
  const ApiException(this.message);
  final String message;
  @override
  String toString() => message;
}

class VoiceApi {
  VoiceApi({http.Client? client, this._privateWorkspaceOrigin})
    : _client = client ?? http.Client();
  static const _configuredOrigin = String.fromEnvironment('VOICE_API_ORIGIN');
  final http.Client _client;
  final Uri? _privateWorkspaceOrigin;
  String? _pendingCompactionId;
  Future<String>? _compactionIdentityLookup;
  String? _pendingTurnId;
  String? _pendingTurnMessage;

  String _newCommandId() {
    final random = Random.secure();
    return List<int>.generate(
      24,
      (_) => random.nextInt(256),
    ).map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
  }

  Future<String> _compactionIdentity() {
    final pending = _pendingCompactionId;
    if (pending != null) return Future.value(pending);
    return _compactionIdentityLookup ??= () async {
      // Grain's owner context survives client reload, so resume its active ID first.
      final current = await context();
      final recovered = current.compactionCommandId;
      final id =
          recovered != null &&
              {
                'queued',
                'pending',
                'uncertain',
              }.contains(current.compactionStatus)
          ? recovered
          : _newCommandId();
      _pendingCompactionId = id;
      return id;
    }().whenComplete(() => _compactionIdentityLookup = null);
  }

  Uri _uri(String path) => _configuredOrigin.isEmpty
      ? Uri.base.resolve(path)
      : Uri.parse(_configuredOrigin).resolve(path);

  /// Resolves non-JSON browser resources against the same API origin.
  Uri endpoint(String path) => _uri(path);

  /// Owner-only download served over the configured Tailscale HTTPS origin.
  Uri taskArtifactUrl(String taskId, String name) {
    final origin = _privateWorkspaceOrigin ?? Uri.base;
    if (origin.scheme != 'https' ||
        !origin.host.endsWith('.ts.net') ||
        origin.userInfo.isNotEmpty) {
      throw const ApiException('Tailscale HTTPS URL is not configured.');
    }
    return Uri(
      scheme: 'https',
      host: origin.host,
      port: origin.hasPort ? origin.port : null,
      pathSegments: ['api', 'tasks', taskId, 'artifacts', name],
    );
  }

  Future<JsonMap> _json(
    String path, {
    String method = 'GET',
    Object? body,
    Map<String, String> requestHeaders = const {},
  }) async {
    final uri = _uri(path);
    final headers = <String, String>{
      if (body != null) 'Content-Type': 'application/json',
      ...requestHeaders,
    };
    final response = method == 'POST'
        ? await _client.post(
            uri,
            headers: headers,
            body: body == null ? null : jsonEncode(body),
          )
        : await _client.get(
            uri,
            headers: {'Cache-Control': 'no-store', ...requestHeaders},
          );
    Object? decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      decoded = null;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        '${asMap(decoded)['detail'] ?? 'Request failed (${response.statusCode})'}',
      );
    }
    return asMap(decoded);
  }

  Future<ContextUsage> context() async =>
      ContextUsage.fromJson(await _json('/api/pi-context'));
  Future<ContextUsage> compact() async {
    final commandId = await _compactionIdentity();
    final result = ContextUsage.fromJson(
      await _json(
        '/api/pi-context/compact',
        method: 'POST',
        requestHeaders: {'x-awp-command-id': commandId},
      ),
    );
    if (result.compactionStatus == 'completed') _pendingCompactionId = null;
    return result;
  }

  Future<TranscriptSnapshot> transcript() async =>
      TranscriptSnapshot.fromJson(await _json('/api/pi-transcript'));
  Future<List<PiSessionSummary>> sessions() async {
    final body = await _json('/api/pi-sessions');
    return (body['sessions'] as List? ?? const [])
        .map((value) => PiSessionSummary.fromJson(asMap(value)))
        .toList(growable: false);
  }

  Future<TranscriptSnapshot> sessionTranscript(String id) async =>
      TranscriptSnapshot.fromJson(
        await _json('/api/pi-sessions/${Uri.encodeComponent(id)}'),
      );

  Future<void> newSession() async {
    await _json('/api/pi-sessions/new', method: 'POST');
  }

  Future<void> resumeSession(String id) async {
    await _json(
      '/api/pi-sessions/${Uri.encodeComponent(id)}/resume',
      method: 'POST',
    );
  }

  Future<VisualResponse> sendMessage(String message) async {
    var turnId = _pendingTurnId;
    if (turnId != null && _pendingTurnMessage != message) {
      throw const ApiException(
        'A previous typed turn is unresolved. Retry it before sending another message.',
      );
    }
    if (turnId == null) {
      turnId = _newCommandId();
      _pendingTurnId = turnId;
      _pendingTurnMessage = message;
    }
    final body = await _json(
      '/api/pi-message',
      method: 'POST',
      body: {'message': message},
      requestHeaders: {'Idempotency-Key': turnId},
    );
    final response = VisualResponse.fromValue(body);
    if (response == null) {
      throw const ApiException('Pi returned an invalid response.');
    }
    _pendingTurnId = null;
    _pendingTurnMessage = null;
    return response;
  }

  Future<void> stopTurn() async {
    await _json('/api/pi-turn/abort', method: 'POST');
  }

  Future<String?> taskRouting() async {
    final taskId = (await _json('/api/pi-task-routing'))['taskId'];
    if (taskId == null) return null;
    if (taskId is! String) {
      throw const ApiException('Task routing returned an invalid response.');
    }
    return taskId;
  }

  Future<void> attachTask(String id, String commandId) async {
    await _json(
      '/api/pi-task-routing/attach',
      method: 'POST',
      body: {'taskId': id},
      requestHeaders: {'Idempotency-Key': commandId},
    );
  }

  Future<void> detachTask(String commandId) async {
    await _json(
      '/api/pi-task-routing/detach',
      method: 'POST',
      body: const {},
      requestHeaders: {'Idempotency-Key': commandId},
    );
  }

  Future<List<AgentTask>> tasks() async {
    final response = await _client.get(
      _uri('/api/tasks'),
      headers: {'Cache-Control': 'no-store'},
    );
    if (response.statusCode != 200) {
      throw const ApiException('Tasks are unavailable.');
    }
    final body = jsonDecode(response.body);
    if (body is! List) {
      throw const ApiException('Tasks returned an invalid response.');
    }
    return body
        .map((v) => AgentTask.fromJson(asMap(v)))
        .toList(growable: false);
  }

  Future<AgentTask> createTask(
    String instruction, {
    required String idempotencyKey,
  }) async => AgentTask.fromJson(
    await _json(
      '/api/tasks',
      method: 'POST',
      body: {'instruction': instruction},
      requestHeaders: {'Idempotency-Key': idempotencyKey},
    ),
  );

  Future<List<TaskProgressEvent>> taskProgress(
    String id, {
    int after = 0,
  }) async {
    final response = await _client.get(
      _uri('/api/tasks/${Uri.encodeComponent(id)}/progress?after=$after'),
      headers: {'Cache-Control': 'no-store'},
    );
    if (response.statusCode != 200) {
      throw const ApiException('Task progress is unavailable.');
    }
    final body = jsonDecode(response.body);
    if (body is! List) {
      throw const ApiException('Task progress returned an invalid response.');
    }
    return body
        .map((value) => TaskProgressEvent.fromJson(asMap(value)))
        .toList(growable: false);
  }

  Future<String> narrateTaskProgress(
    String id, {
    required int after,
    required int through,
  }) async {
    final body = await _json(
      '/api/tasks/${Uri.encodeComponent(id)}/narration',
      method: 'POST',
      body: {'after': after, 'through': through},
    );
    final spoken = body['spoken'];
    if (spoken is! String) {
      throw const ApiException('Task narration returned an invalid response.');
    }
    return spoken;
  }

  Future<void> taskAction(String id, String action) async => _json(
    '/api/tasks/${Uri.encodeComponent(id)}/$action',
    method: 'POST',
    body: const {},
  );

  void close() => _client.close();
}
