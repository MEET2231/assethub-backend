# Stage 1: Build JAR
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn

# Allow execution of mvnw
RUN chmod +x mvnw

# Cache maven dependencies
RUN ./mvnw dependency:go-offline -B || true

# Build application
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/assethub-backend-*.jar app.jar

ENV PORT=5000
EXPOSE 5000

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-5000} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
