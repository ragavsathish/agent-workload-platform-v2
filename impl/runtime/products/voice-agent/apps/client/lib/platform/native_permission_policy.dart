import 'package:webview_flutter/webview_flutter.dart';

/// Only a page-originated microphone request can reach the system prompt.
bool isMicrophoneOnlyRequest(Iterable<WebViewPermissionResourceType> types) =>
    types.isNotEmpty &&
    types.every((type) => type == WebViewPermissionResourceType.microphone);
