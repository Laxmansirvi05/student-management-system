# Container build for hosts that run Docker images, such as Render (docs/18-production-plan.md).
# Heroku does not use this file: it builds from the Procfile.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/campus-companion.jar app.jar
RUN useradd --system --no-create-home appuser
USER appuser

# Sized for a 512 MB container. Measured: about 330 MB after start-up and 380 MB under load
# (docs/18-production-plan.md section 13). Override JAVA_OPTS to change it.
ENV JAVA_OPTS="-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC"

# The host gives the port in PORT; Render uses 10000 by default.
EXPOSE 10000
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar app.jar"]
