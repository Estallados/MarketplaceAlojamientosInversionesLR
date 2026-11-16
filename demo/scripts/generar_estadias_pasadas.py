"""Genera el SQL de estadías ya terminadas para la demostración (SCRUM-206 / T18).

Las reseñas solo se pueden escribir sobre una reserva CONFIRMADA cuya salida ya pasó, y la API no deja crear
reservas con fechas pasadas. Por eso este SQL inserta, como hace database/05_seed_data.sql, dos estadías
terminadas por alojamiento (cotización + reserva CONFIRMADA + pago APROBADO del simulador). Las reseñas se
escriben después por la API con generar_actividad.py.

- Las fechas son relativas a CURRENT_DATE y no se cruzan entre sí.
- Los valores siguen la fórmula de CotizacionService: base = precio x noches, limpieza 5 %, servicio 10 %.
- Es idempotente: cada estadía se marca con la referencia de pago sim_pi_demo-pasada-NN y no se repite.
- Busca los huéspedes por correo_hash (HMAC-SHA256 con AES_SECRET_KEY, igual que AESUtil.hash) y los
  alojamientos por título, así no depende de los ids.

Uso:
    $env:AES_SECRET_KEY = '<clave del ambiente>'
    python demo/scripts/generar_estadias_pasadas.py > ../database/05c_estadias_pasadas_demo.sql
y ejecutar el archivo en el SQL Editor de Neon.
"""

import base64
import hashlib
import hmac
import json
import os
import sys
from decimal import Decimal
from pathlib import Path

DATOS = Path(__file__).resolve().parent.parent / "datos"
HUESPEDES = ["anatorres@gmail.com", "diegomora@gmail.com", "sofiaperez@gmail.com", "pedrodiaz@gmail.com",
             "camilaherrera@gmail.com"]
METODOS = ["pm_card_visa", "pm_card_mastercard", "pm_card_amex", "pm_card_visa_debit"]
LIMPIEZA, SERVICIO = Decimal("5.00"), Decimal("10.00")


def correo_hash(correo, clave):
    return base64.b64encode(hmac.new(clave, correo.encode(), hashlib.sha256).digest()).decode()


def main():
    valor = os.environ.get("AES_SECRET_KEY", "").strip()
    if not valor:
        sys.exit("Define la variable de entorno AES_SECRET_KEY")
    clave = base64.b64decode(valor)
    alojamientos = json.loads((DATOS / "alojamientos_bogota.json").read_text(encoding="utf-8"))["alojamientos"]

    filas = []
    n = 0
    for i, a in enumerate(alojamientos):
        # Dos estadías por alojamiento: una hace 1-2 meses y otra hace 2-4 semanas
        for j, (salida_hace, noches) in enumerate([(40 + 3 * i, 2 + (i % 3)), (12 + 2 * i, 3 - (i % 2))]):
            n += 1
            huesped = HUESPEDES[(2 * i + j) % len(HUESPEDES)]
            huespedes = 1 + ((i + j) % 2)
            precio = Decimal(a["precio_noche"])
            base = precio * noches
            limpieza = (base * LIMPIEZA / 100).quantize(Decimal("0.01"))
            servicio = (base * SERVICIO / 100).quantize(Decimal("0.01"))
            total = base + limpieza + servicio
            entrada_hace = salida_hace + noches
            filas.append(f"({n}, '{a['titulo']}', '{correo_hash(huesped, clave)}', {entrada_hace}, {salida_hace}, "
                         f"{noches}, {huespedes}, {precio:.2f}, {base:.2f}, {limpieza:.2f}, {servicio:.2f}, {total:.2f}, "
                         f"'{METODOS[n % len(METODOS)]}')")

    print("-- Estadías terminadas de la demostración (SCRUM-206). Generado por demo/scripts/generar_estadias_pasadas.py.")
    print("-- Idempotente: cada estadía se identifica por la referencia de pago sim_pi_demo-pasada-NN.")
    print("SET client_encoding = 'UTF8';")
    print("BEGIN;")
    print("-- Las validaciones de 03b_triggers_validacion.sql rechazan fechas pasadas fuera del modo carga.")
    print("SET LOCAL marketplace.carga_datos = 'on';")
    print("DO $$")
    print("DECLARE e RECORD; v_cot BIGINT; v_res BIGINT; v_huesped BIGINT; v_aloj BIGINT; v_ref TEXT;")
    print("BEGIN")
    print("  FOR e IN SELECT * FROM (VALUES")
    print(",\n".join("    " + f for f in filas))
    print("  ) AS t(n, titulo, hash_huesped, entrada_hace, salida_hace, noches, huespedes, precio, base, limpieza, servicio, total, metodo)")
    print("  LOOP")
    print("    v_ref := 'sim_pi_demo-pasada-' || lpad(e.n::text, 2, '0');")
    print("    CONTINUE WHEN EXISTS (SELECT 1 FROM pago WHERE referencia = v_ref);")
    print("    SELECT id_usuario INTO v_huesped FROM usuario WHERE correo_hash = e.hash_huesped;")
    print("    SELECT id_alojamiento INTO v_aloj FROM alojamiento WHERE titulo = e.titulo;")
    print("    IF v_huesped IS NULL OR v_aloj IS NULL THEN")
    print("      RAISE NOTICE 'Se omite la estadía %: falta el huésped o el alojamiento', e.n;")
    print("      CONTINUE;")
    print("    END IF;")
    print("    INSERT INTO cotizacion (fecha_entrada, fecha_salida, huespedes, noches, precio_noche, precio_base, descuento,")
    print("        porcentaje_limpieza, valor_limpieza, porcentaje_servicio, valor_servicio, valor_adicionales, total, desglose,")
    print("        id_usuario, id_alojamiento, fecha_creacion, fecha_expiracion)")
    print("      VALUES (CURRENT_DATE - e.entrada_hace, CURRENT_DATE - e.salida_hace, e.huespedes, e.noches, e.precio, e.base, 0,")
    print(f"        {LIMPIEZA}, e.limpieza, {SERVICIO}, e.servicio, 0, e.total, '[]', v_huesped, v_aloj,")
    print("        LOCALTIMESTAMP - make_interval(days => e.entrada_hace + 10),")
    print("        LOCALTIMESTAMP - make_interval(days => e.entrada_hace + 10) + INTERVAL '30 minutes')")
    print("      RETURNING id_cotizacion INTO v_cot;")
    print("    INSERT INTO reserva (fecha_entrada, fecha_salida, noches, huespedes, estado, total, desglose, id_huesped,")
    print("        id_alojamiento, id_cotizacion, fecha_creacion)")
    print("      VALUES (CURRENT_DATE - e.entrada_hace, CURRENT_DATE - e.salida_hace, e.noches, e.huespedes, 'CONFIRMADA',")
    print("        e.total, '[]', v_huesped, v_aloj, v_cot, LOCALTIMESTAMP - make_interval(days => e.entrada_hace + 10))")
    print("      RETURNING id_reserva INTO v_res;")
    print("    UPDATE cotizacion SET id_reserva = v_res WHERE id_cotizacion = v_cot;")
    print("    INSERT INTO pago (referencia, proveedor, valor, estado, detalle, fecha, metodo, id_reserva)")
    print("      VALUES (v_ref, 'SIMULADO', e.total, 'APROBADO', 'Pago de prueba aprobado.',")
    print("        LOCALTIMESTAMP - make_interval(days => e.entrada_hace + 10), e.metodo, v_res);")
    print("  END LOOP;")
    print("END $$;")
    print("COMMIT;")


if __name__ == "__main__":
    main()
