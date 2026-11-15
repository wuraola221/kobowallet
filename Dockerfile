
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and project configuration
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make Maven wrapper executable and download dependencies
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

# Copy application source code
COPY src ./src

# Package the application
RUN ./mvnw -B clean package -DskipTests


# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the executable JAR from the builder stage
COPY --from=builder /app/target/kobowallet-0.0.1-SNAPSHOT.jar app.jar

# Application listens on port 8080
EXPOSE 8080

# Create a dedicated non-root user and group
RUN groupadd --system spring && \
    useradd --system --uid 10001 --gid spring --no-create-home spring

# Run the application as the dedicated user
USER spring:spring

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
