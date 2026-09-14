FROM eclipse-temurin:25-jre-noble

LABEL org.opencontainers.image.source="https://github.com/hyundai-autoever-team3/fleaflea-backend"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 fleaflea \
    && useradd --system --uid 10001 --gid fleaflea --home-dir /app fleaflea

WORKDIR /app

COPY --chown=fleaflea:fleaflea build/deploy/fleaflea.jar /app/fleaflea.jar

USER fleaflea

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx768m"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/fleaflea.jar"]
