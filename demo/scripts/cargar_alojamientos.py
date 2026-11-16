"""Carga en el Marketplace los alojamientos de demo/datos/alojamientos_bogota.json usando la API REST existente.

No escribe directamente en Neon ni en Cloudinary: reproduce el mismo flujo que la aplicación, así se aplican
todas las reglas de negocio (validaciones, historial de estados, auditoría y subida de imágenes a Cloudinary
con AlmacenamientoService):

  1. El anfitrión crea la publicación en BORRADOR con sus imágenes (POST /alojamientos, multipart).
     Se envían las coordenadas del IDT, así el backend no geocodifica la dirección.
  2. El anfitrión agrega un período de disponibilidad (POST /alojamientos/{id}/disponibilidad).
  3. El anfitrión la envía a revisión (POST /alojamientos/{id}/enviarrevision).
  4. El administrador la aprueba (POST /admin/alojamientos/{id}/aprobar) y queda ACTIVA.

Es idempotente: si el anfitrión ya tiene una publicación con el mismo título, no la duplica; solo completa los
pasos que le falten. No borra ni modifica otras publicaciones. El resultado (id, estado y URL de Cloudinary de
cada imagen) queda en demo/datos/resultado_carga.json y los errores en la consola.

Credenciales: se leen de variables de entorno, nunca del código.
    API_URL                  dirección del backend (por defecto http://localhost:8081)
    DEMO_ANFITRION_CORREO    anfitrión de demostración (por defecto lauragomez@gmail.com)
    DEMO_ADMIN_CORREO        administrador (por defecto andrescastillo@gmail.com)
    DEMO_ANFITRION_CONTRASENIA, DEMO_ADMIN_CONTRASENIA   obligatorias

Uso: python demo/scripts/cargar_alojamientos.py --imagenes ../database/Alojamientos [--dias 180] [--solo ID_DEMO]
"""

import argparse
import datetime
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

DATOS = Path(__file__).resolve().parent.parent / "datos"


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def llamar(self, metodo, ruta, cuerpo=None, multipart=None):
        cabeceras = {"Accept": "application/json"}
        if self.token:
            cabeceras["Authorization"] = "Bearer " + self.token
        datos = None
        if multipart is not None:
            limite = uuid.uuid4().hex
            datos = codificar_multipart(multipart, limite)
            cabeceras["Content-Type"] = "multipart/form-data; boundary=" + limite
        elif cuerpo is not None:
            datos = json.dumps(cuerpo).encode()
            cabeceras["Content-Type"] = "application/json"
        peticion = urllib.request.Request(self.base + ruta, data=datos, headers=cabeceras, method=metodo)
        try:
            with urllib.request.urlopen(peticion, timeout=120) as respuesta:
                texto = respuesta.read().decode() or "null"
                return respuesta.status, json.loads(texto)
        except urllib.error.HTTPError as error:
            texto = error.read().decode()
            try:
                return error.code, json.loads(texto)
            except json.JSONDecodeError:
                return error.code, texto

    def iniciar_sesion(self, correo, contrasenia):
        estado, respuesta = self.llamar("POST", "/auth/login", {"correo": correo, "contrasenia": contrasenia})
        if estado != 200 or not isinstance(respuesta, dict) or "token" not in respuesta:
            raise SystemExit(f"No se pudo iniciar sesión con {correo}: {estado} {respuesta}")
        self.token = respuesta["token"]


def codificar_multipart(partes, limite):
    cuerpo = bytearray()
    for nombre, valor in partes:
        cuerpo += f"--{limite}\r\n".encode()
        if isinstance(valor, Path):
            tipo = mimetypes.guess_type(valor.name)[0] or "application/octet-stream"
            cuerpo += (f'Content-Disposition: form-data; name="{nombre}"; filename="{valor.name}"\r\n'
                       f"Content-Type: {tipo}\r\n\r\n").encode()
            cuerpo += valor.read_bytes()
        else:
            # 'datos' va como texto, igual que lo envía el front en el FormData
            cuerpo += f'Content-Disposition: form-data; name="{nombre}"\r\n\r\n'.encode()
            cuerpo += valor.encode()
        cuerpo += b"\r\n"
    cuerpo += f"--{limite}--\r\n".encode()
    return bytes(cuerpo)


