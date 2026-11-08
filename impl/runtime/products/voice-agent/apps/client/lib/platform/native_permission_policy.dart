import 'package:webview_flutter/webview_flutter.dart';

/// Keeps operating-system permission requests behind a trusted page request.
enum NativeMicrophoneAccessResult { granted, denied, permanentlyDenied }

class NativeMicrophonePermissionBoundary {
  const NativeMicrophonePermissionBoundary({required this.requestMicrophone});

  final Future<NativeMicrophoneAccessResult> Function() requestMicrophone;

  /// A load retry only reloads the page; it cannot request device permission.
  Future<void> retryWorkspaceLoad(Future<void> Function() reload) => reload();

  /// Only page-originated microphone requests can reach the system prompt.
  Future<NativeMicrophoneAccessResult> handlePageRequest(
    Iterable<WebViewPermissionResourceType> types,
  ) async {
    if (!isMicrophoneOnlyRequest(types)) {
      return NativeMicrophoneAccessResult.denied;
    }
    return requestMicrophone();
  }
}

bool isMicrophoneOnlyRequest(Iterable<WebViewPermissionResourceType> types) =>
    types.isNotEmpty &&
    types.every((type) => type == WebViewPermissionResourceType.microphone);
