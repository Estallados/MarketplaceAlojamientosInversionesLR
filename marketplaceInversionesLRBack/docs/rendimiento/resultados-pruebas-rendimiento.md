# Resultados de las pruebas de carga y estrés

- **Sistema medido:** backend de **MarketplaceAlojamientosInversionesLR**, commit `2c84041` (`main`).
- **Fecha:** 15 de noviembre de 2026.
- **Entorno:** local, con H2 en memoria y 12 núcleos.
- **Datos:** 2.201 usuarios, 5.000 alojamientos, 30.000 reservas y 10.000 reseñas, todos sintéticos.
- **Criterio de aceptación:** p95 < 500 ms y errores < 1 %.

El funcionamiento de las pruebas está en [`README.md`](README.md). Los datos completos de cada prueba están en `resultados/*.json`.

## Resumen

| Medida | Valor |
|---|---|
| Usuarios concurrentes | **400** con p95 de 40 ms y 0 % de errores (máximo probado) |
| Throughput sostenible | **~400 rps** con p95 de 427 ms |
| Throughput máximo | **~470 rps** |
| Respuesta típica (p50) | **6–8 ms** hasta 300 rps |
| Respuesta del 95 % (p95) | **36–49 ms** hasta 300 rps |
| Errores | **0 %** en ~750.000 peticiones de navegación |
| Pico de 400 rps durante 30 s | 0 % de errores, p95 de 934 ms |
| Recuperación tras saturar | Menos de 1 min (p95 de 35–38 ms) |
| Resistencia de 15 min a 220 rps | p95 de 40–45 ms, 0 % de errores |
| Login | **~80 logins/s** con p95 de 272 ms; máximo de ~120/s |
| Ataque de 200 intentos de login fallidos/s | 0 % de errores para los demás usuarios (p95 de 4 ms) |

## Carga (usuarios simultáneos, 1–3 s entre acciones)

| Usuarios | rps | Media | p50 | p90 | p95 | p99 | Errores |
|---|---|---|---|---|---|---|---|
| 50 | 25,1 | 14,5 | 8,9 | 38,2 | 43,1 | 50,8 | 0 % |
| 200 | 100,3 | 12,5 | 6,9 | 35,3 | 39,2 | 52,8 | 0 % |
| 400 | 200,8 | 12,2 | 6,5 | 36,6 | 40,4 | 48,8 | 0 % |

Tiempos en ms. 75.629 peticiones en 13 minutos.

## Estrés (tasa creciente, escalones de 60 s)

| rps pedidos | rps atendidos | Media | p50 | p90 | p95 | p99 | Errores |
|---|---|---|---|---|---|---|---|
| 10 | 10,1 | 16,7 | 11,8 | 38,8 | 42,6 | 46,7 | 0 % |
| 50 | 50,6 | 12,2 | 7,6 | 33,5 | 35,5 | 40,4 | 0 % |
| 100 | 100,9 | 11,7 | 6,7 | 33,9 | 36,6 | 43,0 | 0 % |
| 150 | 151,7 | 11,7 | 6,6 | 35,0 | 38,5 | 46,5 | 0 % |
| 200 | 201,8 | 12,0 | 6,5 | 36,2 | 39,6 | 46,4 | 0 % |
| 250 | 252,6 | 12,4 | 6,5 | 37,5 | 41,4 | 49,2 | 0 % |
| 300 | 302,4 | 14,6 | 7,3 | 41,3 | 48,8 | 68,8 | 0 % |
| 350 | 353,0 | 20,2 | 10,4 | 53,3 | 68,7 | 100,9 | 0 % |
| 400 | 403,5 | 107,2 | 43,5 | 279,3 | **426,7** | 996,5 | 0 % |
| 500 | 468,9 | 944,9 | 337,7 | 2.934,7 | 3.829,0 | 4.632,1 | 0 % |
| 600 | 445,4 | 5.410,2 | 5.889,1 | 7.290,0 | 7.628,1 | 8.160,7 | 0 % |
| 25 (recuperación) | 25,2 | 12,5 | 7,5 | 34,2 | 37,9 | 52,1 | 0 % |

Tiempos en ms. 218.809 peticiones.

### p95 por endpoint (ms)

| Endpoint | 100 rps | 300 rps | 400 rps |
|---|---|---|---|
| `GET /alojamientos/disponibles` | 43,7 | 70,4 | 438,1 |
| `GET /alojamientos/filtros` | 36,1 | 56,7 | 388,7 |
| `GET /alojamientos` con fechas | 20,3 | 30,3 | 404,5 |
| `GET /alojamientos/disponibles` con filtros | 17,3 | 26,7 | 402,2 |
| `POST /reservas` | 15,6 | 20,4 | 722,6 |
| `GET /alojamientos?ciudad=` | 9,9 | 14,8 | 392,1 |
| `GET /reservas/mias` | 8,1 | 13,0 | 605,0 |
| `POST /cotizaciones` | 6,1 | 9,0 | 755,6 |
| `GET /alojamientos/{id}/resenas` | 4,4 | 6,9 | 376,3 |
| `GET /alojamientos/{id}` | 4,1 | 7,8 | 381,8 |
| `GET /alojamientos/{id}/disponibilidad` | 3,6 | 5,6 | 329,4 |
| `GET /alojamientos/tipos` | 3,4 | 5,6 | 359,0 |
| `GET /notificaciones/noleidas` | 3,3 | 4,7 | 637,9 |
| `GET /usuarios/perfil` | 2,8 | 3,8 | 615,4 |

