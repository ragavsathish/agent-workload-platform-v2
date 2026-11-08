const defaultVoiceWorkspaceUrl = String.fromEnvironment(
  'VOICE_WEB_URL',
  defaultValue: 'https://voice.example.invalid/',
);

bool isAllowedVoiceWorkspaceNavigation(Uri candidate, Uri trustedOrigin) {
  if (candidate.toString() == 'about:blank') {
    return true;
  }
  return trustedOrigin.scheme == 'https' &&
      candidate.scheme == 'https' &&
      candidate.host == trustedOrigin.host &&
      candidate.port == trustedOrigin.port;
}
