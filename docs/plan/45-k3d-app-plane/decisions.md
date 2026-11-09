# Decisions

- k3d hosts Linux-compatible services; Pi, MLX, and Gondolin remain macOS-native.
- Grain remains the event-sourced system of record; the gateway is stateless.
- All services are ClusterIP. Short-lived loopback port-forwards are used for operator inspection.
- Semaphore runner host networking does not imply pod-to-host connectivity; prove a host bridge before use.
- Stateful services use persistent claims separate from v1.
- Pin image digests, including recording local AWP image digests after build.
- PromQL/OTLP belongs to a later slice with a dedicated v2 telemetry stack.
- v1 is not stopped in this slice.
