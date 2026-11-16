"""Compara el contrato OpenAPI con la implementación del backend (SCRUM-204).

Lee los controladores de Spring (rutas, métodos HTTP y códigos declarados con
@ApiResponse) y los compara con docs/openapi/openapi.yaml. Informa:

1. Operaciones que existen en el código y no en el contrato.
2. Operaciones del contrato que ya no existen en el código.
3. Códigos de respuesta que el controlador declara y el contrato no documenta.
4. Operaciones protegidas que no documentan 401 (token ausente o inválido).

Sale con código 1 si encuentra alguna diferencia, para que el CI falle.

Uso, desde la raíz del repositorio:
    python3 docs/api-tests/verificar_contrato.py
"""

import glob
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("Falta PyYAML: pip install pyyaml")

RAIZ = Path(__file__).resolve().parents[2]
CONTROLADORES = RAIZ / "marketplaceInversionesLRBack/src/main/java"
CONTRATO = RAIZ / "docs/openapi/openapi.yaml"
METODOS = ("get", "post", "put", "patch", "delete")
MAPEO = re.compile(r"@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^)]*)\))?")
# Rutas públicas según SecurityConfig: no exigen token
PUBLICAS = {
    ("GET", "/pagos/configuracion"), ("GET", "/alojamientos"), ("GET", "/alojamientos/disponibles"),
    ("GET", "/alojamientos/filtros"), ("GET", "/alojamientos/tipos"), ("GET", "/alojamientos/politicas"),
    ("GET", "/alojamientos/{}"), ("GET", "/alojamientos/{}/disponibilidad"),
    ("GET", "/alojamientos/{}/disponibilidad/consultar"), ("GET", "/alojamientos/{}/resenas"),
}


def normalizar(ruta):
    """Quita la barra final y deja las variables de ruta como {}."""
    ruta = ruta.rstrip("/") or "/"
    return re.sub(r"\{[^}]+\}", "{}", ruta)


def operaciones_del_codigo():
    """Devuelve {(MÉTODO, ruta): (códigos declarados, archivo)} leyendo los controladores."""
    operaciones = {}
    for archivo in sorted(glob.glob(str(CONTROLADORES / "**/controller/*.java"), recursive=True)):
        texto = Path(archivo).read_text(encoding="utf-8")
        inicio = texto.find("public class")
        if inicio < 0:
            continue
        prefijo = re.search(r'@RequestMapping\(\s*"([^"]*)"', texto[:inicio])
        prefijo = prefijo.group(1) if prefijo else ""
        cuerpo = texto[inicio:]
        for mapeo in MAPEO.finditer(cuerpo):
            # Las anotaciones del método van desde el cierre del método anterior
            desde = max(cuerpo.rfind("\n\t}\n", 0, mapeo.start()), cuerpo.rfind("\n\t}\r\n", 0, mapeo.start()), 0)
            codigos = {int(c) for c in re.findall(r'responseCode\s*=\s*"(\d{3})"', cuerpo[desde:mapeo.start()])}
            for ruta in re.findall(r'"([^"]*)"', mapeo.group(2) or "") or [""]:
                clave = (mapeo.group(1).upper(), normalizar(prefijo + ruta))
                operaciones[clave] = (codigos, Path(archivo).name)
    return operaciones


def operaciones_del_contrato():
    """Devuelve {(MÉTODO, ruta): códigos documentados} leyendo openapi.yaml."""
    contrato = yaml.safe_load(CONTRATO.read_text(encoding="utf-8"))
    operaciones = {}
    for ruta, metodos in contrato["paths"].items():
        for metodo, operacion in metodos.items():
            if metodo in METODOS:
                codigos = {int(c) for c in operacion.get("responses", {})}
                operaciones[(metodo.upper(), normalizar(ruta))] = codigos
    return operaciones


def main():
    codigo = operaciones_del_codigo()
    contrato = operaciones_del_contrato()
    diferencias = []

    for clave in sorted(set(codigo) - set(contrato)):
        diferencias.append(f"Falta en el contrato: {clave[0]} {clave[1]} ({codigo[clave][1]})")
    for clave in sorted(set(contrato) - set(codigo)):
        diferencias.append(f"No existe en el código: {clave[0]} {clave[1]}")
    for clave in sorted(set(codigo) & set(contrato)):
        declarados, archivo = codigo[clave]
        faltan = sorted(declarados - contrato[clave])
        if faltan:
            diferencias.append(f"Códigos sin documentar en {clave[0]} {clave[1]}: {faltan} ({archivo})")
        protegida = clave not in PUBLICAS and not clave[1].startswith("/auth")
        if protegida and 401 not in contrato[clave]:
            diferencias.append(f"Ruta protegida sin 401 documentado: {clave[0]} {clave[1]}")

    print(f"Operaciones en el código: {len(codigo)} | en el contrato: {len(contrato)}")
    if diferencias:
        print(f"\n{len(diferencias)} diferencias entre el contrato y la implementación:")
        for diferencia in diferencias:
            print(f"  - {diferencia}")
        sys.exit(1)
    print("El contrato coincide con la implementación.")


if __name__ == "__main__":
    main()
