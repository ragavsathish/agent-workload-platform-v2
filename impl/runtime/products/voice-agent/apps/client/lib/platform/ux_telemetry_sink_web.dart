// UxTelemetry retains bounded samples in Dart. The browser has no external
// telemetry receiver, so publishing must not interrupt the voice controls.
void publishUxMetric(Map<String, Object> event) {}
