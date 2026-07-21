FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S wallet && adduser -S wallet -G wallet
COPY --from=build /build/target/*.jar app.jar
USER wallet
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
