#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SCRUM-289 · Pruebas de inyección SQL contra el backend real (Spring Boot + PostgreSQL en Neon).

Adaptado de SoftwareFake/marketplace-backend/docs/seguridad/sqli_test.py. Solo usa la librería
estándar de Python.

Qué prueba
  1. Evasión de autenticación en POST /auth/login (campo correo y campo contraseña).
  2. Inyección por diferencia de resultados (boolean-based) en la búsqueda pública y sus filtros.
  3. Parámetros de ruta numéricos (id) en rutas públicas y protegidas.
  4. Rutas que exigen sesión de administrador y reciben texto: auditoría, búsqueda por correo,
     reportes y filtros por estado.
  5. Campos JSON numéricos que reciben texto.
  6. Inyección de segundo orden: se guarda un texto malicioso y se vuelve a leer.
  7. Inyección ciega por tiempo con pg_sleep(5), que también detecta consultas apiladas.
  8. Integridad: los conteos de datos antes y después de la batería deben coincidir.

No usa cargas destructivas (DROP, DELETE, UPDATE) porque la base de datos es la compartida del
equipo: si alguna consulta fuera inyectable, pg_sleep lo demuestra sin dañar datos.

Las credenciales se leen de variables de entorno y nunca se escriben en los resultados:
    SQLI_ADMIN        correo del administrador semilla
    SQLI_CUENTAS      correos de cuentas semilla no administradoras, separados por comas
    SQLI_CLAVE        contraseña de las cuentas semilla

Uso:
    python sqli_test.py [base_url]        (por defecto http://localhost:8081)

Genera resultados_sqli.json en la carpeta actual.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081"
TIMEOUT = 30            # alto para medir pg_sleep
UMBRAL_TIEMPO = 4.5     # pg_sleep(5) ejecutado => la respuesta tarda al menos ~5 s
PAUSA_LOGIN = 2.2       # el limitador permite 30 fallos por IP por minuto

ADMIN = os.environ.get("SQLI_ADMIN", "")
CUENTAS = [c.strip() for c in os.environ.get("SQLI_CUENTAS", "").split(",") if c.strip()]
CLAVE = os.environ.get("SQLI_CLAVE", "")

# Señales de que un error de base de datos llegó a la respuesta (inyección basada en errores)
FUGAS_SQL = ["sqlstate", "psqlexception", "org.hibernate", "syntax error", "jdbc", "select ",
             "relation \"", "unterminated quoted", "postgresql"]

resultados = []


def peticion(metodo, ruta, *, params=None, cuerpo=None, token=None):
    """Hace una petición y devuelve (status, segundos, texto, error_de_red)."""
    url = BASE + ruta
    if params:
        url += "?" + urllib.parse.urlencode(params, doseq=True)
    datos = None
    cabeceras = {"Accept": "application/json"}
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode("utf-8")
        cabeceras["Content-Type"] = "application/json"
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=datos, headers=cabeceras, method=metodo)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            return r.status, time.perf_counter() - t0, r.read().decode("utf-8", "replace"), None
    except urllib.error.HTTPError as e:
        return e.code, time.perf_counter() - t0, e.read().decode("utf-8", "replace"), None
    except Exception as e:  # noqa: BLE001
        return None, time.perf_counter() - t0, "", repr(e)


def leer_json(texto):
    try:
        return json.loads(texto)
    except ValueError:
        return None


def resumen_cuerpo(texto):
    """Resume la respuesta sin copiar datos personales: solo el mensaje o el total de resultados."""
    datos = leer_json(texto)
    if isinstance(datos, dict):
        if "message" in datos:
            return {"message": datos["message"]}
        if "totalElementos" in datos:
            return {"totalElementos": datos["totalElementos"]}
        return {"campos": sorted(datos.keys())[:12]}
    if isinstance(datos, list):
        return {"elementos": len(datos)}
    return {"bytes": len(texto or "")}


def total(texto):
    datos = leer_json(texto)
    return datos.get("totalElementos") if isinstance(datos, dict) else None


def fuga_sql(texto):
    bajo = (texto or "").lower()
    return [f for f in FUGAS_SQL if f in bajo]


