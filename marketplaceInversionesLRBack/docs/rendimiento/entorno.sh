#!/usr/bin/env bash
# Entorno aislado de las pruebas de carga del backend (marketplaceInversionesLRBack):
# H2 en memoria (proceso propio, por TCP) + backend con todos los servicios externos apagados.
#   ./entorno.sh compilar | iniciar [--sql] | detener
# Variables: TRABAJO (carpeta temporal, por defecto ${TMPDIR:-/tmp}/marketplace-alojamientos-carga),
#            JAVA_HOME_PRUEBAS (JDK 17+ con javac, por defecto ~/Prog/JDK),
#            CARGA_XMX (heap del backend, por defecto 1g), CARGA_POOL (conexiones Hikari, por defecto 10),
#            CARGA_OSIV (open-in-view, por defecto true como Spring Boot).
# Nunca usa la base de Neon, Brevo, Stripe, Cloudinary ni Nominatim: las variables de entorno reales se borran.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/../.." && pwd)"
TRABAJO="${TRABAJO:-${TMPDIR:-/tmp}/marketplace-alojamientos-carga}"; mkdir -p "$TRABAJO"
JDK="${JAVA_HOME_PRUEBAS:-$HOME/Prog/JDK}"; PATH="$JDK/bin:$PATH"; export JAVA_HOME="$JDK"
# Clave AES de pruebas del pom.xml (aes.clave.pruebas): no protege datos reales.
CLAVE_PRUEBA="N+5fgm46SnNTQGLiHCM1TU8qDomjNY8g+DKb+6SoVNQ="
H2_VERSION="${H2_VERSION:-2.4.240}"
H2_JAR="$HOME/.m2/repository/com/h2database/h2/$H2_VERSION/h2-$H2_VERSION.jar"
URL="jdbc:h2:tcp://localhost:9092/mem:carga;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
MAIN=co.edu.unbosque.marketplaceInversionesLR.MarketplaceInversionesLrApplication

compilar() {
  [[ -f "$H2_JAR" ]] || { echo "Falta $H2_JAR (driver H2 en ~/.m2)"; exit 1; }
  # Dependencias ya descargadas en ~/.m2: Maven en modo offline, sin tocar target/ del proyecto.
  (cd "$RAIZ" && ./mvnw -o -q dependency:build-classpath -Dmdep.includeScope=compile -Dmdep.outputFile="$TRABAJO/cp-compilar.txt")
  (cd "$RAIZ" && ./mvnw -o -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile="$TRABAJO/cp.txt")
  rm -rf "$TRABAJO/out"; mkdir -p "$TRABAJO/out"
  javac -nowarn -proc:full -parameters -encoding UTF-8 --release 17 -cp "$(cat "$TRABAJO/cp-compilar.txt")" \
    -processorpath "$(tr ':' '\n' < "$TRABAJO/cp-compilar.txt" | grep -m1 lombok)" -d "$TRABAJO/out" $(find "$RAIZ/src/main/java" -name '*.java')
  cp "$RAIZ/src/main/resources/application.properties" "$TRABAJO/out/"
  echo "out:$(tr ':' '\n' < "$TRABAJO/cp.txt" | grep -v -E 'devtools|lombok' | paste -sd:):$H2_JAR" > "$TRABAJO/run.txt"
  (cd "$RAIZ" && git rev-parse --short HEAD 2>/dev/null || echo sin-git) > "$TRABAJO/commit.txt"
  echo "Compilado el commit $(cat "$TRABAJO/commit.txt") en $TRABAJO/out"
}

iniciar() {
  cd "$TRABAJO"
  if curl -s -m 1 localhost:8081 > /dev/null 2>&1 || (exec 3<>/dev/tcp/127.0.0.1/9092) 2>/dev/null; then
    echo "Los puertos 8081 o 9092 ya están en uso: detén la otra prueba antes de empezar"; exit 1
  fi
  nohup java -Xmx4g -cp "$H2_JAR" org.h2.tools.Server -tcp -tcpPort 9092 -ifNotExists > h2.log 2>&1 &
  echo $! > h2.pid; sleep 2
  local log=app.log; local extra=()
  if [[ "${1:-}" == "--sql" ]]; then log=app-sql.log; extra=(--logging.level.org.hibernate.SQL=DEBUG); fi
  env -u DB_URL -u DB_USERNAME -u DB_PASSWORD -u STRIPE_SECRET_KEY -u STRIPE_PUBLISHABLE_KEY -u CLOUDINARY_URL \
      -u MAIL_USERNAME -u GOOGLE_CLIENT_ID -u DEMO_POBLAR -u MIGRAR_IMAGENES \
    AES_SECRET_KEY="$CLAVE_PRUEBA" MAIL_PASSWORD= GEOCODING_HABILITADO=false \
    nohup java "-Xms${CARGA_XMX:-1g}" "-Xmx${CARGA_XMX:-1g}" -cp "$(cat run.txt)" "$MAIN" \
    --spring.datasource.url="$URL" --spring.datasource.driver-class-name=org.h2.Driver \
    --spring.datasource.username=sa --spring.datasource.password= \
    --spring.datasource.hikari.maximum-pool-size="${CARGA_POOL:-10}" \
    --spring.jpa.hibernate.ddl-auto=create \
    --spring.jpa.open-in-view="${CARGA_OSIV:-true}" \
    --spring.mail.host=127.0.0.1 --spring.mail.port=1 --management.health.mail.enabled=false \
    --app.geocoding.habilitado=false --app.stripe.secret-key= --app.demo.poblar=false --app.imagenes.migrar=false \
    --management.endpoints.web.exposure.include=health,metrics --server.tomcat.mbeanregistry.enabled=true \
    --logging.level.root=WARN --logging.file.name= \
    "${extra[@]}" > "$log" 2>&1 &
  echo $! > app.pid
  for _ in $(seq 1 90); do
    [[ "$(curl -s -m 2 localhost:8081/actuator/health || true)" == *UP* ]] && break; sleep 1
  done
  curl -s -m 2 localhost:8081/actuator/health; echo
  [[ "${SIN_DATOS:-}" == 1 ]] && return 0   # SIN_DATOS=1: solo el esquema vacío (para inspeccionarlo)
  AES_SECRET_KEY="$CLAVE_PRUEBA" java -cp "$(cat run.txt)" "$AQUI/SembrarDatos.java" "$URL" usuarios.json
}

detener() {
  cd "$TRABAJO"
  for f in app.pid h2.pid; do [[ -f $f ]] && kill "$(cat $f)" 2>/dev/null || true; rm -f "$f"; done
  sleep 2
}
"${1:?compilar|iniciar|detener}" "${@:2}"
