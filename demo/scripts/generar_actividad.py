"""Genera la actividad de la demostración por la API REST (SCRUM-206 / T18).

Sobre los alojamientos ya cargados, y con los mismos flujos que usa la aplicación:
  - Reseñas y calificaciones de las estadías terminadas (las crea generar_estadias_pasadas.py).
  - Reservas futuras pagadas con el simulador de pagos (métodos pm_card_* de prueba).
  - Un pago rechazado (la reserva queda CANCELADA con el pago RECHAZADO).
  - Dos cancelaciones con la política MODERADA: una con reembolso total y otra con reembolso del 50 %.
  - Cotizaciones vigentes que no se reservaron.
  - Favoritos y conversaciones entre huéspedes y la anfitriona.

Las reseñas empiezan con "Reseña de demostración:" porque son de huéspedes ficticios sobre publicaciones de
prueba; no son opiniones reales sobre los establecimientos. Las notificaciones de la aplicación son internas:
ninguno de estos flujos envía correos.

No repite reseñas, reservas (mismo alojamiento y fechas), favoritos ni conversaciones. Las cotizaciones
sueltas sí se crean de nuevo en cada ejecución (vencen solas).

Variables: API_URL (por defecto http://localhost:8081) y DEMO_CONTRASENIA (contraseña de los usuarios demo).
Uso: python demo/scripts/generar_actividad.py
"""

import datetime
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

DATOS = Path(__file__).resolve().parent.parent / "datos"
ANFITRIONA = "lauragomez@gmail.com"

# Calificación y comentario por estadía terminada, en el orden de las estadías de cada huésped.
COMENTARIOS = [
    (5, "Muy buena ubicación y el proceso de reserva fue claro de principio a fin."),
    (4, "La habitación correspondía a la descripción. El check-in tomó un poco más de lo esperado."),
    (5, "Atención amable y todo muy limpio. Volvería a reservar."),
    (3, "Cumplió para una estadía corta, aunque había algo de ruido en la noche."),
    (4, "Buena relación entre precio y servicio; cerca de transporte y restaurantes."),
    (5, "Excelente comunicación con la anfitriona antes de llegar."),
    (4, "Cómodo y bien ubicado para moverse por la ciudad."),
    (5, "La estadía fue tranquila y las fotos de la publicación ayudaron a saber qué esperar."),
]

# (huésped, id_demo, días desde hoy hasta la llegada, noches, huéspedes, método de pago, acción)
RESERVAS = [
    ("anatorres@gmail.com", "hotel-dann-avenida-19", 20, 3, 2, "pm_card_visa", None),
    ("diegomora@gmail.com", "four-seasons-casa-medina", 30, 2, 2, "pm_card_mastercard", None),
    ("camilaherrera@gmail.com", "estelar-la-fontana", 45, 4, 1, "pm_card_amex", None),
    ("sofiaperez@gmail.com", "hotel-de-la-opera", 25, 3, 2, "pm_card_visa", "cancelar"),
    ("camilaherrera@gmail.com", "viaggio-urbano", 3, 2, 1, "pm_card_visa_debit", "cancelar"),
    ("pedrodiaz@gmail.com", "botanico-hostel", 15, 2, 1, "pm_card_chargeDeclined", None),
]
MOTIVOS = {"hotel-de-la-opera": "Cambio de planes de viaje.", "viaggio-urbano": "Se canceló la reunión de trabajo en Bogotá."}

# (huésped, id_demo, días hasta la llegada, noches, huéspedes): cotizaciones que no se reservan
COTIZACIONES = [
    ("camilaherrera@gmail.com", "the-artisan-dc", 40, 2, 2),
    ("anatorres@gmail.com", "city-express-plus-aeropuerto", 10, 1, 1),
    ("pedrodiaz@gmail.com", "ibis-bogota-museo", 18, 3, 2),
]

FAVORITOS = {
    "anatorres@gmail.com": ["four-seasons-casa-medina", "the-artisan-dc", "hotel-de-la-opera"],
    "diegomora@gmail.com": ["estelar-la-fontana", "botanico-hostel"],
    "sofiaperez@gmail.com": ["viaggio-urbano", "hotel-dann-avenida-19"],
}

# (huésped, id_demo, mensaje del huésped, respuesta de la anfitriona)
CONVERSACIONES = [
    ("anatorres@gmail.com", "hotel-dann-avenida-19", "Hola, ¿se puede dejar el equipaje antes de la hora de entrada?",
     "Hola Ana, sí: puedes dejarlo en recepción desde la mañana."),
    ("pedrodiaz@gmail.com", "botanico-hostel", "¿La habitación privada tiene baño propio?",
     "Hola Pedro, en la publicación de demostración se ofrece con 1 baño. Cualquier duda me escribes."),
]