def registrar(categoria, caso, endpoint, vector, payload, status, t, texto, error, veredicto, nota):
    resultados.append({
        "categoria": categoria, "caso": caso, "endpoint": endpoint, "vector": vector,
        "payload": payload, "status": status, "tiempo_seg": round(t, 3),
        "respuesta": resumen_cuerpo(texto), "error_red": error,
        "veredicto": veredicto, "nota": nota,
    })
    marca = {"SEGURO": "[OK]", "VULNERABLE": "[!!]", "INFO": "[..]", "BLOQUEADO": "[429]",
             "ERROR_RED": "[xx]"}.get(veredicto, "[??]")
    print(f"{marca} {caso[:52]:<52} status={status} t={t:5.2f}s {nota}")


def veredicto_base(status, texto, error):
    """Reglas comunes: error de red, limitador, 500 o fuga de SQL en la respuesta."""
    if error:
        return "ERROR_RED", error
    if status == 429:
        return "BLOQUEADO", "429: el limitador cortó el intento antes de consultar la base de datos"
    fugas = fuga_sql(texto)
    if fugas:
        return "VULNERABLE", f"la respuesta deja ver detalles de SQL: {fugas}"
    if status is not None and status >= 500:
        return "INFO", f"error {status} sin detalles de SQL: revisar el log del servidor"
    return None, None


def iniciar_sesion(correo, clave):
    s, _, b, _ = peticion("POST", "/auth/login", cuerpo={"correo": correo, "contrasenia": clave})
    datos = leer_json(b) or {}
    return datos.get("token") if s == 200 else None


# ---------------------------------------------------------------------------------------------
# 0. Conectividad, sesión del administrador y conteos iniciales
# ---------------------------------------------------------------------------------------------
print("=" * 96)
print(f"Objetivo: {BASE}   ({datetime.now(timezone.utc).isoformat()})")
print("=" * 96)

s, t, b, e = peticion("GET", "/alojamientos", params={"pagina": 0, "tamanio": 1})
if e:
    print(f"No se pudo conectar con {BASE}: {e}")
    sys.exit(2)
TIEMPO_BASE = t
TOTAL_CATALOGO = total(b)
registrar("0-linea-base", "catálogo completo", "GET /alojamientos", "-", "-", s, t, b, e, "INFO",
          f"{TOTAL_CATALOGO} publicaciones; tiempo normal {t:.2f}s")

s, t, b, e = peticion("GET", "/alojamientos", params={"ciudad": "Bogotá", "tamanio": 1})
TOTAL_BOGOTA = total(b)
registrar("0-linea-base", "catálogo filtrado ciudad=Bogotá", "GET /alojamientos", "ciudad", "Bogotá",
          s, t, b, e, "INFO", f"{TOTAL_BOGOTA} publicaciones")

s, t, b, e = peticion("GET", "/alojamientos", params={"tipo": "Hotel", "tamanio": 1})
TOTAL_TIPO = total(b)
registrar("0-linea-base", "catálogo filtrado por tipo", "GET /alojamientos", "tipo", "Hotel",
          s, t, b, e, "INFO", f"{TOTAL_TIPO} publicaciones")

TOKEN = iniciar_sesion(ADMIN, CLAVE) if ADMIN and CLAVE else None
if not TOKEN:
    print("Sin sesión de administrador: se omiten las pruebas que exigen sesión.")

TOTAL_USUARIOS = TOTAL_AUDITORIA_LOGIN = None
if TOKEN:
    s, t, b, e = peticion("GET", "/admin/usuarios", params={"tamanio": 1}, token=TOKEN)
    TOTAL_USUARIOS = total(b)
    registrar("0-linea-base", "usuarios registrados (admin)", "GET /admin/usuarios", "-", "-", s, t, b, e,
              "INFO", f"{TOTAL_USUARIOS} usuarios")
    s, t, b, e = peticion("GET", "/admin/auditoria", params={"accion": "LOGIN", "tamanio": 1}, token=TOKEN)
    TOTAL_AUDITORIA_LOGIN = total(b)
    registrar("0-linea-base", "auditoría accion=LOGIN (admin)", "GET /admin/auditoria", "accion", "LOGIN",
              s, t, b, e, "INFO", f"{TOTAL_AUDITORIA_LOGIN} eventos")

s, t, b, e = peticion("POST", "/auth/login", cuerpo={"correo": "noexiste@prueba.local", "contrasenia": "x"})
registrar("0-linea-base", "login con una cuenta que no existe", "POST /auth/login", "-", "(control)",
          s, t, b, e, "INFO", "control negativo")
