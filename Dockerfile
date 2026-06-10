# ---- Stage 1: 빌드 ----
    FROM eclipse-temurin:17-jdk AS builder
    WORKDIR /app
    COPY gradlew .
    COPY gradle gradle
    COPY build.gradle settings.gradle ./
    RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
    COPY src src
    RUN ./gradlew bootJar --no-daemon -x test
    
    # ---- Stage 2: 실행 ----
    FROM eclipse-temurin:17-jre
    WORKDIR /app
    # 메타데이터 추출용 ffmpeg (application-prod에서 ffmpeg.path=/usr/bin)
    RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
        && rm -rf /var/lib/apt/lists/*
    RUN groupadd -r spring && useradd -r -g spring spring
    USER spring:spring
    COPY --from=builder /app/build/libs/*.jar app.jar
    EXPOSE 8080
    ENTRYPOINT ["java", \
      "-XX:+UseContainerSupport", \
      "-XX:MaxRAMPercentage=75.0", \
      "-jar", "app.jar"]