"""Construye los archivos de datos de los alojamientos de Bogotá para la demostración (SCRUM-206 / T18).

Fuente principal: capa oficial "Establecimiento de Alojamiento Turístico. Bogotá" del Instituto Distrital
de Turismo (IDT) e IDECA, publicada en datosabiertos.bogota.gov.co con licencia CC BY-SA 4.0. De ella salen
el número de Registro Nacional de Turismo (RNT), el nombre registrado, la localidad, el sector catastral y las
coordenadas (que el IDT describe como "ubicación aproximada").

La curaduría (dirección, datos del establecimiento, fuentes complementarias e imágenes) está en CURADURIA,
más abajo. Todo dato que no se pudo verificar queda marcado como valor técnico de demostración.

Uso (desde la raíz del repositorio):
    python demo/scripts/construir_datos.py            # descarga la capa del IDT y escribe demo/datos/*
    python demo/scripts/construir_datos.py --idt eatu.geojson   # usa una copia local de la capa

Solo usa la biblioteca estándar de Python 3.10+.
"""

import argparse
import csv
import json
import urllib.request
from pathlib import Path

FECHA_CONSULTA = "2026-11-16"
URL_IDT = ("https://datosabiertos.bogota.gov.co/dataset/316f1588-c5f3-4dc9-b91c-55d82c2e0d75/resource/"
           "cbb4c7f6-80d4-44d4-9835-159b793c0afa/download/eatu.geojson")
URL_FICHA_IDT = "https://datosabiertos.bogota.gov.co/dataset/establecimiento-de-alojamiento-turistico-bogota"
AGENTE = "AcademicMarketplaceSeed/1.0 (proyecto academico)"

DATOS = Path(__file__).resolve().parent.parent / "datos"

# Códigos de localidad de Bogotá (los mismos que usa la capa del IDT).
LOCALIDADES = {
    "1": "Usaquén", "2": "Chapinero", "3": "Santa Fe", "4": "San Cristóbal", "5": "Usme", "6": "Tunjuelito",
    "7": "Bosa", "8": "Kennedy", "9": "Fontibón", "10": "Engativá", "11": "Suba", "12": "Barrios Unidos",
    "13": "Teusaquillo", "14": "Los Mártires", "15": "Antonio Nariño", "16": "Puente Aranda",
    "17": "La Candelaria", "18": "Rafael Uribe Uribe", "19": "Ciudad Bolívar", "20": "Sumapaz",
}

# Subcategoría RNT de la capa. La capa no trae diccionario: el significado se infirió de los nombres de los
# establecimientos de cada código (p. ej. 9 = HOTEL DANN, 8 = SELINA/BOTANICO HOSTEL, 7 = APARTAHOTELES).
SUBCATEGORIAS = {"9": "Hotel (inferido)", "8": "Hostal (inferido)", "7": "Apartamento turístico / apartahotel (inferido)"}

# Precio técnico por segmento: el modelo exige precio por noche y no hay tarifas públicas estables
# (cambian por fecha y canal). NO son precios reales del establecimiento.
PRECIO_TECNICO = {"hostal": 120000, "economico": 220000, "medio": 320000, "alto": 480000, "lujo": 750000}

POLITICA_TECNICA = "MODERADA"  # política existente en la tabla politica_cancelacion (id 2 en 05_seed_data.sql)

REGLAS_TECNICAS = ("Reglas de demostración: respetar las normas internas del establecimiento y los horarios de "
                   "entrada y salida. No es una oferta real.")

AVISO = ("Publicación de demostración académica (Universidad El Bosque): el precio, la disponibilidad y las "
         "reglas son valores de prueba; no es una oferta real ni está afiliada al establecimiento.")

