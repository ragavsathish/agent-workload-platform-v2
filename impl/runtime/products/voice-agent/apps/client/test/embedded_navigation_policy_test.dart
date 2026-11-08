import 'package:flutter_test/flutter_test.dart';
import 'package:voice_agent_client/platform/embedded_navigation_policy.dart';
import 'package:voice_agent_client/platform/native_permission_policy.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() {
  final trustedOrigin = Uri.parse('https://voice.example.test/');

  test('@owner-client-010 allows private paths and query parameters on the trusted origin', () {
    expect(
      isAllowedVoiceWorkspaceNavigation(
        Uri.parse('https://voice.example.test/?view=transcript'),
        trustedOrigin,
      ),
      isTrue,
    );
  });

  test('rejects lookalike hosts and non-HTTPS scheme changes', () {
    expect(
      isAllowedVoiceWorkspaceNavigation(
        Uri.parse('https://voice.example.test.attacker.invalid/'),
        trustedOrigin,
      ),
      isFalse,
    );
    expect(
      isAllowedVoiceWorkspaceNavigation(
        Uri.parse('http://voice.example.test/'),
        trustedOrigin,
      ),
      isFalse,
    );
  });

  test('allows the platform bootstrap blank document', () {
    expect(
      isAllowedVoiceWorkspaceNavigation(
        Uri.parse('about:blank'),
        trustedOrigin,
      ),
      isTrue,
    );
  });

  test('rejects an insecure configured origin', () {
    expect(
      isAllowedVoiceWorkspaceNavigation(
        Uri.parse('http://voice.example.test/'),
        Uri.parse('http://voice.example.test/'),
      ),
      isFalse,
    );
  });

  test('@owner-client-010 requests permission only for microphone media', () {
    expect(
      isMicrophoneOnlyRequest(const [WebViewPermissionResourceType.microphone]),
      isTrue,
    );
    expect(
      isMicrophoneOnlyRequest(const [WebViewPermissionResourceType.camera]),
      isFalse,
    );
    expect(
      isMicrophoneOnlyRequest(const [
        WebViewPermissionResourceType.microphone,
        WebViewPermissionResourceType.camera,
      ]),
      isFalse,
    );
    expect(isMicrophoneOnlyRequest(const []), isFalse);
  });

  test('@native-voice-permission-001 workspace retry does not request microphone access', () async {
    var permissionRequests = 0;
    var reloads = 0;
    final boundary = NativeMicrophonePermissionBoundary(
      requestMicrophone: () async {
        permissionRequests++;
        return NativeMicrophoneAccessResult.granted;
      },
    );

    await boundary.retryWorkspaceLoad(() async => reloads++);

    expect(reloads, 1);
    expect(permissionRequests, 0);
  });

  test(
    '@native-voice-permission-002 microphone access follows a page request',
    () async {
      var permissionRequests = 0;
      final boundary = NativeMicrophonePermissionBoundary(
        requestMicrophone: () async {
          permissionRequests++;
          return NativeMicrophoneAccessResult.granted;
        },
      );

      expect(permissionRequests, 0);
      expect(
        await boundary.handlePageRequest(const [
          WebViewPermissionResourceType.microphone,
        ]),
        NativeMicrophoneAccessResult.granted,
      );
      expect(permissionRequests, 1);
    },
  );

  test(
    'permanent OS denial remains distinguishable for the settings message',
    () async {
      final boundary = NativeMicrophonePermissionBoundary(
        requestMicrophone: () async =>
            NativeMicrophoneAccessResult.permanentlyDenied,
      );

      expect(
        await boundary.handlePageRequest(const [
          WebViewPermissionResourceType.microphone,
        ]),
        NativeMicrophoneAccessResult.permanentlyDenied,
      );
    },
  );

  test(
    '@native-voice-permission-003 camera and mixed requests do not prompt',
    () async {
      var permissionRequests = 0;
      final boundary = NativeMicrophonePermissionBoundary(
        requestMicrophone: () async {
          permissionRequests++;
          return NativeMicrophoneAccessResult.granted;
        },
      );

      expect(
        await boundary.handlePageRequest(const [
          WebViewPermissionResourceType.camera,
        ]),
        NativeMicrophoneAccessResult.denied,
      );
      expect(
        await boundary.handlePageRequest(const [
          WebViewPermissionResourceType.microphone,
          WebViewPermissionResourceType.camera,
        ]),
        NativeMicrophoneAccessResult.denied,
      );
      expect(permissionRequests, 0);
    },
  );
}
