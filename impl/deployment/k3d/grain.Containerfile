FROM docker.io/library/clojure:temurin-21-tools-deps@sha256:142e93f5b4b3a2a93bef227515df452040f408f4ef71853b9f65598b1a625bb7

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 awp \
    && useradd --uid 10001 --gid 10001 --create-home --home-dir /home/awp --shell /usr/sbin/nologin awp

ENV HOME=/home/awp \
    JAVA_TOOL_OPTIONS=-Duser.home=/home/awp \
    CLJ_CACHE=/tmp/clj-cache \
    AWP_GRAIN_BIND_HOST=0.0.0.0 \
    AWP_GRAIN_SESSION_PORT=9447

WORKDIR /app/impl
COPY --chown=10001:10001 impl/deps.edn ./deps.edn
COPY --chown=10001:10001 impl/src ./src
COPY --chown=10001:10001 impl/deployment/k3d/start-grain.sh /usr/local/bin/awp-start-grain
RUN chmod 0555 /usr/local/bin/awp-start-grain
RUN clojure -P \
    && chown -R 10001:10001 /home/awp /app/impl

USER 10001:10001
EXPOSE 9447
ENTRYPOINT ["/usr/local/bin/awp-start-grain"]
