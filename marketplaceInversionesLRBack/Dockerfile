# ---------- Etapa 1: compilación ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Primero el wrapper y el pom, para que Docker guarde en caché las dependencias
# y no las vuelva a descargar si solo cambia el código
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Luego el código. Las pruebas no se compilan ni se corren aquí: ya corren en el CI
# y las de integración necesitan Docker
COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true \
	&& mv target/marketplaceInversionesLR-*.jar target/app.jar

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Usuario sin privilegios para no ejecutar la app como root
RUN useradd --system --create-home spring
USER spring

COPY --from=build /app/target/app.jar app.jar

# Java puede usar hasta el 75 % de la memoria del contenedor
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Render indica el puerto en la variable PORT; en local se usa 8081.
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8081} -jar app.jar"]
