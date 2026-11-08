import 'package:flutter_test/flutter_test.dart';
import 'package:voice_agent_client/api_client.dart';

void main() {
  test('task artifact URL stays on the configured private HTTPS origin', () {
    final api = VoiceApi(
      privateWorkspaceOrigin: Uri.parse('https://voice-fixture.ts.net/'),
    );
    addTearDown(api.close);

    expect(
      api
          .taskArtifactUrl(
            '11111111-1111-4111-8111-111111111111',
            'workspace',
          )
          .toString(),
      'https://voice-fixture.ts.net/api/tasks/11111111-1111-4111-8111-111111111111/artifacts/workspace',
    );
  });

  test('task artifact URL rejects insecure or untrusted origins', () {
    for (final origin in [
      Uri.parse('http://voice-fixture.ts.net/'),
      Uri.parse('https://example.test/'),
      Uri.parse('https://user@voice-fixture.ts.net/'),
    ]) {
      final api = VoiceApi(privateWorkspaceOrigin: origin);
      addTearDown(api.close);

      expect(
        () => api.taskArtifactUrl(
          '11111111-1111-4111-8111-111111111111',
          'workspace',
        ),
        throwsA(isA<ApiException>()),
      );
    }
  });
}
