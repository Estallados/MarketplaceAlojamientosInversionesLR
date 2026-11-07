# Requisitos

Catálogo único de identificadores del proyecto. HU, CA y la [matriz de trazabilidad](../traceability/matriz%20trazabilidad.csv) citan **solo** los IDs de [catalogo-requisitos.md](catalogo-requisitos.md).

| Archivo | Contenido |
|---|---|
| [catalogo-requisitos.md](catalogo-requisitos.md) | Catálogo único de RF (RF-001 a RF-029) y RNF (RNF-001 a RNF-023), con sus CA y HU |
| [historias-usuario.md](historias-usuario.md) | HU-01 a HU-32 con sus requisitos y CA definitivos |
| [criterios-aceptacion.md](criterios-aceptacion.md) | CA-001 a CA-196 con su requisito definitivo |
| [equivalencias-ids.md](equivalencias-ids.md) | Numeración anterior → ID definitivo |
| [decisiones-SCRUM-196.md](decisiones-SCRUM-196.md) | Decisiones (CA-195, política de cancelación) y registro de cambios |

## Reglas

- Los IDs no se reutilizan ni se renumeran. Un requisito eliminado se marca como *retirado*; uno nuevo toma el siguiente número libre.
- La relación CA → requisito la define el catálogo (columna *CA* de cada RF/RNF). La relación CA → HU la define el campo *Criterios de aceptación* de cada HU.
- Si cambias un ID o una relación, actualiza en el mismo PR el catálogo, la HU o el CA afectado, la matriz y la página correspondiente en Confluence.
