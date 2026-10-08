# 빗물받이 집사 백엔드 이미지
# jar는 CI(./gradlew bootJar)에서 미리 빌드한다. 이미지 안에서는 Gradle을 돌리지 않는다.

# ───────── 1단계: Spring Boot 레이어 추출 ─────────
FROM eclipse-temurin:17-jre AS extractor
WORKDIR /workspace
COPY build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ───────── 2단계: 실행 이미지 ─────────
FROM eclipse-temurin:17-jre

# root가 아닌 전용 사용자로 실행한다
RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --no-create-home spring

WORKDIR /app

# 자주 바뀌지 않는 레이어부터 복사해 의존성 레이어가 캐시되게 한다
COPY --from=extractor --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/application/ ./

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
