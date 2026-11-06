# agentesp2 - Práctica 2: Percepción y acción en entornos virtuales

Proyecto para la asignatura de **Diseño Basado en Agentes (DBA)** de la Universidad de Granada (Curso 2026/2027).

Implementación de agentes inteligentes en **JADE** que interactúan con el laboratorio remoto **DBA Lab** para resolver:
1. **Bloque 1 — Termostato (`TermostatoAgent`)**: Control térmico de una cámara frigorífica con perturbaciones estocásticas.
2. **Bloque 2 — Grid 2D (`GridAgent`)**: Navegación autónoma en cuadrícula 2D con percepción parcial, presupuesto de energía finita, mapa mental dinámico (`MentalMap`) y planificación **Optimistic Online $A^*$** (`PathPlanner`).

---

## 🚀 Requisitos previos

1. **Java:** OpenJDK 17 o superior.
2. **JADE 4.6.0:** Instalado en el repositorio Maven local (`~/.m2`).
3. **Conexión UGR:** Estar conectado a la red universitaria (eduroam) o tener la VPN del CSIRC activa.
4. **Token de DBA Lab:** Cada integrante del grupo debe disponer de su propio token personal de estudiante.

---

## ⚙️ Configuración inicial

1. Clona el repositorio:
   ```bash
   git clone https://github.com/franlasant/agentesp2.git
   cd agentesp2
   ```

2. Copia la plantilla de configuración y añade tus credenciales:
   ```bash
   cp config.properties.template config.properties
   ```
   Edita `config.properties` introduciendo la URL del servidor y tu `DBA_TOKEN`:
   ```properties
   DBA_LAB_URL=http://orca.ugr.es:8080
   DBA_TOKEN=<tu-token-personal>
   ```
   *(Nota: `config.properties` está ignorado en `.gitignore` por seguridad para no exponer credenciales).*

---

## 🛠️ Estructura del proyecto

```text
src/
├── main/java/com/mycompany/agentesp2/
│   ├── Main.java                       # Punto de entrada (selector de agente Termostato / Grid)
│   ├── TestConexion.java               # Test rápido de conexión y listado de retos sin consumir runs
│   ├── api/
│   │   ├── DbaClient.java              # Cliente REST desacoplado compartido entre Termostato y Grid
│   │   └── DbaException.java           # Manejo de excepciones y códigos HTTP de DBA Lab
│   ├── model/
│   │   ├── RunControl.java             # DTO para el bloque 'control' de DBA Lab
│   │   ├── RunStatus.java              # Enum para estados del run (ACTIVE, SUCCEEDED, FAILED...)
│   │   ├── ThermostatAction.java       # Enum para acciones del termostato (on/off)
│   │   ├── ThermostatState.java        # DTO con el estado completo del termostato
│   │   └── grid/
│   │       ├── GridAction.java         # Enum de movimientos cardinales (N, E, S, W) con (dx, dy)
│   │       ├── GridPosition.java       # Posición inmutable (x, y) y distancia Manhattan
│   │       ├── GridBumpers.java        # Sensores de proximidad e isSafe(GridAction)
│   │       ├── GridEnergy.java         # Saldo y modo de energía finita
│   │       └── GridState.java          # DTO agregador del estado del agente en Grid 2D
│   ├── termostato/
│   │   └── TermostatoAgent.java        # Agente JADE para el reto del Termostato
│   └── grid/
│       ├── GridAgent.java              # Agente JADE de navegación 2D con NavegacionBehaviour
│       └── navigation/
│           ├── CellType.java           # Enum (UNKNOWN, FREE, OBSTACLE)
│           ├── MentalMap.java          # Mapa mental dinámico y renderizador ASCII
│           └── PathPlanner.java        # Planificador Optimistic Online A*
└── test/java/com/mycompany/agentesp2/grid/
    ├── GridPositionTest.java           # Tests unitarios offline de geometría y DTOs
    └── PathPlannerTest.java            # Simulador offline de los 7 mapas 10x10 y meta encerrada
```

---

## ▶️ Ejecución

### Usando el script `./run.sh` (recomendado)
```bash
# 1. Ejecutar tests unitarios offline (no requiere conexión ni consume runs)
./run.sh test-unit

# 2. Comprobar conexión con DBA Lab y listar retos activos
./run.sh test

# 3. Ejecutar el agente de navegación 2D (GridAgent) por número de reto (1, 2, 3...)
./run.sh grid 1                   # Reto 1: Campo abierto
./run.sh grid 2                   # Reto 2: Muro vertical
./run.sh grid 3                   # Reto 3: Muro horizontal
./run.sh grid 4                   # Reto 4: Muro diagonal
./run.sh grid 5                   # Reto 5: Barrera en U
./run.sh grid 6                   # Reto 6: Barrera en arco
./run.sh grid 7                   # Reto 7: Barrera en C

# (Opcional) Ejecución rápida en consola sin ventana de JADE:
JADE_GUI=false ./run.sh grid 1

# 4. Ejecutar el agente del Termostato (TermostatoAgent)
./run.sh termostato
```
