# 서버 실행 이미지. jar는 미리 만들어 둔다(./gradlew bootJar).
# EC2(메모리 1GB)에서 Gradle 빌드를 돌리면 메모리가 모자라서, 여기서는 만들어진 jar를 복사만 한다
FROM eclipse-temurin:25-jre

WORKDIR /app
# compose의 상태 확인(healthcheck)이 쓰는 curl. 기본 이미지에는 없다
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# 관리자(root)가 아닌 사용자로 실행한다. 서버가 뚫려도 컨테이너 안에서 할 수 있는 일을 줄인다
RUN useradd --system --uid 10001 teumta
COPY build/libs/teumta-server.jar app.jar
USER teumta

EXPOSE 8080
# 컨테이너에 준 메모리(compose의 mem_limit)의 70%까지만 힙으로 쓴다. 나머지는 JVM 자체와 스레드 몫이다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