# ---- Imágenes de Wikimedia Commons (licencia y autor consultados con la API de Commons) ---------------
# clasificacion: "real" = fotografía del establecimiento/edificio; "ilustrativa" = no corresponde al inmueble.
IMG = {
    "dann": dict(titulo="File:Hotel Dann Centro.JPG", autor="Felipe Restrepo Acosta", licencia="CC BY-SA 3.0",
                 clasificacion="real", tipo="fachada",
                 nota="Descripción en Commons: Hotel Dann Centro, avenida diecinueve con carrera Quinta."),
    "tequendama_1": dict(titulo="File:Bogotá Hotel Tequendama.jpg", autor="Felipe Restrepo Acosta",
                         licencia="CC BY-SA 3.0", clasificacion="real", tipo="fachada",
                         nota="Costado sur del edificio del Hotel Tequendama (Calle 26 # 10-42), donde opera Four Points by Sheraton Tequendama."),
    "tequendama_2": dict(titulo="File:Hotel Tequendama - Centro Internacional.JPG", autor="Rvsdelfin",
                         licencia="CC BY-SA 3.0", clasificacion="real", tipo="fachada",
                         nota="Edificio del Hotel Tequendama en el Centro Internacional."),
    "ibis_1": dict(titulo="File:Hotel Ibis Bogotá.JPG", autor="Felipe Restrepo Acosta", licencia="CC BY-SA 3.0",
                   clasificacion="real", tipo="fachada",
                   nota="Descripción en Commons: Hotel Ibis en la carrera Séptima con calle 28 (barrio San Diego)."),
    "ibis_2": dict(titulo="File:Bogotá entrada hotel Ibis centro internacional.JPG", autor="Felipe Restrepo Acosta",
                   licencia="CC BY-SA 3.0", clasificacion="real", tipo="entrada",
                   nota="Entrada del hotel Ibis en la calle 28 con carrera 6A, barrio San Diego."),
    "estelar_1": dict(titulo="File:Hotel Estelar La Fontana.JPG", autor="VivianaGarcia010", licencia="CC BY-SA 4.0",
                      clasificacion="real", tipo="fachada", nota="Descripción en Commons: Hotel ESTELAR La Fontana."),
    "estelar_2": dict(titulo="File:HOTEL ESTELAR LA FONTANA - panoramio.jpg", autor="Yuriy Shapiro",
                      licencia="CC BY 3.0", clasificacion="real", tipo="fachada",
                      nota="Coordenadas de la foto 4.704480, -74.043308 (a unos 50 m del punto del IDT)."),
    "casamedina_1": dict(titulo="File:Casa Medina.JPG", autor="Felipe Restrepo Acosta", licencia="CC BY-SA 3.0",
                         clasificacion="real", tipo="fachada",
                         nota="Hotel Casa Medina, carrera 7 entre calles 69A y 70, barrio Emaús."),
    "casamedina_2": dict(titulo="File:Hotel Casa Medina.JPG", autor="Felipe Restrepo Acosta", licencia="CC BY-SA 3.0",
                         clasificacion="real", tipo="fachada", nota="Hotel Casa Medina, calle 70 con carrera 7."),
    "artisan": dict(titulo="File:Bogota Artisan D. C. Hotel.JPG", autor="Felipe Restrepo Acosta",
                    licencia="CC BY-SA 4.0", clasificacion="real", tipo="fachada",
                    nota="Categoría de Commons: Hotels in Bogotá; título: Bogota Artisan D. C. Hotel."),
    "arsalitre": dict(titulo="File:Ar hotel Salitre Hotel Bogota Ar Salitre.jpg", autor="Hotel ar salitre Bogotá",
                      licencia="CC BY-SA 3.0", clasificacion="real", tipo="fachada",
                      nota="Foto del AR Hotel Salitre, hoy DoubleTree by Hilton Bogotá Salitre AR (la foto es anterior al cambio de marca)."),
    # Ilustrativas del entorno (calles cercanas en La Candelaria, no son el establecimiento)
    "candelaria_cl11": dict(titulo="File:Bogotá calle 11 carrera 2.JPG", autor="Felipe Restrepo Acosta",
                            licencia="CC BY-SA 3.0", clasificacion="ilustrativa", tipo="entorno",
                            nota="Calle 11 con carrera 2, La Candelaria. Muestra el entorno, no el establecimiento."),
    "candelaria_cr4": dict(titulo="File:Bog La Candelaria.JPG", autor="Felipe Restrepo Acosta",
                           licencia="CC BY-SA 3.0", clasificacion="ilustrativa", tipo="entorno",
                           nota="Carrera 4 con calle 10, La Candelaria. Muestra el entorno, no el establecimiento."),
    "candelaria_cr1": dict(titulo="File:2019 Bogotá - Carrera 1 entre calles 12 E y 12 F en La Concordia.jpg",
                           autor="Felipe Restrpo Acosta", licencia="CC BY-SA 4.0", clasificacion="ilustrativa",
                           tipo="entorno", nota="Carrera 1 en La Concordia. Muestra el entorno, no el establecimiento."),
    # Ilustrativas de interiores (no corresponden a ningún establecimiento de esta lista)
    "cuarto_a": dict(titulo="File:Cozy hotel room with warm lighting and refreshments on a small table.jpg",
                     autor="Shixart1985", licencia="CC BY 2.0", clasificacion="ilustrativa", tipo="habitación",
                     nota="Habitación de hotel genérica."),
    "cuarto_b": dict(titulo="File:Coffee and infused water on a table near a bed in a hotel room.jpg",
                     autor="Shixart1985", licencia="CC BY 2.0", clasificacion="ilustrativa", tipo="habitación",
                     nota="Habitación de hotel genérica."),
    "cuarto_c": dict(titulo="File:Deluxe Suite bedroom.jpg", autor="PaulPMoroney", licencia="CC BY-SA 4.0",
                     clasificacion="ilustrativa", tipo="habitación",
                     nota="Habitación de un B&B en otro país; solo referencia del tipo de alojamiento."),
    "estudio": dict(titulo="File:Studio apartment in Finland.jpg", autor="Aaaatu", licencia="CC BY-SA 4.0",
                    clasificacion="ilustrativa", tipo="apartaestudio",
                    nota="Apartaestudio genérico en otro país; solo referencia del tipo de alojamiento."),
    # Agregadas para que cada alojamiento tenga 4 imágenes
    "ibis_3": dict(titulo="File:Bogotá Hotel IBIS San Diego cl 27 bis tr 6.JPG", autor="Felipe Restrepo Acosta",
                   licencia="CC BY-SA 3.0", clasificacion="real", tipo="fachada",
                   nota="Hotel IBIS San Diego, calle 27 bis con transversal 6."),
    "casamedina_3": dict(titulo="File:Hotel casa medina-norte.JPG", autor="Cuauhtemoc sanchez fernandez",
                         licencia="CC BY-SA 3.0", clasificacion="real", tipo="fachada",
                         nota="Fachada norte del Hotel Casa Medina (categoría de Commons: Casa Medina Hotel)."),
    "bacata": dict(titulo="File:Edificio Bacatá (20796357625).jpg", autor="Edgar Jiménez from Milton Keynes, UK",
                   licencia="CC BY-SA 2.0", clasificacion="ilustrativa", tipo="entorno",
                   nota="Edificio Bacatá, a unos 40 m del punto del IDT del hotel. Muestra el entorno, no el establecimiento."),
    "candelaria_cl12": dict(titulo="File:Bogota calle 12 con carrera 7 - La Candelaria.JPG",
                            autor="Felipe Restrepo Acosta", licencia="CC BY-SA 4.0", clasificacion="ilustrativa",
                            tipo="entorno", nota="Calle 12 con carrera 7, La Candelaria. Muestra el entorno, no el establecimiento."),
    "chapinero_cr7": dict(titulo="File:2021 Bogotá - Balcón en carrera 7 con calle 65.jpg", autor="Felipe Restrpo Acosta",
                          licencia="CC BY-SA 4.0", clasificacion="ilustrativa", tipo="entorno",
                          nota="Carrera 7 con calle 65, Chapinero. Muestra el entorno, no el establecimiento."),
    "usaquen_cl127": dict(titulo="File:BOG calle 127 con carrera 19 Bogota.JPG", autor="Felipe Restrepo Acosta",
                          licencia="CC BY-SA 4.0", clasificacion="ilustrativa", tipo="entorno",
                          nota="Calle 127 con carrera 19, Usaquén. Muestra el entorno, no el establecimiento."),
    "cuarto_d": dict(titulo="File:Caravelle Saigon - Signature Studio Room.jpg", autor="BaonguyenCaravelas",
                     licencia="CC BY-SA 4.0", clasificacion="ilustrativa", tipo="habitación",
                     nota="Habitación de un hotel en otro país; solo referencia del tipo de alojamiento."),
    "cuarto_e": dict(titulo="File:Hotel Kahve1.jpg", autor="Coocentral", licencia="CC BY-SA 4.0",
                     clasificacion="ilustrativa", tipo="habitación", nota="Habitación de hotel genérica."),
    "bano": dict(titulo="File:Bathrobes!.jpg", autor="Artur Bergman", licencia="CC BY-SA 2.0",
                 clasificacion="ilustrativa", tipo="baño", nota="Baño de hotel genérico."),
    # Medellín
    "nutibara_1": dict(titulo="File:Hotel Nutibara-Fachada-Medellin.JPG", autor="SajoR", licencia="Public domain",
                       clasificacion="real", tipo="fachada", nota="Descripción en Commons: Hotel Nutibara, Medellín."),
    "nutibara_2": dict(titulo="File:Hotel Nutibara-entrada.jpg", autor="einalem", licencia="CC BY-SA 2.0",
                       clasificacion="real", tipo="entrada", nota="Acceso al Hotel Nutibara, Medellín."),
    "nutibara_3": dict(titulo="File:Hotel Nutibara.jpg", autor="Luis Perez", licencia="CC BY 2.0",
                       clasificacion="real", tipo="fachada", nota="Hotel Nutibara, Medellín."),
    "danncarlton_med": dict(titulo="File:Hotel Dann Carlton-Medellin.JPG", autor="SajoR", licencia="CC BY-SA 2.5",
                            clasificacion="real", tipo="fachada",
                            nota="Hotel Dann Carlton, Avenida El Poblado, Medellín."),
    "fourpoints_med_1": dict(titulo="File:Four Points Medellín 1.JPG", autor="XalD", licencia="CC BY-SA 3.0",
                             clasificacion="real", tipo="fachada", nota="Four Points Medellín, exterior."),
    "fourpoints_med_2": dict(titulo="File:Four Points Medellín 2.JPG", autor="XalD", licencia="CC BY-SA 3.0",
                             clasificacion="real", tipo="fachada", nota="Four Points Medellín, exterior."),
    "estudio_2": dict(titulo="File:Studio Apartment.jpg", autor="Centre for Digital Media", licencia="CC BY-SA 4.0",
                      clasificacion="ilustrativa", tipo="apartaestudio",
                      nota="Apartaestudio genérico; solo referencia del tipo de alojamiento."),
}

