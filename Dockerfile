# Estágio de build: compila e empacota sem rodar os testes (rodam no CI/local com Docker disponível).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# Estágio final: só o JRE, usuário sem privilégio.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
# Memória: instância gratuita pequena. SerialGC e C1 reduzem consumo; 65% da RAM do container para o heap.
# A porta vem de PORT (perfil cloud); logs em stdout.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k", "-jar", "app.jar"]
