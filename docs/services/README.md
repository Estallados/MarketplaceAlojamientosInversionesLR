# Servicios

Descomposición del sistema en servicios: responsabilidades, datos que gestiona cada uno, dependencias e integración con el proveedor de pagos.

| Archivo | Contenido |
|---|---|
| [lista-servicios.md](lista-servicios.md) | Lista de servicios (S01 a S16), sus responsabilidades y datos, dependencias e integración de pagos |
| [matriz-servicios-requisitos.csv](matriz-servicios-requisitos.csv) | Matriz servicio, requisito, HU y proceso BPMN |

## Formato de la matriz

- Separador: punto y coma (`;`).
- Codificación: ISO-8859-1 (Latin-1), para que Excel en español lo abra con las tildes correctas.
- La fila 1 tiene los encabezados. Los datos empiezan en la fila 2.

## Columnas de la matriz

| Columna | Contenido |
|---|---|
| Servicio | Identificador del servicio (S01 a S16) |
| Nombre del servicio | Nombre del servicio en `lista-servicios.md` |
| Requisito | RF o RNF del [catálogo](../requirements/catalogo-requisitos.md) |
| Requerimiento | Nombre del requisito |
| HU | Historias de usuario que citan el requisito en el catálogo. `-` si el requisito es transversal |
| Proceso BPMN | Proceso PB-01 a PB-07 del análisis. `Por confirmar` si ningún proceso lo cubre |
| Tipo de relación | `Responsable`: el servicio implementa el requisito. `Cumple el requisito no funcional`: el servicio aporta a un RNF |

## Procedimiento

1. Si cambia la responsabilidad de un servicio, actualiza `lista-servicios.md` y sus filas en la matriz en el mismo PR.
2. Si cambia un RF o RNF, o una HU, actualiza también el [catálogo](../requirements/catalogo-requisitos.md) y la [matriz de trazabilidad](../traceability/README.md) según corresponda.
3. Guarda la matriz con separador `;` y codificación ISO-8859-1.