LICENCIAS_URL = {
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
    "CC BY 3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC BY 2.0": "https://creativecommons.org/licenses/by/2.0/",
    "CC BY-SA 2.0": "https://creativecommons.org/licenses/by-sa/2.0/",
    "CC BY-SA 2.5": "https://creativecommons.org/licenses/by-sa/2.5/",
    "Public domain": "https://commons.wikimedia.org/wiki/Commons:Licensing#Public_domain",
}

# ---- Curaduría ----------------------------------------------------------------------------------------
# f(url, dato) = fuente complementaria. "comercial" son agregadores/plataformas de reserva consultados solo
# como referencia pública; "oficial_establecimiento" es el sitio de la cadena u hotel.
def f(tipo, nombre, url, dato):
    return dict(tipo_fuente=tipo, nombre_fuente=nombre, url=url, dato_respaldado=dato)


CURADURIA = [
    dict(rnt=396, slug="hotel-dann-avenida-19", nombre="Hotel Dann Avenida 19", tipo="Hotel", segmento="medio",
         direccion="Avenida Calle 19 # 5-72", estado_direccion="coincide en varias fuentes comerciales; el sitio oficial no la muestra",
         hechos="Hotel del centro de Bogotá con 129 habitaciones de 5 tipos, según la cadena Dann.",
         hora_entrada="15:00", hora_salida="13:00", origen_horas="fuentes comerciales",
         amenidades=["Parqueadero"], imagenes=["dann", "cuarto_a", "bacata", "cuarto_c"],
         fuentes=[f("oficial_establecimiento", "Hoteles Dann", "https://hotelesdann.com/avenida19/en/rooms-in-downtown-bogota/", "129 habitaciones, 5 tipos"),
                  f("comercial", "Kayak", "https://www.es.kayak.com/Bogota-Hoteles-Dann-Avenida-19.72038.ksp", "dirección Av. 19 # 5-72"),
                  f("comercial", "Skyscanner", "https://www.espanol.skyscanner.com/cl/es-es/clp/hoteles/colombia/bogota-hoteles/hotel-dann-avenida-19-bogota/ht-130757442", "check-in 15:00, check-out 13:00; parqueadero")]),
    dict(rnt=407, slug="four-points-tequendama", nombre="Four Points by Sheraton Tequendama", tipo="Hotel", segmento="alto",
         direccion="Calle 26 # 10-42", estado_direccion="publicada por Marriott (sitio oficial) y confirmada por otras fuentes",
         hechos="Funciona en el edificio del histórico Hotel Tequendama, en el Centro Internacional; un listado indica 176 habitaciones en 17 pisos.",
         amenidades=[], imagenes=["tequendama_1", "tequendama_2", "cuarto_b"],
         nota_idt="El IDT tiene dos registros para este hotel (RNT 407 y 72979) en el mismo punto; se usa el 407.",
         fuentes=[f("oficial_establecimiento", "Marriott", "https://www.marriott.com/es/hotels/bogpt-four-points-tequendama-bogota/photos/", "dirección Calle 26 # 10-42"),
                  f("comercial", "Hotels.com", "https://ca.hotels.com/ho3653934592/", "176 habitaciones, 17 pisos")]),
    dict(rnt=21638, slug="ibis-bogota-museo", nombre="ibis Bogotá Museo", tipo="Hotel", segmento="economico",
         direccion="Transversal 6 # 27-85", estado_direccion="coincide en Accor (oficial) y fuentes comerciales",
         hechos="Hotel del Centro Internacional, a unos 110 m del Museo Nacional; habitaciones estándar de unos 18 m², algunas accesibles.",
         hora_entrada="15:00", hora_salida="12:00", origen_horas="fuentes comerciales",
         amenidades=["Parqueadero", "Admite mascotas"], imagenes=["ibis_1", "ibis_2", "cuarto_a", "ibis_3"],
         coordenadas_oficiales_establecimiento=(4.614176, -74.068794),
         fuentes=[f("oficial_establecimiento", "Accor", "https://all.accor.com/hotel/7318/index.es.shtml", "dirección, GPS 4.614176,-74.068794, habitaciones de 18 m²"),
                  f("comercial", "Kayak", "https://www.es.kayak.com/Bogota-Hoteles-Ibis-Bogota-Museo.350652.ksp", "parqueadero gratuito, admite mascotas, horarios")]),
    dict(rnt=54250, slug="selina-la-candelaria", nombre="Selina La Candelaria (Socialtel Candelaria)", tipo="Habitación", segmento="hostal",
         direccion="Calle 11 # 3-43, La Candelaria", estado_direccion="aproximada: las fuentes comerciales no coinciden en la numeración (Calle 11 # 3-43 / Calle 11 n.º 343)",
         hechos="Hostal en una casona del centro histórico con 36 habitaciones entre camas en dormitorio compartido, dobles, twin y suites. Algunos portales ya lo listan como Socialtel Candelaria.",
         unidad="Habitación doble privada en hostal", hora_entrada="15:00", hora_salida="11:00", origen_horas="fuentes comerciales",
         amenidades=["Wifi"], imagenes=["candelaria_cl11", "cuarto_c"],
         fuentes=[f("comercial", "Ebookers/Expedia", "https://ebookers.ch/en/Bogota-Hotels-Selina-La-Candelaria-Bogota.h19108093.Hotel-Information", "36 habitaciones, tipos de habitación, horarios"),
                  f("comercial", "Booking.com", "https://sp.booking.com/hotel/co/selina-bogota.en-gb.html", "wifi gratuito; dirección en Calle 11")]),
    dict(rnt=53966, slug="botanico-hostel", nombre="Botánico Hostel", tipo="Habitación", segmento="hostal",
         direccion="Carrera 2 # 9-87, La Candelaria", estado_direccion="coincide en Hostelworld, Expedia y Kayak",
         hechos="Hostal en una casona colonial del barrio histórico de La Candelaria, cerca de museos y de la Plaza de Bolívar.",
         unidad="Habitación privada en hostal", amenidades=[], imagenes=["candelaria_cl11", "cuarto_b", "candelaria_cr4", "cuarto_e"],
         fuentes=[f("comercial", "Hostelworld", "https://www.hostelworld.com/hostels/p/279935/botanico-hostel-bogota/", "dirección y casona colonial"),
                  f("comercial", "Kayak", "https://www.kayak.com/Bogota-Hotels-Botanico-Hostel.3012590.ksp", "dirección")]),
    dict(rnt=18662, slug="hotel-casa-deco", nombre="Hotel Casa Deco", tipo="Hotel", segmento="medio",
         direccion="Calle 12C # 2-36, La Candelaria", estado_direccion="coincide en varias fuentes comerciales",
         hechos="Hotel art déco en La Candelaria, cerca del Museo del Oro; ofrece habitación doble ejecutiva para 2 personas, wifi y recepción 24 horas; establecimiento para no fumadores.",
         hora_entrada="14:30", hora_salida="12:00", origen_horas="fuentes comerciales (hay una fuente con horario distinto)",
         amenidades=["Wifi"], imagenes=["candelaria_cr1", "cuarto_a"],
         fuentes=[f("comercial", "Hoteles.com", "https://co.hoteles.com/ho336598/hotel-casa-deco-bogota-colombia", "dirección, habitación doble ejecutiva (2 personas), wifi"),
                  f("comercial", "Frommer's", "https://www.frommers.com/destinations/bogota/hotels/hotel-casa-deco", "ubicación en La Candelaria")]),
    dict(rnt=5029, slug="hotel-de-la-opera", nombre="Hotel de la Ópera", tipo="Hotel", segmento="alto",
         direccion="Calle 10 # 5-72, La Candelaria", estado_direccion="coincide en varias fuentes",
         hechos="Hotel de 42 habitaciones en dos casas coloniales y una mansión art déco junto al Teatro Colón; habitaciones con baño privado, calefacción, wifi y televisión por cable.",
         hora_entrada="15:00", hora_salida="12:30", origen_horas="fuentes comerciales",
         amenidades=["Wifi", "Televisor"], imagenes=["candelaria_cr4", "cuarto_c", "candelaria_cl12", "bano"],
         fuentes=[f("comercial", "AFAR", "https://www.afar.com/places/hotel-de-la-opera-bogota", "42 habitaciones en casas coloniales"),
                  f("comercial", "Atrápalo", "https://www.atrapalo.com/hoteles/28249-0_hotel-de-la-opera", "baño privado, wifi, TV por cable")]),
    dict(rnt=41325, slug="four-seasons-casa-medina", nombre="Four Seasons Hotel Casa Medina Bogotá", tipo="Hotel", segmento="lujo",
         direccion="Avenida Carrera 7 # 69A-22", estado_direccion="publicada en la ficha de prensa oficial de Four Seasons",
         hechos="Hotel de estilo colonial en la Zona G con 62 alojamientos (43 habitaciones y 19 suites), según la ficha oficial.",
         amenidades=[], imagenes=["casamedina_1", "casamedina_2", "cuarto_c", "casamedina_3"],
         fuentes=[f("oficial_establecimiento", "Four Seasons (prensa)", "https://press.fourseasons.com/casamedina/hotel-facts/", "dirección, 43 habitaciones y 19 suites"),
                  f("comercial", "American Express Travel", "https://www.americanexpress.com/es-es/travel/discover/property/Colombia/Bogotá/Four-Seasons-Hotel-Casa-Medina-Bogota", "dirección Carrera 7 No 69a-22")]),
    dict(rnt=36729, slug="the-artisan-dc", nombre="The Artisan D.C. Hotel", tipo="Hotel", segmento="lujo",
         direccion="Avenida Calle 72 # 5-51", estado_direccion="coincide en Visit Bogotá y fuentes comerciales",
         hechos="Hotel de la línea Autograph Collection de Marriott en el sector de la Zona G; la cifra más citada es de 64 habitaciones con minibar y caja fuerte.",
         amenidades=[], imagenes=["artisan", "cuarto_b", "chapinero_cr7", "cuarto_d"],
         fuentes=[f("oficial", "Visit Bogotá (IDT)", "https://files.visitbogota.co/drpl/en/node/702", "dirección Av. Calle 72 Nº 5-51"),
                  f("comercial", "Kayak", "https://www.es.kayak.com/Bogota-Hoteles-The-Artisan-D-C-Hotel-Autograph-Collection.803601.ksp", "64 habitaciones, Chapinero")]),
    dict(rnt=21160, slug="jw-marriott-bogota", nombre="JW Marriott Hotel Bogotá", tipo="Hotel", segmento="lujo",
         direccion="Calle 73 # 8-60", estado_direccion="publicada por Marriott y confirmada por Visit Bogotá",
         hechos="Hotel de 264 habitaciones y suites en Chapinero, según Visit Bogotá y Cvent.",
         amenidades=[], imagenes=["cuarto_a", "cuarto_c"],
         fuentes=[f("oficial", "Visit Bogotá (IDT)", "https://files.visitbogota.co/drpl/en/node/473", "264 habitaciones y suites"),
                  f("oficial_establecimiento", "Marriott", "https://www.marriott.com/es/redirectwte/BOGJW", "dirección Calle 73 # 8-60")]),
    dict(rnt=148297, slug="viaggio-urbano", nombre="Viaggio Urbano", tipo="Apartamento", segmento="medio",
         direccion="Transversal 4 # 43-95, Chapinero", estado_direccion="coincide en Booking.com, Foursquare y otros listados",
         hechos="Apartahotel de apartamentos amoblados en Chapinero; ofrece estudios, suites y apartamentos.",
         unidad="Apartaestudio amoblado", habitaciones=0, amenidades=[], imagenes=["estudio", "cuarto_b", "estudio_2", "cuarto_a"],
         fuentes=[f("comercial", "Booking.com", "https://www.booking.com/hotel/co/viaggio-apartamentos-amoblados.html", "dirección, tipos Studio/Suite/Apartment"),
                  f("comercial", "Frommer's", "https://www.frommers.com/destinations/bogota/hotels/viaggio-urbano-apartamentos-amoblados", "apartamentos amoblados")]),
    dict(rnt=194992, slug="estelar-la-fontana", nombre="Hotel Estelar La Fontana", tipo="Hotel", segmento="alto",
         direccion="Avenida Calle 127 # 15A-10", estado_direccion="coincide en varias fuentes",
         hechos="Hotel del norte de Bogotá, cerca del centro comercial Unicentro, con parqueadero y wifi.",
         hora_entrada="15:00", hora_salida="13:00", origen_horas="fuentes comerciales",
         amenidades=["Parqueadero", "Wifi"], imagenes=["estelar_1", "estelar_2", "cuarto_a", "usaquen_cl127"],
         fuentes=[f("oficial", "Visit Bogotá (IDT)", "https://files.visitbogota.co/drpl/en/node/468", "ficha del hotel"),
                  f("comercial", "Trabber", "https://www.trabber.us/es/hoteles/colombia-co/bogota-3688689/hotel-estelar-la-fontana-124092485/", "dirección Av. 127 # 15A-10"),
                  f("comercial", "Kayak", "https://www.es.kayak.com/Bogota-Hoteles-Hotel-Estelar-La-Fontana.8064.ksp", "wifi gratis, parqueadero, horarios")]),
    dict(rnt=39735, slug="hampton-usaquen", nombre="Hampton by Hilton Bogotá Usaquén", tipo="Hotel", segmento="medio",
         direccion="Carrera 7 # 118-30, Usaquén", estado_direccion="publicada en el sitio oficial de Hilton",
         hechos="Hotel cerca del centro comercial Hacienda Santa Bárbara; Visit Bogotá indica unas 100 habitaciones.",
         amenidades=[], imagenes=["cuarto_b", "cuarto_a"],
         fuentes=[f("oficial_establecimiento", "Hilton", "https://www.hilton.com/es/hotels/bogushx-hampton-bogota-usaquen/", "dirección Carrera 7 n.º 118-30"),
                  f("oficial", "Visit Bogotá (IDT)", "https://visitbogota.co/en/mice/locacion/hotel-hampton-by-hilton-bogota---usaquen-463", "unas 100 habitaciones")]),
    dict(rnt=21242, slug="doubletree-salitre-ar", nombre="DoubleTree by Hilton Bogotá Salitre AR", tipo="Hotel", segmento="alto",
         direccion="Avenida Carrera 60 # 22-99", estado_direccion="publicada por Hilton y confirmada por su comunicado de prensa",
         hechos="Hotel de 199 habitaciones en el sector de Salitre, de propiedad de Grupo AR (antes AR Hotel Salitre).",
         amenidades=[], imagenes=["arsalitre", "cuarto_c", "cuarto_d", "bano"],
         fuentes=[f("oficial_establecimiento", "Hilton (prensa)", "https://stories.hilton.com/releases/third-doubletree-by-hilton-hotel-in-bogota-grows-latin-america-presence", "199 habitaciones, Salitre, Grupo AR"),
                  f("comercial", "Cvent", "https://www.cvent.com/venues/es-ES/bogota/hotel/doubletree-by-hilton-bogota-salitre-ar/venue-bf99f713-dd70-4595-b2c1-426577089c5d", "dirección y habitaciones")]),
    dict(rnt=63818, slug="hilton-corferias", nombre="Hilton Bogotá Corferias", tipo="Hotel", segmento="alto",
         direccion="Carrera 37 # 24-29", estado_direccion="coincide en Cvent y otras fuentes",
         hechos="Hotel dentro del recinto ferial de Corferias, conectado al centro de convenciones Ágora; habitaciones con aire acondicionado.",
         amenidades=["Aire acondicionado"], imagenes=["cuarto_a", "cuarto_b"],
         fuentes=[f("oficial_establecimiento", "Hilton", "https://hilton.com/en/hotels/bogcchh-hilton-bogota-corferias", "ficha del hotel"),
                  f("comercial", "Rumbo", "https://www.rumbo.es/hoteles/colombia/bogota/hilton-bogota-corferias_hid-6949754", "habitaciones con aire acondicionado"),
                  f("comercial", "Cvent", "https://www.cvent.com/venues/mexico/hotel/hilton-bogota-corferias/venue-ec8defd2-1420-4634-adca-1dfd87fb3184", "dirección Cr 37 24-29")]),
    dict(rnt=54132, slug="hilton-garden-inn-airport", nombre="Hilton Garden Inn Bogotá Airport", tipo="Hotel", segmento="medio",
         direccion="Carrera 82 # 25G-84, Fontibón", estado_direccion="coincide en varias fuentes",
         hechos="Hotel en Fontibón, cerca de la Avenida El Dorado y a unos 4 km del aeropuerto; habitaciones con cama king o dos camas y televisor HD.",
         amenidades=["Televisor"], imagenes=["cuarto_b", "cuarto_c"],
         fuentes=[f("oficial_establecimiento", "Hilton", "https://www.hilton.com/en/hotels/bogatgi-hilton-garden-inn-bogota-airport/hotel-info", "ficha del hotel"),
                  f("comercial", "Trabber", "https://www.trabber.us/es/hoteles/colombia-co/bogota-3688689/hilton-garden-inn-bogota-airport-124433906/", "dirección Carrera 82 No. 25G 84"),
                  f("comercial", "Hotels by Day", "https://www.hotelsbyday.com/es/hotels/colombia/bogota/hilton-garden-inn-bogota-airport", "tipos de habitación, TV HD")]),
    dict(rnt=46936, slug="city-express-plus-aeropuerto", nombre="City Express Plus by Marriott Bogotá Aeropuerto", tipo="Hotel", segmento="economico",
         direccion="Diagonal 25G # 95-66, Fontibón", estado_direccion="coincide en varias fuentes",
         hechos="Hotel de unas 120 habitaciones de 22 m² con aire acondicionado, televisor, wifi y baño privado; traslado gratuito al aeropuerto El Dorado.",
         amenidades=["Aire acondicionado", "Televisor", "Wifi"], imagenes=["cuarto_a", "cuarto_b", "cuarto_e", "cuarto_c"],
         fuentes=[f("oficial_establecimiento", "Marriott", "https://www.marriott.com/es/hotels/bogpo-city-express-plus-bogota-aeropuerto/", "ficha del hotel, traslado al aeropuerto"),
                  f("comercial", "Rumbo", "https://www.rumbo.es/hoteles/colombia/bogota/city-express-plus-by-marriott-bogota-aeropuerto_hid-4170514", "120 habitaciones, 22 m², AC, TV, wifi")]),
    dict(rnt=25609, slug="aloft-bogota-airport", nombre="Aloft Bogotá Airport", tipo="Hotel", segmento="medio",
         direccion="Avenida Calle 26 # 92-32", estado_direccion="coincide en varias fuentes",
         hechos="Hotel de 138 habitaciones tipo loft con elementos tecnológicos, cerca del aeropuerto El Dorado.",
         amenidades=[], imagenes=["cuarto_c", "cuarto_a"],
         fuentes=[f("oficial_establecimiento", "Marriott", "https://www.marriott.com/en-us/hotels/bogal-aloft-bogota-airport/overview/", "ficha del hotel"),
                  f("oficial", "Visit Bogotá (IDT)", "https://files.visitbogota.co/drpl/en/node/498", "138 habitaciones"),
                  f("comercial", "Kayak", "https://www.es.kayak.com/Bogota-Hoteles-Aloft-Bogota-Airport.653112.ksp", "dirección Av. Calle 26 # 92-32")]),
    dict(rnt=166508, slug="bliss-boutique-hotel", nombre="Bliss Boutique Hotel Bogotá", tipo="Hotel", segmento="medio",
         direccion="Calle 98A # 49-29, La Castellana", estado_direccion="coincide en varias fuentes comerciales",
         hechos="Hotel boutique en La Castellana, cerca del Teatro Nacional La Castellana; ofrece habitación doble superior y desayuno.",
         hora_salida="13:00", origen_horas="salida: fuentes comerciales; entrada: valor técnico (las fuentes no coinciden)",
         amenidades=[], imagenes=["cuarto_c", "cuarto_b"],
         fuentes=[f("comercial", "Expedia", "https://www.expedia.com.sg/h103454585.Hotel-Information", "dirección, habitación doble superior, desayuno"),
                  f("comercial", "Skyscanner", "https://www.skyscanner.ie/hotels/colombia/bogota-hotels/bliss-boutique-hotel-bogota/ht-216441421", "horarios")]),
]