time.sleep(PAUSA_LOGIN)

# ---------------------------------------------------------------------------------------------
# 1. Evasión de autenticación en POST /auth/login
# ---------------------------------------------------------------------------------------------
PAYLOADS_LOGIN = [
    "' OR '1'='1",
    "' OR '1'='1'-- ",
    "' OR 1=1-- ",
    "admin'-- ",
    "' OR ''='",
    "') OR ('1'='1",
    "' UNION SELECT NULL-- ",
    "' OR 1=1#",
    "\" OR \"1\"=\"1",
    "'; SELECT 1-- ",
]


def evaluar_login(status, texto, error):
    v, nota = veredicto_base(status, texto, error)
    if v:
        return v, nota
    if status == 200 and "token" in (texto or ""):
        return "VULNERABLE", "se obtuvo un token sin credenciales válidas"
    return "SEGURO", f"{status}: el valor se trató como texto, sin sesión"


for p in PAYLOADS_LOGIN:
    s, t, b, e = peticion("POST", "/auth/login", cuerpo={"correo": p, "contrasenia": "x"})
    v, nota = evaluar_login(s, b, e)
    registrar("1-login", f"login/correo: {p}", "POST /auth/login", "correo", p, s, t, b, e, v, nota)
    time.sleep(PAUSA_LOGIN)

# Contraseña: se reparte entre las cuentas semilla para no superar 5 fallos por cuenta
for i, p in enumerate(PAYLOADS_LOGIN):
    if not CUENTAS:
        break
    cuenta = CUENTAS[i % len(CUENTAS)]
    s, t, b, e = peticion("POST", "/auth/login", cuerpo={"correo": cuenta, "contrasenia": p})
    v, nota = evaluar_login(s, b, e)
    registrar("1-login", f"login/contrasenia (cuenta semilla {i % len(CUENTAS) + 1}): {p}", "POST /auth/login",
              "contrasenia", p, s, t, b, e, v, nota)
    time.sleep(PAUSA_LOGIN)

# ---------------------------------------------------------------------------------------------
# 2. Búsqueda pública: inyección por diferencia de resultados
# ---------------------------------------------------------------------------------------------
# Si "Bogotá' OR '1'='1" se ejecutara como SQL devolvería todo el catálogo; tratado como texto
# no coincide con ninguna ciudad y devuelve 0.
PAYLOADS_BOOLEANOS = [
    "{v}' OR '1'='1",
    "{v}' OR 1=1-- ",
    "{v}') OR ('1'='1",
    "{v}' UNION SELECT NULL,NULL,NULL-- ",
    "{v}' AND '1'='2",
]


def evaluar_filtro(status, texto, error, base_total):
    v, nota = veredicto_base(status, texto, error)
    if v:
        return v, nota
    obtenido = total(texto)
    if status == 200 and obtenido is not None and base_total is not None and obtenido > base_total:
        return "VULNERABLE", f"devolvió {obtenido} resultados, más que el filtro legítimo ({base_total})"
    if status == 200:
        return "SEGURO", f"200 con {obtenido} resultados: el filtro comparó el texto completo"
    return "SEGURO", f"{status}: valor rechazado"


for campo, valor, legitimo in [("ciudad", "Bogotá", TOTAL_BOGOTA), ("tipo", "Hotel", TOTAL_TIPO)]:
    for plantilla in PAYLOADS_BOOLEANOS:
        p = plantilla.format(v=valor)
        s, t, b, e = peticion("GET", "/alojamientos", params={campo: p, "tamanio": 1})
        v, nota = evaluar_filtro(s, b, e, legitimo)
        registrar("2-busqueda", f"buscar/{campo}: {p}", "GET /alojamientos", campo, p, s, t, b, e, v, nota)

for p in ["wifi' OR '1'='1", "wifi') OR ('1'='1", "x' UNION SELECT NULL-- "]:
    s, t, b, e = peticion("GET", "/alojamientos", params={"amenidades": p, "tamanio": 1})
    v, nota = evaluar_filtro(s, b, e, 0)
    registrar("2-busqueda", f"buscar/amenidades: {p}", "GET /alojamientos", "amenidades", p, s, t, b, e, v, nota)

