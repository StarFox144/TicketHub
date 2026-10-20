FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 tickethub && mkdir -p /app/data && chown tickethub /app/data
COPY --from=build /workspace/build/libs/*.jar app.jar
USER tickethub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