# Segunda ciudad (criterio de T18: publicaciones ACTIVAS en 2 ciudades). Sin una capa oficial equivalente a la
# del IDT, la ubicación sale del objeto tourism=hotel de OpenStreetMap (ODbL), que es además el proveedor de
# mapas y geocodificación del Marketplace.
URL_OSM = "https://www.openstreetmap.org/"
CURADURIA_MEDELLIN = [
    dict(slug="hotel-nutibara-medellin", nombre="Hotel Nutibara", tipo="Hotel", segmento="medio", ciudad="Medellín",
         direccion="Calle 52A # 50-46", estado_direccion="coincide en varias fuentes comerciales",
         barrio="La Candelaria", localidad="La Candelaria (Comuna 10)", osm="node/358110291",
         latitud=6.2523954, longitud=-75.5672716,
         hechos="Hotel tradicional del centro de Medellín; un listado indica 128 habitaciones.",
         hora_entrada="15:00", hora_salida="13:00", origen_horas="fuentes comerciales",
         amenidades=[], imagenes=["nutibara_1", "nutibara_2", "nutibara_3", "cuarto_e"],
         fuentes=[f("comercial", "Rumbo", "https://www.rumbo.es/hoteles/colombia/medellin/hotel-nutibara_hid-160799", "dirección, 128 habitaciones"),
                  f("comercial", "Booking.com", "https://booking.com/hotel/co/nutibara.html", "horarios de entrada y salida")]),
    dict(slug="dann-carlton-medellin", nombre="Hotel Dann Carlton Medellín", tipo="Hotel", segmento="alto", ciudad="Medellín",
         direccion="Carrera 43A # 7-50, El Poblado", estado_direccion="coincide en varias fuentes comerciales",
         barrio="El Poblado", localidad="El Poblado (Comuna 14)", osm="way/348692051",
         latitud=6.2072796, longitud=-75.5707251,
         hechos="Hotel sobre la Avenida El Poblado; un listado indica 200 habitaciones entre estándar y suites.",
         hora_entrada="15:00", hora_salida="12:00", origen_horas="fuentes comerciales",
         amenidades=[], imagenes=["danncarlton_med", "cuarto_a", "bano", "cuarto_d"],
         fuentes=[f("comercial", "Kayak", "https://www.es.kayak.com/Medellin-Hoteles-Hotel-Dann-Carlton-Medellin.160562.ksp", "dirección, 200 habitaciones"),
                  f("comercial", "Skyscanner", "https://www.espanol.skyscanner.com/hoteles/colombia/medellin-hoteles/hotel-dann-carlton-medellin/ht-46952382", "horarios")]),
    dict(slug="four-points-medellin", nombre="Four Points by Sheraton Medellín", tipo="Hotel", segmento="medio", ciudad="Medellín",
         direccion="Carrera 43C # 6 Sur-100, El Poblado", estado_direccion="publicada por Marriott (sitio oficial) y confirmada por otras fuentes",
         barrio="La Aguacatala", localidad="El Poblado (Comuna 14)", osm="node/13410598701",
         latitud=6.1993474, longitud=-75.5760580,
         hechos="Hotel de 123 habitaciones (86 clásicas, 28 ejecutivas, 6 junior suites y 3 suites), según Cvent.",
         amenidades=[], imagenes=["fourpoints_med_1", "fourpoints_med_2", "cuarto_b", "bano"],
         fuentes=[f("oficial_establecimiento", "Marriott", "https://www.marriott.com/es/hotels/mdefp-four-points-medellin/photos/", "dirección Carrera 43 C #6 Sur 100"),
                  f("comercial", "Cvent", "https://www.cvent.com/venues/es-ES/medellin/hotel/four-points-by-sheraton-medellin/venue-16e18d03-687d-4ceb-9bbc-828b11fa12e0", "123 habitaciones y su distribución")]),
]