for p in ["precio_menor; SELECT 1", "precio_menor' OR '1'='1", "idAlojamiento DESC", "(SELECT 1)"]:
    s, t, b, e = peticion("GET", "/alojamientos", params={"orden": p, "tamanio": 1})
    v, nota = veredicto_base(s, b, e)
    if not v:
        v, nota = ("SEGURO", "400: el orden solo acepta valores de una lista fija") if s == 400 else \
            ("INFO", f"status {s}")
    registrar("2-busqueda", f"buscar/orden: {p}", "GET /alojamientos", "orden", p, s, t, b, e, v, nota)

for ruta, campo, valor, legitimo in [("/alojamientos/disponibles", "tipo", "Hotel", TOTAL_TIPO)]:
    for plantilla in PAYLOADS_BOOLEANOS[:3]:
        p = plantilla.format(v=valor)
        s, t, b, e = peticion("GET", ruta, params={campo: p, "tamanio": 1})
        v, nota = evaluar_filtro(s, b, e, legitimo)
        registrar("2-busqueda", f"disponibles/{campo}: {p}", f"GET {ruta}", campo, p, s, t, b, e, v, nota)

for p in ["Bogotá' OR '1'='1", "x' UNION SELECT NULL-- "]:
    s, t, b, e = peticion("GET", "/alojamientos/filtros", params={"ciudad": p})
    v, nota = veredicto_base(s, b, e)
    if not v:
        v, nota = "SEGURO", f"{s}: filtros calculados sin ejecutar el texto"
    registrar("2-busqueda", f"filtros/ciudad: {p}", "GET /alojamientos/filtros", "ciudad", p, s, t, b, e, v, nota)

# ---------------------------------------------------------------------------------------------
# 3. Parámetros de ruta numéricos
# ---------------------------------------------------------------------------------------------
PAYLOADS_ID = ["1 OR 1=1", "1;SELECT 1", "1' OR '1'='1", "0 UNION SELECT NULL", "1)) OR ((1=1"]
RUTAS_ID = [("/alojamientos/{}", False), ("/alojamientos/{}/resenas", False), ("/reservas/{}", True),
            ("/admin/auditoria/{}", True)]
for plantilla_ruta, con_sesion in RUTAS_ID:
    if con_sesion and not TOKEN:
        continue
    for p in PAYLOADS_ID:
        ruta = plantilla_ruta.format(urllib.parse.quote(p))
        s, t, b, e = peticion("GET", ruta, token=TOKEN if con_sesion else None)
        v, nota = veredicto_base(s, b, e)
        if not v:
            v, nota = ("SEGURO", "400: Spring rechaza el valor no numérico antes de consultar") if s == 400 else \
                ("SEGURO", f"{s}: tratado como id") if s in (200, 404) else ("INFO", f"status {s}")
        registrar("3-ruta-id", f"{plantilla_ruta.format('{id}')}: {p}", "GET " + plantilla_ruta.format("{id}"),
                  "id (ruta)", p, s, t, b, e, v, nota)

# ---------------------------------------------------------------------------------------------
# 4. Rutas con sesión de administrador que reciben texto
# ---------------------------------------------------------------------------------------------
if TOKEN:
    for p in ["LOGIN' OR '1'='1", "LOGIN' OR 1=1-- ", "x' UNION SELECT NULL-- ", "LOGIN') OR ('1'='1"]:
        s, t, b, e = peticion("GET", "/admin/auditoria", params={"accion": p, "tamanio": 1}, token=TOKEN)
        v, nota = evaluar_filtro(s, b, e, TOTAL_AUDITORIA_LOGIN)
        registrar("4-admin", f"auditoria/accion: {p}", "GET /admin/auditoria", "accion", p, s, t, b, e, v, nota)

    for p in ["' OR '1'='1", "x@x.com' OR 1=1-- ", "' UNION SELECT NULL-- "]:
        s, t, b, e = peticion("GET", "/usuarios/obtenercorreo", params={"correo": p}, token=TOKEN)
        v, nota = veredicto_base(s, b, e)
        if not v:
            v, nota = ("VULNERABLE", "devolvió un usuario con un correo que no existe") if s == 200 else \
                ("SEGURO", f"{s}: el correo se convierte en un hash antes de consultar")
        registrar("4-admin", f"obtenercorreo/correo: {p}", "GET /usuarios/obtenercorreo", "correo", p,
                  s, t, b, e, v, nota)

    for endpoint, ruta, params, vector, p in [
        ("/admin/reportes/{tipo}", "/admin/reportes/" + urllib.parse.quote("usuarios' OR '1'='1"), {"formato": "pdf"},
         "tipo (ruta)", "usuarios' OR '1'='1"),
        ("/admin/reportes/{tipo}", "/admin/reportes/usuarios", {"formato": "pdf' OR '1'='1"}, "formato", "pdf' OR '1'='1"),
        ("/admin/alojamientos", "/admin/alojamientos", {"estado": "ACTIVA' OR '1'='1"}, "estado",
         "ACTIVA' OR '1'='1"),
        ("/admin/reservas", "/admin/reservas", {"estado": "CONFIRMADA' OR '1'='1"}, "estado",
         "CONFIRMADA' OR '1'='1"),
        ("/admin/resenas", "/admin/resenas", {"estado": "PUBLICADA' OR '1'='1"}, "estado", "PUBLICADA' OR '1'='1"),
    ]:
        s, t, b, e = peticion("GET", ruta, params=params, token=TOKEN)
        v, nota = veredicto_base(s, b, e)
        if not v:
            v, nota = ("SEGURO", "400: solo se aceptan valores de una lista fija") if s == 400 else \
                ("INFO", f"status {s}: revisar")
        registrar("4-admin", f"{endpoint} {vector}: {p}", "GET " + endpoint, vector, p, s, t, b, e, v, nota)