### Estrés con páginas pequeñas (mezcla `paginada`)

| rps pedidos | rps atendidos | p50 | p95 | p99 | Errores |
|---|---|---|---|---|---|
| 100 | 101,0 | 6,4 | 37,3 | 43,7 | 0 % |
| 200 | 201,7 | 6,6 | 41,7 | 51,8 | 0 % |
| 300 | 302,1 | 6,9 | 46,4 | 58,7 | 0 % |
| 400 | 404,0 | 7,9 | **56,7** | 89,2 | 0 % |
| 500 | 494,8 | 1.307,3 | 2.159,3 | 2.539,9 | 0 % |
| 600 | 550,2 | 3.098,2 | 5.552,0 | 5.967,0 | 0 % |
| 25 (recuperación) | 25,2 | 7,2 | 36,5 | 41,3 | 0 % |

Tiempos en ms. 233.425 peticiones.

## Pico

| Etapa | rps pedidos | rps atendidos | p50 | p95 | p99 | Errores |
|---|---|---|---|---|---|---|
| Antes | 25 | 25,2 | 10,0 | 37,7 | 44,6 | 0 % |
| Pico (30 s) | 400 | 403,1 | 94,8 | 934,1 | 1.390,2 | 0 % |
| Minuto 1 después | 25 | 25,4 | 7,2 | 35,1 | 40,3 | 0 % |
| Minutos 2 a 5 después | 25 | 25,1–25,3 | 6,9–7,7 | 34,0–35,6 | 36,2–41,2 | 0 % |

Tiempos en ms.

## Resistencia (15 min a 220 rps)

| Tramo | rps | p50 | p95 | p99 | Errores | Memoria del backend |
|---|---|---|---|---|---|---|
| 0–5 min | 222,0 | 6,8 | 44,6 | 123,6 | 0 % | 1.208 MB |
| 5–10 min | 221,9 | 6,3 | 39,6 | 48,3 | 0 % | 1.229 MB |
| 10–15 min | 221,8 | 6,7 | 42,8 | 55,7 | 0 % | 1.231 MB |

Tiempos en ms. 199.764 peticiones. La memoria es el máximo de cada tramo.

## Login

| Logins/s pedidos | Atendidos | Media | p50 | p95 | p99 | Errores |
|---|---|---|---|---|---|---|
| 5 | 5,0 | 71,9 | 71,6 | 77,2 | 83,1 | 0 % |
| 20 | 19,9 | 73,5 | 71,7 | 85,1 | 106,1 | 0 % |
| 40 | 39,9 | 71,6 | 69,9 | 82,6 | 100,1 | 0 % |
| 80 | 79,7 | 111,5 | 73,8 | 271,6 | 380,0 | 0 % |
| 120 | 112,0 | 2.022,6 | 2.259,7 | 3.997,4 | 5.513,2 | 0,3 % |
| 160 | 121,3 | 4.849,2 | 4.839,5 | 9.464,3 | 10.001,2 | 3,1 % |
| 240 | 240,0 | 1.216,6 | 1,1 | 5.451,2 | 7.390,4 | 80,7 % |

Tiempos en ms.

## Login con intentos fallidos

Cada etapa mantiene 100 lecturas/s de usuarios normales y suma los intentos fallidos indicados.

| Intentos fallidos/s | Total rps | p95 de los demás usuarios | Errores | Intentos rechazados con 429 |
|---|---|---|---|---|
| 0 | 100,0 | 10,2 | 0 % | — |
| 50 | 150,0 | 6,1 | 0 % | 1.510 |
| 100 | 200,0 | 4,9 | 0 % | 2.966 |
| 200 | 299,5 | 4,1 | 0 % | 6.012 |

Tiempos en ms.

## Concurrencia (20 rondas)

| Prueba | Resultado |
|---|---|
| 100 huéspedes reservan las mismas fechas a la vez | 1 reserva por ronda (20 de 20); p95 de 431 ms |
| La misma reserva enviada 30 veces con la misma clave | 1 reserva por ronda (20 de 20) |
| La misma cancelación enviada 30 veces con la misma clave | 1 cancelación por reserva (20 de 20) |
| Revisión final de la base | 0 cruces de fechas y 0 pagos duplicados |