# El equipo pidió cargar 10 alojamientos. Se eligieron los que tienen más fotos reales y más variedad de
# localidades y tipos; los demás de CURADURIA quedan verificados pero no se cargan (ver "no_cargados").
SELECCION = [
    "hotel-dann-avenida-19", "ibis-bogota-museo", "hotel-de-la-opera", "botanico-hostel", "four-seasons-casa-medina",
    "the-artisan-dc", "viaggio-urbano", "estelar-la-fontana", "doubletree-salitre-ar", "city-express-plus-aeropuerto",
    # Segunda ciudad
    "hotel-nutibara-medellin", "dann-carlton-medellin", "four-points-medellin",
]

EXCLUIDOS = [
    dict(nombre="1177 Hotel Boutique (RNT 135206, Suba)", motivo="No se encontró dirección ni datos públicos verificables."),
    dict(nombre="Hostal Toscana (RNT 34334, Suba)", motivo="No se encontró dirección ni datos públicos verificables."),
    dict(nombre="Hotel Estación de la Sabana (RNT 99406, Los Mártires)", motivo="Las búsquedas devuelven otros hoteles con nombres parecidos; no se pudo confirmar."),
    dict(nombre="Fotos 'Hotel Marriott Bogota' de Commons", motivo="Sus coordenadas (-74.108) corresponden al Bogotá Marriott de la Av. El Dorado, no al JW Marriott de la Calle 73."),
]