def variable(nombre, defecto=None):
    valor = os.environ.get(nombre, defecto)
    if not valor:
        raise SystemExit(f"Falta la variable de entorno {nombre}")
    return valor


def mis_publicaciones(api):
    publicaciones, pagina = {}, 0
    while True:
        estado, respuesta = api.llamar("GET", f"/alojamientos/mios?pagina={pagina}&tamanio=100")
        if estado != 200:
            raise SystemExit(f"No se pudieron listar las publicaciones del anfitrión: {estado} {respuesta}")
        for a in respuesta["contenido"]:
            publicaciones[a["titulo"]] = a
        pagina += 1
        if pagina >= respuesta["totalPaginas"]:
            return publicaciones


def datos_api(a, id_politica):
    return {
        "titulo": a["titulo"], "descripcion": a["descripcion"], "ubicacion": a["direccion"], "ciudad": a["ciudad"],
        "latitud": a["latitud"], "longitud": a["longitud"], "tipo": a["tipo"], "capacidad": a["capacidad"],
        "habitaciones": a["habitaciones"], "camas": a["camas"], "banos": a["banos"],
        "horaEntrada": a["hora_entrada"], "horaSalida": a["hora_salida"], "precioNoche": a["precio_noche"],
        "reglas": a["reglas"], "idPolitica": id_politica, "amenidades": a["amenidades"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--imagenes", required=True, help="carpeta con las imágenes (descargar_imagenes.py)")
    parser.add_argument("--dias", type=int, default=180, help="días de disponibilidad desde hoy")
    parser.add_argument("--solo", help="cargar solo este id_demo")
    args = parser.parse_args()

    carpeta = Path(args.imagenes)
    datos = json.loads((DATOS / "alojamientos_bogota.json").read_text(encoding="utf-8"))
    alojamientos = [a for a in datos["alojamientos"] if not args.solo or a["id_demo"] == args.solo]
    faltantes = [im["archivo"] for a in alojamientos for im in a["imagenes"] if not (carpeta / im["archivo"]).exists()]
    if faltantes:
        raise SystemExit(f"Faltan imágenes; ejecuta descargar_imagenes.py primero: {faltantes[:3]}...")

    base = os.environ.get("API_URL", "http://localhost:8081")
    anfitrion, admin = Api(base), Api(base)
    anfitrion.iniciar_sesion(variable("DEMO_ANFITRION_CORREO", "lauragomez@gmail.com"),
                             variable("DEMO_ANFITRION_CONTRASENIA"))
    admin.iniciar_sesion(variable("DEMO_ADMIN_CORREO", "andrescastillo@gmail.com"), variable("DEMO_ADMIN_CONTRASENIA"))

    estado, politicas = anfitrion.llamar("GET", "/alojamientos/politicas")
    id_politica = next((p["idPolitica"] for p in politicas if p["nombre"] == datos["alojamientos"][0]["politica_cancelacion"]), None)
    if id_politica is None:
        raise SystemExit(f"No existe la política {datos['alojamientos'][0]['politica_cancelacion']}: {politicas}")

    existentes = mis_publicaciones(anfitrion)
    hoy = datetime.date.today()
    resultado, errores = [], []
    for a in alojamientos:
        try:
            previo = existentes.get(a["titulo"])
            if previo is None:
                partes = [("datos", json.dumps(datos_api(a, id_politica), ensure_ascii=False))]
                partes += [("archivos", carpeta / im["archivo"]) for im in a["imagenes"]]
                estado, creado = anfitrion.llamar("POST", "/alojamientos", multipart=partes)
                if estado != 201:
                    raise RuntimeError(f"crear: {estado} {creado}")
                id_alojamiento, estado_actual = creado["idAlojamiento"], creado["estado"]
                print(f"creado      {id_alojamiento:5d} {a['titulo']}")
            else:
                id_alojamiento, estado_actual = previo["idAlojamiento"], previo["estado"]
                print(f"ya existe   {id_alojamiento:5d} {a['titulo']} ({estado_actual})")

            if estado_actual == "BORRADOR":
                estado, calendario = anfitrion.llamar("GET", f"/alojamientos/{id_alojamiento}/disponibilidad")
                periodos = calendario.get("periodos", []) if isinstance(calendario, dict) else []
                vigente = any(p.get("fechaFin", "") > hoy.isoformat() for p in periodos)
                if not vigente:
                    estado, r = anfitrion.llamar("POST", f"/alojamientos/{id_alojamiento}/disponibilidad",
                                                 {"fechaInicio": hoy.isoformat(),
                                                  "fechaFin": (hoy + datetime.timedelta(days=args.dias)).isoformat()})
                    if estado not in (200, 201):
                        raise RuntimeError(f"disponibilidad: {estado} {r}")
                estado, r = anfitrion.llamar("POST", f"/alojamientos/{id_alojamiento}/enviarrevision")
                if estado != 200:
                    raise RuntimeError(f"enviar a revisión: {estado} {r}")
                estado_actual = "PENDIENTE_REVISION"
            if estado_actual == "PENDIENTE_REVISION":
                estado, r = admin.llamar("POST", f"/admin/alojamientos/{id_alojamiento}/aprobar")
                if estado != 200:
                    raise RuntimeError(f"aprobar: {estado} {r}")
                estado_actual = "ACTIVA"

            # Sincroniza una publicación existente con la curaduría: sube las imágenes que falten (al final, en
            # el mismo orden) y actualiza la descripción, que lleva los créditos de las fotos
            estado, detalle = anfitrion.llamar("GET", f"/alojamientos/{id_alojamiento}")
            imagenes = detalle.get("detalleImagenes") or []
            faltantes = a["imagenes"][len(imagenes):]
            if faltantes:
                estado, r = anfitrion.llamar("POST", f"/alojamientos/{id_alojamiento}/imagenes",
                                             multipart=[("archivos", carpeta / im["archivo"]) for im in faltantes])
                if estado not in (200, 201):
                    raise RuntimeError(f"subir imágenes: {estado} {r}")
                print(f"            {id_alojamiento:5d} + {len(faltantes)} imágenes")
            if detalle.get("descripcion") != a["descripcion"]:
                estado, r = anfitrion.llamar("PUT", f"/alojamientos/{id_alojamiento}", {"descripcion": a["descripcion"]})
                if estado != 200:
                    raise RuntimeError(f"actualizar descripción: {estado} {r}")
            if faltantes or detalle.get("descripcion") != a["descripcion"]:
                estado, detalle = anfitrion.llamar("GET", f"/alojamientos/{id_alojamiento}")
                imagenes = detalle.get("detalleImagenes") or []
            resultado.append(dict(
                id_demo=a["id_demo"], id_alojamiento=id_alojamiento, estado=detalle.get("estado", estado_actual),
                latitud=detalle.get("latitud"), longitud=detalle.get("longitud"),
                imagenes=[dict(local=im["archivo"], clasificacion=im["clasificacion"], licencia=im["licencia"],
                               autor=im["autor"], url_original=im["url_original"],
                               url_cloudinary=(imagenes[i]["url"] if i < len(imagenes) else None),
                               public_id=(imagenes[i].get("rutaArchivo") if i < len(imagenes) else None))
                          for i, im in enumerate(a["imagenes"])]))
            print(f"            {id_alojamiento:5d} -> {resultado[-1]['estado']}, {len(imagenes)} imágenes en Cloudinary")
        except Exception as error:  # se registra y se sigue con el siguiente
            errores.append(dict(id_demo=a["id_demo"], error=str(error)))
            print(f"ERROR       {a['id_demo']}: {error}", file=sys.stderr)

    archivo = DATOS / "resultado_carga.json"
    anterior = json.loads(archivo.read_text(encoding="utf-8")) if archivo.exists() else {}
    # Conserva las notas de ejecuciones anteriores (por ejemplo, las imágenes huérfanas borradas)
    extra = {k: v for k, v in anterior.items() if k not in ("api", "fecha", "alojamientos", "errores")}
    archivo.write_text(json.dumps(dict(
        api=base, fecha=datetime.datetime.now().isoformat(timespec="seconds"), alojamientos=resultado,
        errores=errores, **extra), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(resultado)} alojamientos cargados, {len(errores)} errores. Detalle en demo/datos/resultado_carga.json")
    sys.exit(1 if errores else 0)


if __name__ == "__main__":
    main()
