#!/usr/bin/env bash
# Corre escenarios contra el backend, cada uno con base nueva, validación de 1 usuario y JVM calentada:
#   ./ejecutar.sh estres concurrencia ...        (MEZCLA=paginada para usar páginas pequeñas)
# Si la validación funcional falla, el escenario NO se ejecuta. Ctrl+C detiene el generador al instante y
# ./entorno.sh detener apaga el backend y H2.
set -uo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"; export TRABAJO="${TRABAJO:-${TMPDIR:-/tmp}/marketplace-alojamientos-carga}"
H2_JAR="$HOME/.m2/repository/com/h2database/h2/${H2_VERSION:-2.4.240}/h2-${H2_VERSION:-2.4.240}.jar"
JAVA="${JAVA_HOME_PRUEBAS:-$HOME/Prog/JDK}/bin/java"
trap '"$AQUI/entorno.sh" detener; exit 130' INT TERM
[[ -f "$TRABAJO/run.txt" ]] || "$AQUI/entorno.sh" compilar || exit 1
mkdir -p "$AQUI/resultados"
for e in "$@"; do
  "$AQUI/entorno.sh" detener; "$AQUI/entorno.sh" iniciar | tail -1
  if ! node "$AQUI/carga.mjs" validacion http://localhost:8081 "$TRABAJO/usuarios.json" "$TRABAJO/validacion" > "$TRABAJO/validacion_$e.txt"; then
    cat "$TRABAJO/validacion_$e.txt"; echo "$e: la validación funcional falló; no se ejecuta la prueba"; continue
  fi
  echo "$e: validación de 1 usuario correcta ($(grep -c '^OK' "$TRABAJO/validacion_$e.txt") comprobaciones)"
  node "$AQUI/carga.mjs" humo http://localhost:8081 "$TRABAJO/usuarios.json" "$TRABAJO/calentamiento" > /dev/null
  node "$AQUI/carga.mjs" "$e" http://localhost:8081 "$TRABAJO/usuarios.json" "$AQUI/resultados"
  # Todo lo que sale del escenario queda en un solo archivo: resultados/<escenario><SUFIJO>.json
  SQL=""
  if [[ "$e" == concurrencia ]]; then
    SQL=$("$JAVA" -cp "$H2_JAR" org.h2.tools.Shell -url "jdbc:h2:tcp://localhost:9092/mem:carga" -user sa -password "" -sql \
      "select count(*) as cruces_confirmados from reserva a join reserva b on a.id_alojamiento=b.id_alojamiento and a.id_reserva<b.id_reserva and a.fecha_entrada<b.fecha_salida and b.fecha_entrada<a.fecha_salida where a.estado='CONFIRMADA' and b.estado='CONFIRMADA';
       select count(*) as cotizaciones_con_mas_de_una_reserva from (select id_cotizacion from reserva where id_reserva>=100000 group by id_cotizacion having count(*)>1);
       select count(*) as reservas_con_mas_de_un_pago from (select id_reserva from pago where id_pago>=100000 group by id_reserva having count(*)>1);
       select estado,count(*) as reservas from reserva where id_reserva>=100000 group by estado;
       select estado,count(*) as pagos from pago where id_pago>=100000 group by estado;
       select count(*) as cancelaciones, count(distinct id_reserva) as reservas_canceladas from cancelacion;
       select estado,count(*) as registros_idempotencia from registro_idempotencia group by estado;")
    echo "$SQL"
  fi
  ERRORES=$(grep -c ' ERROR ' "$TRABAJO/app.log")
  RESUMEN=$(grep ' ERROR ' "$TRABAJO/app.log" | sed -E 's/^.* ERROR [0-9]+ --- (\[[^]]*\] )*//' | cut -c1-170 | sort | uniq -c | sort -rn | head -6)
  echo "$e: lineas ERROR en el log = $ERRORES"; [[ -n "$RESUMEN" ]] && echo "$RESUMEN"
  ARCHIVO="$AQUI/resultados/$e${SUFIJO:-}.json"
  [[ -f "$ARCHIVO" ]] && ERRORES="$ERRORES" RESUMEN="$RESUMEN" SQL="$SQL" node -e '
    const fs = require("fs"), f = process.argv[1], d = JSON.parse(fs.readFileSync(f, "utf8"));
    const lineas = t => (t || "").split("\n").map(l => l.trim()).filter(Boolean);
    d.erroresLog = { lineas: +process.env.ERRORES, resumen: lineas(process.env.RESUMEN) };
    if (process.env.SQL) d.comprobacionSql = lineas(process.env.SQL);
    fs.writeFileSync(f, JSON.stringify(d, null, 1));' "$ARCHIVO"
done
"$AQUI/entorno.sh" detener