def url_commons(titulo):
    return "https://commons.wikimedia.org/wiki/" + titulo.replace(" ", "_")


def construir(idt):
    alojamientos = []
    for c in CURADURIA + CURADURIA_MEDELLIN:
        p = idt[c["rnt"]] if "rnt" in c else None
        unidad = c.get("unidad", "Habitación doble")
        tecnicos = ["precio_noche", "politica_cancelacion", "reglas", "periodo_disponibilidad", "capacidad",
                    "habitaciones", "banos"]
        hora_entrada = c.get("hora_entrada", "15:00")
        hora_salida = c.get("hora_salida", "12:00")
        if "hora_entrada" not in c:
            tecnicos.append("hora_entrada")
        if "hora_salida" not in c:
            tecnicos.append("hora_salida")
        imagenes = []
        for orden, clave in enumerate(c["imagenes"]):
            i = IMG[clave]
            imagenes.append(dict(
                clave=clave, orden=orden, principal=orden == 0,
                archivo=f"{c['slug']}/{orden + 1:02d}-{i['clasificacion']}-{clave}.jpg",
                titulo_commons=i["titulo"], url_original=url_commons(i["titulo"]), autor=i["autor"],
                fuente="Wikimedia Commons", licencia=i["licencia"], licencia_url=LICENCIAS_URL[i["licencia"]],
                clasificacion=i["clasificacion"], tipo_imagen=i["tipo"], nota=i["nota"],
                fecha_consulta=FECHA_CONSULTA))
        creditos = " ".join(
            f"Foto {im['orden'] + 1} ({'del establecimiento' if im['clasificacion'] == 'real' else 'ilustrativa, no es el inmueble'}): "
            f"{im['autor']}, {im['licencia']}, Wikimedia Commons." for im in imagenes)
        if p:
            localidad, barrio, ciudad = LOCALIDADES[p["LOCALIDAD"]], p["SECCATASTR"].title(), "Bogotá"
            latitud, longitud = round(p["LATITUD"], 7), round(p["LONGITUD"], 7)
            precision = "aproximada (punto oficial del IDT; el IDT la describe como ubicación aproximada)"
            origen = (f"establecimiento con Registro Nacional de Turismo {p['REGNTURISM']} en el registro de alojamiento "
                      f"turístico del IDT (sector {barrio}, localidad de {localidad})")
            fuente_ubicacion = f("oficial", "IDT / IDECA - Establecimiento de Alojamiento Turístico. Bogotá (CC BY-SA 4.0)",
                                 URL_FICHA_IDT, "RNT, nombre registrado, subcategoría, localidad, sector y coordenadas")
        else:
            localidad, barrio, ciudad = c["localidad"], c["barrio"], c["ciudad"]
            latitud, longitud = c["latitud"], c["longitud"]
            precision = f"del objeto tourism=hotel de OpenStreetMap ({c['osm']})"
            origen = f"hotel de {ciudad} ubicado en {barrio}, {localidad}"
            fuente_ubicacion = f("osm", "OpenStreetMap (ODbL)", URL_OSM + c["osm"], "coordenadas y comuna del hotel")
        descripcion = f"{unidad} en {c['nombre']}, {origen}. {c['hechos']} {AVISO} {creditos}"
        alojamientos.append(dict(
            id_demo=c["slug"],
            titulo=f"{unidad} en {c['nombre']}",
            nombre_establecimiento=c["nombre"],
            nombre_registro_idt=p["NOMESTABLE"] if p else None,
            rnt=p["REGNTURISM"] if p else None,
            subcategoria_rnt=p["SUBCATRNT"] if p else None,
            tipo_establecimiento=SUBCATEGORIAS.get(p["SUBCATRNT"], "No determinado") if p else "Hotel",
            tipo=c["tipo"],
            unidad_publicada=unidad,
            descripcion=descripcion,
            direccion=c["direccion"],
            estado_direccion=c["estado_direccion"],
            barrio=barrio,
            localidad=localidad,
            ciudad=ciudad,
            pais="Colombia",
            latitud=latitud,
            longitud=longitud,
            precision_coordenadas=precision,
            capacidad=2,
            habitaciones=c.get("habitaciones", 1),
            camas=None,
            banos=1,
            hora_entrada=hora_entrada,
            hora_salida=hora_salida,
            origen_horas=c.get("origen_horas", "valor técnico de demostración"),
            precio_noche=PRECIO_TECNICO[c["segmento"]],
            segmento_precio_tecnico=c["segmento"],
            politica_cancelacion=POLITICA_TECNICA,
            reglas=REGLAS_TECNICAS,
            amenidades=c["amenidades"],
            servicios_adicionales=[],
            estado_objetivo="ACTIVA",
            datos_tecnicos=tecnicos,
            nota_idt=c.get("nota_idt"),
            coordenadas_oficiales_establecimiento=c.get("coordenadas_oficiales_establecimiento"),
            fuentes=[fuente_ubicacion] + c["fuentes"],
            imagenes=imagenes,
            fecha_consulta=FECHA_CONSULTA,
        ))
    return alojamientos