class Api:
    def __init__(self, base, correo, contrasenia):
        self.base, self.correo = base.rstrip("/"), correo
        estado, r = self.llamar("POST", "/auth/login", {"correo": correo, "contrasenia": contrasenia}, token=False)
        if estado != 200:
            sys.exit(f"No se pudo iniciar sesión con {correo}: {estado} {r}")
        self.token = r["token"]

    def llamar(self, metodo, ruta, cuerpo=None, token=True, cabeceras=None):
        h = {"Content-Type": "application/json", "Accept": "application/json", **(cabeceras or {})}
        if token:
            h["Authorization"] = "Bearer " + self.token
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        try:
            with urllib.request.urlopen(urllib.request.Request(self.base + ruta, data=datos, headers=h,
                                                               method=metodo), timeout=60) as r:
                return r.status, json.loads(r.read().decode() or "null")
        except urllib.error.HTTPError as e:
            texto = e.read().decode()
            try:
                return e.code, json.loads(texto)
            except json.JSONDecodeError:
                return e.code, texto

    def todas(self, ruta):
        elementos, pagina = [], 0
        while True:
            estado, r = self.llamar("GET", f"{ruta}{'&' if '?' in ruta else '?'}pagina={pagina}&tamanio=100")
            if estado != 200:
                raise RuntimeError(f"GET {ruta}: {estado} {r}")
            lista = r.get("contenido", r) if isinstance(r, dict) else r
            elementos += lista
            pagina += 1
            if not isinstance(r, dict) or pagina >= r.get("totalPaginas", 1):
                return elementos


def cotizar(api, id_aloj, llegada, noches, huespedes):
    estado, r = api.llamar("POST", "/cotizaciones", {
        "idAlojamiento": id_aloj, "fechaEntrada": llegada.isoformat(),
        "fechaSalida": (llegada + datetime.timedelta(days=noches)).isoformat(), "huespedes": huespedes})
    if estado not in (200, 201):
        raise RuntimeError(f"cotizar {id_aloj}: {estado} {r}")
    return r


