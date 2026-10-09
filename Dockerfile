FROM eclipse-temurin:25-jre-noble

LABEL org.opencontainers.image.source="https://github.com/hyundai-autoever-team3/fleaflea-backend"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 fleaflea \
    && useradd --system --uid 10001 --gid fleaflea --home-dir /app fleaflea

WORKDIR /app

COPY --chown=fleaflea:fleaflea build/deploy/fleaflea.jar /app/fleaflea.jar

RUN curl -fsSL "https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.31.1/opentelemetry-javaagent-2.31.1.jar" \
      -o /app/opentelemetry-javaagent.jar \
    && echo "bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba  /app/opentelemetry-javaagent.jar" | sha256sum -c - \
    && chown fleaflea:fleaflea /app/opentelemetry-javaagent.jar

USER fleaflea

EXPOSE 8080 8081

ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENV OTEL_JAVAAGENT_ENABLED=false

ENTRYPOINT ["sh", "-c", "exec java -javaagent:/app/opentelemetry-javaagent.jar $JAVA_OPTS -jar /app/fleaflea.jar"]