def escribir_csv(ruta, filas, campos):
    with open(ruta, "w", encoding="utf-8-sig", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=campos, extrasaction="ignore")
        escritor.writeheader()
        escritor.writerows(filas)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--idt", help="ruta local de eatu.geojson; si no se da, se descarga del portal")
    args = parser.parse_args()
    if args.idt:
        geojson = json.loads(Path(args.idt).read_text(encoding="utf-8"))
    else:
        peticion = urllib.request.Request(URL_IDT, headers={"User-Agent": AGENTE})
        geojson = json.load(urllib.request.urlopen(peticion, timeout=120))
    idt = {f["properties"]["REGNTURISM"]: f["properties"] for f in geojson["features"]}

    todos = construir(idt)
    alojamientos = [a for a in todos if a["id_demo"] in SELECCION]
    no_cargados = [dict(id_demo=a["id_demo"], rnt=a["rnt"], nombre=a["nombre_establecimiento"],
                        localidad=a["localidad"], direccion=a["direccion"],
                        motivo="Verificado, pero fuera de la selección de 10 alojamientos para la demostración.")
                   for a in todos if a["id_demo"] not in SELECCION]
    DATOS.mkdir(parents=True, exist_ok=True)
    (DATOS / "alojamientos_bogota.json").write_text(json.dumps(dict(
        descripcion="Alojamientos reales de Bogotá para la demostración del Marketplace (SCRUM-206 / T18).",
        fecha_consulta=FECHA_CONSULTA,
        fuente_principal=dict(nombre="Establecimiento de Alojamiento Turístico. Bogotá (IDT / IDECA)",
                              url=URL_FICHA_IDT, licencia="CC BY-SA 4.0"),
        aviso=AVISO,
        excluidos=EXCLUIDOS,
        no_cargados=no_cargados,
        alojamientos=alojamientos), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    filas = [dict(a, amenidades="; ".join(a["amenidades"]), datos_tecnicos="; ".join(a["datos_tecnicos"]),
                  imagenes=len(a["imagenes"])) for a in alojamientos]
    escribir_csv(DATOS / "alojamientos_bogota.csv", filas,
                 ["id_demo", "rnt", "nombre_establecimiento", "titulo", "tipo", "unidad_publicada", "direccion",
                  "barrio", "localidad", "ciudad", "pais", "latitud", "longitud", "capacidad", "habitaciones", "camas",
                  "banos", "hora_entrada", "hora_salida", "precio_noche", "politica_cancelacion", "amenidades",
                  "estado_objetivo", "imagenes", "datos_tecnicos"])
    escribir_csv(DATOS / "fuentes_alojamientos.csv",
                 [dict(fu, id_demo=a["id_demo"], rnt=a["rnt"], fecha_consulta=FECHA_CONSULTA)
                  for a in alojamientos for fu in a["fuentes"]],
                 ["id_demo", "rnt", "tipo_fuente", "nombre_fuente", "url", "dato_respaldado", "fecha_consulta"])
    escribir_csv(DATOS / "licencias_imagenes.csv",
                 [dict(im, id_demo=a["id_demo"]) for a in alojamientos for im in a["imagenes"]],
                 ["id_demo", "orden", "principal", "clasificacion", "tipo_imagen", "archivo", "titulo_commons",
                  "url_original", "autor", "fuente", "licencia", "licencia_url", "nota", "fecha_consulta"])
    print(f"{len(alojamientos)} alojamientos escritos en {DATOS}")


if __name__ == "__main__":
    main()
