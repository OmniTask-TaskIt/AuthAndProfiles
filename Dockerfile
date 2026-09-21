# Etapa de compilación
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Etapa de ejecución ligera
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# No ejecutar como root
RUN addgroup -S app && adduser -S app -G app
USER app

# Puerto expuesto dinámico
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
