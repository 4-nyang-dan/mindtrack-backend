# 1. Gradle로 JAR 빌드
FROM gradle:8.4-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test

# 2. 빌드된 JAR 실행용 이미지
FROM openjdk:17-jdk-slim
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*


COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
