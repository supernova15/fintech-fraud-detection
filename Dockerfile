ARG BASE_JDK_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:21-jdk
ARG BASE_JRE_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:21-jre

FROM ${BASE_JDK_IMAGE} AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM build AS test
RUN ./gradlew --no-daemon test

FROM ${BASE_JRE_IMAGE}
WORKDIR /app
RUN useradd --create-home --uid 10001 appuser
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER 10001
EXPOSE 9090
ENTRYPOINT ["java","-jar","/app/app.jar"]
