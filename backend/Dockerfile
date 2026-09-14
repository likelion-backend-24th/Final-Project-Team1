# 실행 전용 이미지. jar 는 CD(또는 로컬)에서 ./gradlew bootJar 로 먼저 만든다.
# 빌드: docker build --build-arg SERVICE=ticket-service -f backend/Dockerfile backend
FROM eclipse-temurin:21-jre

ARG SERVICE
WORKDIR /app

COPY ${SERVICE}/build/libs/ /tmp/libs/
RUN find /tmp/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app/app.jar \; \
    && test -f /app/app.jar \
    && rm -rf /tmp/libs

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