# ---------------------------------------------------------------------------------------------
# 5. Campos JSON numéricos que reciben texto
# ---------------------------------------------------------------------------------------------
if TOKEN:
    for cuerpo in [{"idAlojamiento": "1 OR 1=1", "fechaEntrada": "2027-01-10", "fechaSalida": "2027-01-12",
                    "huespedes": 1},
                   {"idAlojamiento": 1, "fechaEntrada": "2027-01-10' OR '1'='1", "fechaSalida": "2027-01-12",
                    "huespedes": "1; SELECT 1"}]:
        s, t, b, e = peticion("POST", "/cotizaciones", cuerpo=cuerpo, token=TOKEN)
        v, nota = veredicto_base(s, b, e)
        if not v:
            v, nota = ("SEGURO", f"{s}: el JSON no se convierte al tipo esperado y se rechaza") if s in (400, 403) \
                else ("INFO", f"status {s}")
        registrar("5-json", f"cotizaciones: {json.dumps(cuerpo, ensure_ascii=False)[:46]}", "POST /cotizaciones",
                  "cuerpo JSON", json.dumps(cuerpo, ensure_ascii=False), s, t, b, e, v, nota)

# ---------------------------------------------------------------------------------------------
# 6. Inyección de segundo orden: guardar un texto malicioso y volver a leerlo
# ---------------------------------------------------------------------------------------------
if TOKEN:
    s, _, b, _ = peticion("GET", "/usuarios/perfil", token=TOKEN)
    perfil = leer_json(b) or {}
    nombre_original = perfil.get("nombre")
    malicioso = "Prueba'); SELECT pg_sleep(5);--"
    s, t, b, e = peticion("PUT", "/usuarios/perfil", cuerpo={"nombre": malicioso}, token=TOKEN)
    s2, t2, b2, e2 = peticion("GET", "/usuarios/perfil", token=TOKEN)
    guardado = (leer_json(b2) or {}).get("nombre")
    v, nota = veredicto_base(s, b, e)
    if not v:
        v, nota = ("SEGURO", "el texto se guardó y se leyó literal, sin ejecutarse") if \
            guardado == malicioso and t < UMBRAL_TIEMPO and t2 < UMBRAL_TIEMPO else \
            ("INFO", f"guardado={guardado == malicioso}, tiempos {t:.2f}s/{t2:.2f}s")
    registrar("6-segundo-orden", "perfil/nombre guardado y releído", "PUT + GET /usuarios/perfil", "nombre",
              malicioso, s, t + t2, b2, e, v, nota)
    if nombre_original:
        s, t, b, e = peticion("PUT", "/usuarios/perfil", cuerpo={"nombre": nombre_original}, token=TOKEN)
        registrar("6-segundo-orden", "perfil/nombre restaurado", "PUT /usuarios/perfil", "nombre",
                  "(nombre original)", s, t, b, e, "INFO", "se restauró el nombre original")

