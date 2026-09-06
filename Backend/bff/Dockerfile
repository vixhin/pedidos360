# ─── Etapa 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copiar pom.xml y descargar dependencias primero (cache layer)
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# ─── Etapa 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario no-root para seguridad
RUN addgroup -S bff && adduser -S bff -G bff
USER bff

# Copiar el jar compilado
COPY --from=builder /app/target/bff-*.jar app.jar

# Puerto del BFF
EXPOSE 8090

# Variables de entorno requeridas en Docker
# (se pasan via docker-compose.yml)
ENV AZURE_TENANT_ID=""
ENV AZURE_API_CLIENT_ID=""
ENV FRONTEND_ORIGIN="http://localhost:4200"
ENV USUARIO_SERVICE_URL="http://usuario:8081"
ENV PEDIDOS_SERVICE_URL="http://pedidos:8082"
ENV CARRITO_SERVICE_URL="http://carrito:8083"
ENV ANALITICA_SERVICE_URL="http://analitica:8084"
ENV PRODUCTOS_SERVICE_URL="http://productos:8085"
ENV NOTIFICACION_SERVICE_URL="http://notificacion:8086"

ENTRYPOINT ["java", "-jar", "app.jar"]
