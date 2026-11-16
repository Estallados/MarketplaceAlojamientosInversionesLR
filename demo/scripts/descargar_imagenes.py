"""Descarga de Wikimedia Commons las imágenes listadas en demo/datos/alojamientos_bogota.json.

Antes de guardar cada imagen vuelve a consultar en la API de Commons su licencia y su autor, y se detiene si
no coinciden con lo registrado en licencias_imagenes.csv. Descarga la versión de 1600 px de ancho (JPEG, por
debajo del límite de 5 MB del backend) y la guarda en <destino>/<id_demo>/NN-<real|ilustrativa>-<clave>.jpg.
No modifica las imágenes ni quita marcas de agua.

Uso: python demo/scripts/descargar_imagenes.py --destino ../database/Alojamientos
"""

import argparse
import html
import json
import re
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DATOS = Path(__file__).resolve().parent.parent / "datos"
API = "https://commons.wikimedia.org/w/api.php"
AGENTE = "AcademicMarketplaceSeed/1.0 (proyecto academico)"
ANCHO = 1600
TAMANIO_MAXIMO = 5 * 1024 * 1024


def abrir(url):
    for intento in range(5):
        try:
            peticion = urllib.request.Request(url, headers={"User-Agent": AGENTE})
            return urllib.request.urlopen(peticion, timeout=120).read()
        except urllib.error.HTTPError as error:
            if error.code != 429:
                raise
            time.sleep(10 * (intento + 1))
    raise RuntimeError(f"Commons no respondió: {url}")


def metadatos(titulo):
    url = API + "?" + urllib.parse.urlencode(dict(action="query", titles=titulo, prop="imageinfo", format="json",
                                                  iiprop="url|extmetadata|mime", iiurlwidth=ANCHO))
    pagina = next(iter(json.loads(abrir(url))["query"]["pages"].values()))
    info = pagina["imageinfo"][0]
    extra = info["extmetadata"]
    texto = lambda clave: html.unescape(re.sub("<[^>]+>", "", extra.get(clave, {}).get("value", ""))).strip()
    return dict(descarga=info.get("thumburl", info["url"]), licencia=texto("LicenseShortName"), autor=texto("Artist"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--destino", required=True, help="carpeta donde se guardan las imágenes")
    args = parser.parse_args()
    destino = Path(args.destino)
    datos = json.loads((DATOS / "alojamientos_bogota.json").read_text(encoding="utf-8"))
    cache = {}
    for alojamiento in datos["alojamientos"]:
        for imagen in alojamiento["imagenes"]:
            ruta = destino / imagen["archivo"]
            ruta.parent.mkdir(parents=True, exist_ok=True)
            if ruta.exists():
                print(f"ya existe  {ruta}")
                continue
            titulo = imagen["titulo_commons"]
            if titulo not in cache:
                meta = metadatos(titulo)
                if meta["licencia"] != imagen["licencia"] or meta["autor"] != imagen["autor"]:
                    raise SystemExit(f"La licencia o el autor de {titulo} cambiaron en Commons: {meta}")
                contenido = abrir(meta["descarga"])
                if not contenido.startswith(b"\xff\xd8\xff") or len(contenido) > TAMANIO_MAXIMO:
                    raise SystemExit(f"{titulo} no es un JPEG de hasta 5 MB")
                cache[titulo] = ruta
                ruta.write_bytes(contenido)
                time.sleep(1)
            else:
                shutil.copyfile(cache[titulo], ruta)
            print(f"{ruta.stat().st_size // 1024:5d} KB {ruta}")


if __name__ == "__main__":
    main()
