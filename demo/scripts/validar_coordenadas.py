"""Valida las coordenadas de demo/datos/alojamientos_bogota.json y escribe coordenadas_alojamientos.csv.

Para cada alojamiento:
  1. Comprueba que el punto del IDT esté dentro del área urbana de Bogotá.
  2. Geocodifica a la inversa el punto en Nominatim (OpenStreetMap, el mismo proveedor que usa el backend en
     GeocodingService) y compara la localidad devuelta con la del IDT.
  3. Busca la dirección pública en Nominatim con el mismo formato que usa el backend
     ("<dirección>, Bogotá, Colombia") y mide la distancia al punto del IDT. Nominatim no resuelve muchas
     direcciones colombianas con placa; en ese caso se deja constancia y vale la validación 2.
  4. Si la cadena publica coordenadas propias, mide también esa distancia.

Respeta el límite de Nominatim (1 petición por segundo). Uso: python demo/scripts/validar_coordenadas.py
"""

import csv
import json
import math
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

DATOS = Path(__file__).resolve().parent.parent / "datos"
NOMINATIM = "https://nominatim.openstreetmap.org"
AGENTE = "AcademicMarketplaceSeed/1.0 (proyecto academico)"
# Área urbana de cada ciudad, con margen (Bogotá sin Sumapaz).
AREAS = {"Bogotá": (4.45, 4.84, -74.23, -73.98), "Medellín": (6.15, 6.35, -75.66, -75.52)}
_ultima = 0.0


def pedir(ruta, **parametros):
    global _ultima
    espera = _ultima + 1.1 - time.time()
    if espera > 0:
        time.sleep(espera)
    _ultima = time.time()
    url = f"{NOMINATIM}/{ruta}?" + urllib.parse.urlencode(dict(parametros, format="jsonv2"))
    peticion = urllib.request.Request(url, headers={"User-Agent": AGENTE, "Accept-Language": "es"})
    return json.load(urllib.request.urlopen(peticion, timeout=30))


def normalizar(texto):
    texto = unicodedata.normalize("NFD", texto or "")
    return "".join(c for c in texto if unicodedata.category(c) != "Mn").lower()


def distancia_m(lat1, lon1, lat2, lon2):
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return round(2 * r * math.asin(math.sqrt(a)))


def main():
    datos = json.loads((DATOS / "alojamientos_bogota.json").read_text(encoding="utf-8"))
    filas = []
    for a in datos["alojamientos"]:
        lat, lon = a["latitud"], a["longitud"]
        lat_min, lat_max, lon_min, lon_max = AREAS[a["ciudad"]]
        en_bogota = lat_min <= lat <= lat_max and lon_min <= lon <= lon_max
        inversa = pedir("reverse", lat=lat, lon=lon, zoom=18, addressdetails=1)
        direccion_osm = inversa.get("address", {})
        localidad_osm = direccion_osm.get("suburb", "")
        coincide = normalizar(a["localidad"].split(" (")[0]).replace(" ", "") in normalizar(localidad_osm).replace(" ", "")

        consulta = a["direccion"].split(",")[0] + ", " + a["ciudad"]
        resultados = pedir("search", q=consulta + ", Colombia", limit=1, countrycodes="co")
        if resultados:
            dist_dir = distancia_m(lat, lon, float(resultados[0]["lat"]), float(resultados[0]["lon"]))
            resultado_dir = resultados[0]["display_name"]
        else:
            dist_dir, resultado_dir = "", "Nominatim no encontró la dirección con placa"

        oficial = a.get("coordenadas_oficiales_establecimiento")
        dist_oficial = distancia_m(lat, lon, *oficial) if oficial else ""

        if not en_bogota or not coincide:
            veredicto = "REVISAR"
        elif dist_dir != "" and dist_dir > 1500:
            veredicto = "VALIDA (la geocodificación de la dirección cae lejos; prevalece el punto oficial del IDT)"
        else:
            veredicto = "VALIDA"
        filas.append(dict(
            id_demo=a["id_demo"], rnt=a["rnt"], direccion=a["direccion"], estado_direccion=a["estado_direccion"],
            barrio=a["barrio"], localidad_idt=a["localidad"], latitud=lat, longitud=lon,
            ciudad=a["ciudad"], fuente_coordenadas=a["fuentes"][0]["nombre_fuente"], precision=a["precision_coordenadas"],
            dentro_de_la_ciudad="sí" if en_bogota else "no",
            localidad_osm=localidad_osm, localidad_coincide="sí" if coincide else "no",
            via_osm_en_el_punto=direccion_osm.get("road", ""),
            distancia_direccion_geocodificada_m=dist_dir, resultado_geocodificacion=resultado_dir,
            distancia_coordenadas_oficiales_establecimiento_m=dist_oficial,
            veredicto=veredicto, fecha_validacion=time.strftime("%Y-%m-%d")))
        print(f"{a['id_demo']:32} {veredicto:10.10} localidad OSM={localidad_osm!r:28} dir={dist_dir!s:>6} m")

    with open(DATOS / "coordenadas_alojamientos.csv", "w", encoding="utf-8-sig", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(filas[0]))
        escritor.writeheader()
        escritor.writerows(filas)
    revisar = [f["id_demo"] for f in filas if f["veredicto"] == "REVISAR"]
    print(f"{len(filas) - len(revisar)} válidas, {len(revisar)} para revisar {revisar}")


if __name__ == "__main__":
    main()
