# syntax=docker/dockerfile:1

# ---- Build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Protocol test vectors, shared with the firmware tests
COPY protocol ./protocol
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

# ---- Run ----
FROM eclipse-temurin:25-jre

# Non-root user (the spi and gpio groups of the host are added by docker compose).
# UID 1000 = the default user of Raspberry Pi OS, so ./data stays editable on the host.
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && useradd --uid 1000 --create-home jarvis \
    && mkdir /data && chown jarvis /data

COPY --from=build /build/target/JARVIS.jar /app/jarvis.jar

# Sized for a Raspberry Pi 3B (1 GB RAM, 4 cores) :
# SerialGC = least memory and CPU overhead for a small heap ; compact headers save ~10% of heap.
ENV JAVA_OPTS="-Xms32m -Xmx192m -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+ExitOnOutOfMemoryError" \
    JARVIS_DATA_DIR=/data \
    JARVIS_API_PORT=8000

USER jarvis
WORKDIR /app
VOLUME /data
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${JARVIS_API_PORT} && printf "GET /health HTTP/1.0\r\n\r\n" >&3 && head -n1 <&3 | grep -q " 200 "'

# exec : java becomes PID 1 and receives SIGTERM directly (graceful save on docker stop).
# Arguments are passed through : "docker compose run --rm jarvis scan" scans the radio channels.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/jarvis.jar \"$@\"", "--"]