# ---------------------------------------------------------------------------------------------
# 7. Inyección ciega por tiempo con pg_sleep(5)
# ---------------------------------------------------------------------------------------------
PRUEBAS_TIEMPO = [
    ("login/correo apilada", "POST", "/auth/login", None, {"correo": "x'; SELECT pg_sleep(5)-- ", "contrasenia": "x"}, False),
    ("login/correo subconsulta", "POST", "/auth/login", None,
     {"correo": "x' OR (SELECT 1 FROM pg_sleep(5))='1", "contrasenia": "x"}, False),
    ("buscar/ciudad apilada", "GET", "/alojamientos", {"ciudad": "x'; SELECT pg_sleep(5)-- "}, None, False),
    ("buscar/tipo condicional", "GET", "/alojamientos", {"tipo": "x' AND 1=(SELECT 1 FROM pg_sleep(5))-- "}, None, False),
    ("buscar/amenidades", "GET", "/alojamientos", {"amenidades": "x' OR pg_sleep(5) IS NULL-- "}, None, False),
    ("auditoria/accion", "GET", "/admin/auditoria", {"accion": "x'; SELECT pg_sleep(5)-- "}, None, True),
    ("obtenercorreo/correo", "GET", "/usuarios/obtenercorreo", {"correo": "x' OR pg_sleep(5) IS NULL-- "}, None, True),
]
for nombre, metodo, ruta, params, cuerpo, con_sesion in PRUEBAS_TIEMPO:
    if con_sesion and not TOKEN:
        continue
    s, t, b, e = peticion(metodo, ruta, params=params, cuerpo=cuerpo, token=TOKEN if con_sesion else None)
    payload = json.dumps(cuerpo or params, ensure_ascii=False)
    v, nota = veredicto_base(s, b, e)
    if not v:
        v, nota = ("VULNERABLE", f"tardó {t:.2f}s: pg_sleep se ejecutó") if t >= UMBRAL_TIEMPO else \
            ("SEGURO", f"respondió en {t:.2f}s: pg_sleep no se ejecutó")
    registrar("7-tiempo", f"time-based {nombre}", f"{metodo} {ruta}", "varios", payload, s, t, b, e, v, nota)
    if ruta == "/auth/login":
        time.sleep(PAUSA_LOGIN)

# ---------------------------------------------------------------------------------------------
# 8. Integridad: los conteos no deben cambiar
# ---------------------------------------------------------------------------------------------
s, t, b, e = peticion("GET", "/alojamientos", params={"tamanio": 1})
despues = total(b)
registrar("8-integridad", "catálogo después de la batería", "GET /alojamientos", "-", "-", s, t, b, e,
          "SEGURO" if despues == TOTAL_CATALOGO else "VULNERABLE",
          f"antes {TOTAL_CATALOGO}, después {despues}")
if TOKEN:
    s, t, b, e = peticion("GET", "/admin/usuarios", params={"tamanio": 1}, token=TOKEN)
    despues = total(b)
    registrar("8-integridad", "usuarios después de la batería", "GET /admin/usuarios", "-", "-", s, t, b, e,
              "SEGURO" if despues == TOTAL_USUARIOS else "VULNERABLE",
              f"antes {TOTAL_USUARIOS}, después {despues}")

# ---------------------------------------------------------------------------------------------
# Resumen
# ---------------------------------------------------------------------------------------------
cuenta = {v: sum(1 for r in resultados if r["veredicto"] == v)
          for v in ("SEGURO", "VULNERABLE", "INFO", "BLOQUEADO", "ERROR_RED")}
resumen = {
    "objetivo": BASE,
    "fecha_utc": datetime.now(timezone.utc).isoformat(),
    "tiempo_base_seg": round(TIEMPO_BASE, 3),
    "sesion_admin": bool(TOKEN),
    "total_pruebas": len(resultados),
    "por_veredicto": cuenta,
    "veredicto_global": "VULNERABLE" if cuenta["VULNERABLE"] else "NO VULNERABLE A SQLi",
    "resultados": resultados,
}
with open("resultados_sqli.json", "w", encoding="utf-8") as f:
    json.dump(resumen, f, ensure_ascii=False, indent=2)

print("=" * 96)
print(f"TOTAL: {len(resultados)} | " + " | ".join(f"{k}: {v}" for k, v in cuenta.items()))
print(f"VEREDICTO GLOBAL: {resumen['veredicto_global']}")
print("Resultados guardados en resultados_sqli.json")
print("=" * 96)