def main():
    base = os.environ.get("API_URL", "http://localhost:8081")
    contrasenia = os.environ.get("DEMO_CONTRASENIA")
    if not contrasenia:
        sys.exit("Falta la variable de entorno DEMO_CONTRASENIA")
    carga = json.loads((DATOS / "resultado_carga.json").read_text(encoding="utf-8"))
    ids = {a["id_demo"]: a["id_alojamiento"] for a in carga["alojamientos"]}
    hoy = datetime.date.today()
    sesiones = {}

    def api(correo):
        if correo not in sesiones:
            sesiones[correo] = Api(base, correo, contrasenia)
        return sesiones[correo]

    resumen = {"resenas": [], "reservas": [], "cancelaciones": [], "cotizaciones": [], "favoritos": [],
               "conversaciones": [], "errores": []}

    # 1. Reseñas de las estadías terminadas
    huespedes = sorted({c for c, *_ in RESERVAS} | {"anatorres@gmail.com", "diegomora@gmail.com"})
    for correo in huespedes:
        terminadas = [r for r in api(correo).todas("/reservas/mias") if r.get("completada")]
        for k, r in enumerate(sorted(terminadas, key=lambda r: r["idReserva"])):
            if r.get("tieneResena"):
                continue
            calificacion, texto = COMENTARIOS[(r["idReserva"] + k) % len(COMENTARIOS)]
            estado, res = api(correo).llamar("POST", "/resenas", {
                "idReserva": r["idReserva"], "calificacion": calificacion,
                "comentario": "Reseña de demostración: " + texto})
            if estado in (200, 201):
                resumen["resenas"].append(dict(huesped=correo, id_reserva=r["idReserva"],
                                               alojamiento=r["idAlojamiento"], calificacion=calificacion))
            else:
                resumen["errores"].append(f"reseña reserva {r['idReserva']}: {estado} {res}")

    # 2. Reservas futuras, un pago rechazado y cancelaciones
    for correo, id_demo, dias, noches, n, metodo, accion in RESERVAS:
        id_aloj, llegada = ids[id_demo], hoy + datetime.timedelta(days=dias)
        cliente = api(correo)
        previa = [r for r in cliente.todas("/reservas/mias")
                  if r["idAlojamiento"] == id_aloj and r["fechaEntrada"] == llegada.isoformat()]
        if previa:
            reserva = previa[0]
        else:
            cot = cotizar(cliente, id_aloj, llegada, noches, n)
            estado, reserva = cliente.llamar("POST", "/reservas", {"idCotizacion": cot["idCotizacion"],
                                                                   "metodoPago": metodo},
                                             cabeceras={"Idempotency-Key": str(uuid.uuid4())})
            if estado == 402 and isinstance(reserva, dict):  # pago rechazado: la reserva viene en "dato"
                reserva = reserva.get("dato", reserva)
            elif estado != 201:
                resumen["errores"].append(f"reserva {id_demo}: {estado} {reserva}")
                continue
        resumen["reservas"].append(dict(huesped=correo, alojamiento=id_aloj, id_reserva=reserva.get("idReserva"),
                                        llegada=llegada.isoformat(), metodo=metodo, estado=reserva.get("estado")))
        if accion == "cancelar" and reserva.get("estado") == "CONFIRMADA":
            estado, r = cliente.llamar("POST", f"/reservas/{reserva['idReserva']}/cancelar",
                                       {"motivo": MOTIVOS[id_demo]}, cabeceras={"Idempotency-Key": str(uuid.uuid4())})
            if estado == 200:
                c = r.get("cancelacion") or r
                resumen["cancelaciones"].append(dict(id_reserva=reserva["idReserva"], respuesta=c))
                resumen["reservas"][-1]["estado"] = "CANCELADA"
            else:
                resumen["errores"].append(f"cancelar {reserva['idReserva']}: {estado} {r}")

    # 3. Cotizaciones vigentes sin reservar
    for correo, id_demo, dias, noches, n in COTIZACIONES:
        cot = cotizar(api(correo), ids[id_demo], hoy + datetime.timedelta(days=dias), noches, n)
        resumen["cotizaciones"].append(dict(huesped=correo, alojamiento=ids[id_demo], id_cotizacion=cot["idCotizacion"],
                                            total=cot["total"]))

    # 4. Favoritos
    for correo, lista in FAVORITOS.items():
        actuales = {f["idAlojamiento"] for f in api(correo).todas("/favoritos")}
        for id_demo in lista:
            if ids[id_demo] not in actuales:
                estado, r = api(correo).llamar("POST", f"/favoritos/{ids[id_demo]}")
                if estado not in (200, 201):
                    resumen["errores"].append(f"favorito {id_demo}: {estado} {r}")
                    continue
            resumen["favoritos"].append(dict(huesped=correo, alojamiento=ids[id_demo]))

    # 5. Conversaciones con la anfitriona
    for correo, id_demo, mensaje, respuesta in CONVERSACIONES:
        existentes = [c for c in api(correo).todas("/conversaciones") if c["idAlojamiento"] == ids[id_demo]]
        if existentes:
            resumen["conversaciones"].append(dict(huesped=correo, id_conversacion=existentes[0]["idConversacion"]))
            continue
        estado, c = api(correo).llamar("POST", "/conversaciones", {"idAlojamiento": ids[id_demo], "mensaje": mensaje})
        if estado not in (200, 201):
            resumen["errores"].append(f"conversación {id_demo}: {estado} {c}")
            continue
        estado, r = api(ANFITRIONA).llamar("POST", f"/conversaciones/{c['idConversacion']}/mensajes",
                                           {"contenido": respuesta})
        if estado not in (200, 201):
            resumen["errores"].append(f"respuesta {c['idConversacion']}: {estado} {r}")
        resumen["conversaciones"].append(dict(huesped=correo, id_conversacion=c["idConversacion"]))

    (DATOS / "resultado_actividad.json").write_text(json.dumps(dict(
        api=base, fecha=datetime.datetime.now().isoformat(timespec="seconds"), **resumen),
        ensure_ascii=False, indent=2, default=str) + "\n", encoding="utf-8")
    print(f"reseñas {len(resumen['resenas'])}, reservas {len(resumen['reservas'])}, "
          f"cancelaciones {len(resumen['cancelaciones'])}, cotizaciones {len(resumen['cotizaciones'])}, "
          f"favoritos {len(resumen['favoritos'])}, conversaciones {len(resumen['conversaciones'])}, "
          f"errores {len(resumen['errores'])}")
    for e in resumen["errores"]:
        print("ERROR", e, file=sys.stderr)
    sys.exit(1 if resumen["errores"] else 0)


if __name__ == "__main__":
    main()
