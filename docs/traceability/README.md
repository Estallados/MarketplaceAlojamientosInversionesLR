# Matriz de trazabilidad

`matriz trazabilidad.csv` relaciona cada criterio de aceptación (CA) con su requisito, historia de usuario, caso de uso, caso de prueba, prueba automatizada, rama, PR y release. Tiene una fila por CA: 108 en total, de CA-001 a CA-196.

## Formato del archivo

- Separador: punto y coma (`;`).
- Codificación: ISO-8859-1 (Latin-1), para que Excel en español lo abra con las tildes correctas.
- La fila 1 tiene el título, la fila 2 está vacía y la fila 3 tiene los encabezados. Los datos empiezan en la fila 4.
- Las celdas con varios valores (por ejemplo varios CP o varias pruebas) los separan con saltos de línea dentro de la celda.

## Columnas

| Columna | Contenido |
|---|---|
| CA | Identificador del criterio de aceptación |
| Requisito | RF y RNF que listan el CA en su columna de criterios |
| Requerimiento | Nombre de esos requisitos |
| Criterio | Nombre corto del criterio |
| Condición de aceptación | Condición en formato Dado / Cuando / Entonces |
| HU | Historias de usuario que listan el CA. Vacía en los 21 CA de RNF transversales que ninguna historia lista |
| Caso de uso | CU del requisito, según la matriz de Confluence |
| Caso de prueba | CP del requisito, según la matriz de Confluence |
| Prueba automatizada | `Clase#metodo` de la prueba que cubre el CA |
| Estado | Estado de la prueba del CA |
| Commit | Commit que introdujo la prueba |
| Rama | Rama donde se trabajó el CA |
| PR | Número del pull request que entregó el CA |
| Release | Versión en la que se publicó el CA |
| Observación | Notas, por ejemplo por qué un CA no se prueba con JUnit o por qué no tiene HU |

Caso de uso y caso de prueba se asignan por requisito, igual que en la matriz de Confluence: ningún documento relaciona un CP con un CA concreto.

## Fuentes

Exportaciones de Confluence del espacio Estallados:

- Requerimientos Funcionales (RF-001 a RF-029): columna "CA relacionado".
- Requerimientos No Funcionales (RNF-001 a RNF-023): columna "Métrica / Criterio de aceptación".
- Historias de Usuario (HU-01 a HU-32): campo "Criterios de aceptación".
- Criterios de aceptación: nombre y condición de cada CA.
- Matriz de Trazabilidad: relación RF/RNF → HU → CU → CP.
- Casos de prueba: nombre de cada CP.

## Procedimiento: cada PR completa su fila antes del merge

1. Identifica los CA que cubre tu cambio.
2. En `matriz trazabilidad.csv`, actualiza la fila de cada CA:
   - **Prueba automatizada**: la prueba nueva o modificada. El nombre del método termina en `_CAxxx` (por ejemplo `debeRechazarSalidaIgualOAnteriorALaLlegada_CA082`).
   - **Estado** y **Commit**.
   - **Rama**: la rama del PR.
   - **PR**: el número del PR (por ejemplo `#12`).
3. Al publicar una versión, llena **Release** en las filas que entraron en ella (por ejemplo `v0.1.0`).
4. Si cambia un CA, una HU o un CP, actualiza también la página *Matriz de Trazabilidad* en Confluence.

Guarda el archivo con el mismo formato: separador `;` y codificación ISO-8859-1.
