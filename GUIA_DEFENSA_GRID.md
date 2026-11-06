# Guión Técnico Detallado: Ficheros Editados y Creados (Bloque Grid 2D)

---

## 1. Resumen General de Archivos

| Estado | Ruta del Fichero | Rol Principal |
| :---: | :--- | :--- |
| **EDITADO** | `src/main/java/com/mycompany/agentesp2/api/DbaClient.java` | Cliente HTTP REST compartido entre Termostato y Grid |
| **EDITADO** | `src/main/java/com/mycompany/agentesp2/Main.java` | Lanzador principal con soporte para elegir agente y reto |
| **EDITADO** | `run.sh` | Script de automatización de compilación, tests y ejecución |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/model/grid/GridAction.java` | Enum de las 4 acciones cardinales con vectores $(\Delta x, \Delta y)$ |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/model/grid/GridPosition.java` | Coordenada inmutable $(x, y)$, distancia Manhattan y adyacencia |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/model/grid/GridBumpers.java` | Estado de los 4 sensores de colisión e `isSafe(action)` |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/model/grid/GridEnergy.java` | Saldo de energía (`mode` y `remaining`) |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/model/grid/GridState.java` | DTO agregador de todas las percepciones del Grid y `RunControl` |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/grid/navigation/CellType.java` | Enum del estado de una celda (`UNKNOWN`, `FREE`, `OBSTACLE`) |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/grid/navigation/MentalMap.java` | Mapa mental dinámico (sin asumir 10×10) y renderizador ASCII |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/grid/navigation/PathPlanner.java` | Algoritmo **Optimistic Online $A^*$** sobre el mapa mental |
| **CREADO** | `src/main/java/com/mycompany/agentesp2/grid/GridAgent.java` | Agente JADE de navegación 2D con `NavegacionBehaviour` |
| **CREADO** | `src/test/java/com/mycompany/agentesp2/grid/GridPositionTest.java` | Tests unitarios offline de geometría, JSON y bumpers |
| **CREADO** | `src/test/java/com/mycompany/agentesp2/grid/PathPlannerTest.java` | Simulador offline de los 7 mapas 10×10 y meta encerrada |

---

## 2. Ficheros EDITADOS (Qué se ha cambiado y por qué)

### 2.1. [`DbaClient.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/api/DbaClient.java)
**Qué se ha mantenido intacto**:
Todo el código del Termostato (`initThermostatState`, `refreshThermostatTemperature`, `sendAction(runId, ThermostatAction)`), la lectura de credenciales (`DBA_LAB_URL`, `DBA_TOKEN`), el `ShutdownHook` de la JVM, el archivo `.active_run_id` y las peticiones HTTP de bajo nivel siguen exactamente igual.

**Qué se ha añadido**:
1. **`findGridChallenges()`**:
   - Llama a `GET /api/v1/challenges` y filtra todos los retos cuyo `environmentType` sea `"GRID_2D"`.
2. **`resolveGridChallenge(String selector)`**:
   - Permite elegir dinámicamente qué reto `GRID_2D` ejecutar **sin fijar IDs numéricos en el código**:
     - Si no se pasa selector, elige el primer reto `GRID_2D`.
     - Si se pasa `#1`, `#2`, etc., selecciona por orden dentro de los retos Grid.
     - Si se pasa texto (ej. `"Campo abierto"`, `"Barrera en U"`), busca coincidencia en el nombre del reto devuelto por `/challenges`.
3. **`validateGridCapabilities(JSONObject challenge)`**:
   - Verifica que el reto elegido anuncia en `sensorCodes` los 4 sensores (`grid.position`, `grid.bumpers`, `grid.target`, `grid.energy`) y en `actionCodes` los 4 movimientos (`grid.move.north`, `grid.move.east`, `grid.move.south`, `grid.move.west`). Si faltase alguno, lanza `DbaException` con código `422`.
4. **`initGridState(long runId)`**:
   - Realiza las 4 lecturas iniciales (`grid.position`, `grid.target`, `grid.bumpers`, `grid.energy`) comprobando tras **cada** petición si `control.runStatus` ha dejado de ser `ACTIVE`. Devuelve un objeto `GridState` completamente inicializado.
5. **`refreshGridState(GridState state)`**:
   - Se llama después de cada movimiento. Como `POST /actions` no devuelve la nueva posición ni la nueva energía, este método consulta `grid.position`, `grid.bumpers` y `grid.energy` (la meta `grid.target` no se vuelve a pedir porque es fija). Tras cada lectura comprueba `state.getControl().isTerminal()`.
6. **`sendGridAction(long runId, GridAction action)` y `recoverControlAfterUncertainAction(...)`**:
   - Envía `POST /api/v1/runs/{runId}/actions` con el código de `GridAction`.
   - **Protección de idempotencia**: Si se produce un error `503` (resultado no confirmado) o un corte de red al recibir la respuesta, **NO reintenta el POST a ciegas** (ya que podría gastar doble energía o chocar). En su lugar, consulta `GET /runs/{runId}` (`getRunStatus`) y `GET /runs/{runId}/actions/last` (`getLastAction`) para recuperar el estado real del servidor.
7. **`getLastAction(long runId)`**:
   - Nuevo método de bajo nivel que invoca `GET /api/v1/runs/{runId}/actions/last`.

---

### 2.2. [`Main.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/Main.java)
**Cambios realizados**:
1. **Listado enriquecido de retos**: Al arrancar, etiqueta los retos `GRID_2D` con su número de orden (`[GRID #1]`, `[GRID #2]`, etc.) para identificarlos fácilmente.
2. **Selector de agente y reto**:
   - Si recibe argumentos por línea de comandos (`grid [1..7]` o `termostato`), lanza directamente el agente correspondiente pasando el selector de reto como argumento del agente JADE: `grid:com.mycompany.agentesp2.grid.GridAgent(selector)`.
   - Soporta selección numérica directa (`grid 1`, `grid 2`, ..., `grid 7`) o por texto (`grid "Campo abierto"`).
   - Une con espacios los argumentos a partir de `args[1]` (`Arrays.copyOfRange`) para que nombres con espacios lleguen íntegros al agente.
3. **Separación de contenedores JADE (`server` vs `-container`)**:
   - Soporta modo `server` (`./run.sh server`) que arranca el `Main-Container` persistente en `localhost:1099` con la interfaz gráfica RMA abierta una sola vez.
   - Dispone de comprobación de socket TCP (`isMainContainerActive`) para detectar si el `Main-Container` ya está corriendo en `localhost:1099`. Si está activo, lanza el agente como contenedor secundario ligero (`-container -host localhost -port 1099 -agents ...`).
   - Si no está activo el servidor, arranca de forma autónoma (standalone) sin fallar.
4. **Soporte con y sin GUI (`JADE_GUI`)**:
   - Pasa siempre el flag `-agents` a `jade.Boot.main(...)` y permite desactivar la ventana gráfica RMA con `JADE_GUI=false`.

---

### 2.3. [`run.sh`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/run.sh)
**Cambios realizados**:
- Añadidos subcomandos específicos:
  - `./run.sh server`: Inicia el servidor JADE persistente (`Main-Container` en `localhost:1099`).
  - `./run.sh grid [1..7]`: Compila y lanza `GridAgent` en el reto numérico indicado.
  - `./run.sh termostato [reto]`: Compila y lanza `TermostatoAgent`.
  - `./run.sh test-unit`: Ejecuta `mvn test` con la batería de tests unitarios offline.
  - `./run.sh test`: Ejecuta `TestConexion`.

---

## 3. Ficheros CREADOS (Explicación al detalle de cada clase)

### 3.1. Paquete `model/grid/` (Modelos de Dominio y Percepciones)

#### 1. [`GridAction.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/model/grid/GridAction.java)
- **Qué hace**: Representa las 4 acciones físicas del entorno Grid 2D mediante un `enum` seguro (evitando usar Strings sueltos en la lógica del agente).
- **Detalles clave**:
  - Cada constante almacena su código oficial de API y su vector de desplazamiento $(\Delta x, \Delta y)$ acorde al sistema de coordenadas de DBA Lab (donde $x$ crece a la derecha e $y$ crece hacia abajo):
    - `MOVE_NORTH("grid.move.north", 0, -1)`
    - `MOVE_EAST("grid.move.east", 1, 0)`
    - `MOVE_SOUTH("grid.move.south", 0, 1)`
    - `MOVE_WEST("grid.move.west", -1, 0)`
  - Método `opposite()`: devuelve la dirección inversa (`NORTH <-> SOUTH`, `EAST <-> WEST`).

#### 2. [`GridPosition.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/model/grid/GridPosition.java)
- **Qué hace**: Representa un punto inmutable $(x, y)$ en la cuadrícula.
- **Detalles clave**:
  - Es `final` e inmutable (`x` e `y` son `final`) e implementa correctamente `equals(Object)` y `hashCode()`, lo que es imprescindible para usar `GridPosition` como clave en los `HashMap` y `HashSet` del `MentalMap` y del algoritmo $A^*$.
  - `fromJson(JSONObject)`: extrae `x` e `y` del bloque `"value"` devuelto por `grid.position` o `grid.target`.
  - `neighbor(GridAction action)`: devuelve una nueva `GridPosition(x + action.getDx(), y + action.getDy())`.
  - `manhattanDistance(GridPosition other)`: calcula $|x_1 - x_2| + |y_1 - y_2|$.

#### 3. [`GridBumpers.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/model/grid/GridBumpers.java)
- **Qué hace**: Encapsula los 4 booleanos (`north`, `east`, `south`, `west`) del sensor `grid.bumpers`.
- **Detalles clave**:
  - `isBlocked(GridAction)` devuelve `true` si en esa dirección hay un muro o el límite del mapa.
  - `isSafe(GridAction)` devuelve `!isBlocked(action)` y actúa como barrera de seguridad antes de enviar cualquier movimiento al servidor.

#### 4. [`GridEnergy.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/model/grid/GridEnergy.java)
- **Qué hace**: Encapsula el modo (`"FINITE"`) y las unidades restantes (`remaining`) del sensor `grid.energy`.
- **Detalles clave**:
  - `hasEnergy()` comprueba `remaining > 0` cuando el modo es `"FINITE"`.

#### 5. [`GridState.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/model/grid/GridState.java)
- **Qué hace**: Es el DTO principal del agente Grid (equivalente a `ThermostatState` en el bloque 1).
- **Detalles clave**:
  - Agrupa `currentPosition`, `targetPosition`, `bumpers`, `energy` y `RunControl`.
  - Actualiza automáticamente `this.control` cada vez que se refresca cualquiera de los sensores (`updatePosition`, `updateBumpers`, `updateEnergy`) o tras aplicar una acción (`updateControl`).
  - Proporciona `isAtTarget()` (`currentPosition.equals(targetPosition)`) y `getDistanceToTarget()`.

---

### 3.2. Paquete `grid/navigation/` (Cerebro de Navegación y Memoria)

#### 6. [`CellType.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/grid/navigation/CellType.java)
- **Qué hace**: Define los 3 estados posibles de una casilla en la memoria del agente:
  - `UNKNOWN ('?')`: Aún no observada.
  - `FREE ('.')`: Confirmada como transitable.
  - `OBSTACLE ('#')`: Confirmada como muro o fuera de los límites del mapa.

#### 7. [`MentalMap.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/grid/navigation/MentalMap.java)
- **Qué hace**: Construye y mantiene el mapa mental del entorno a medida que el agente se mueve.
- **Por qué está diseñado con `HashMap` en vez de `char[10][10]`**:
  - El guión advierte que el agente no conoce el tamaño del mundo a priori y que en la Tanda 2 los mundos serán desconocidos. Usar `Map<GridPosition, CellType> cells` y `Map<GridPosition, Integer> visitCounts` permite representar mundos de cualquier tamaño e incluso registrar los bordes externos en coordenadas como $x = -1$, $y = -1$ o $x = 10$, $y = 10$.
- **Métodos principales**:
  - `updateFromPerceptions(current, bumpers)`:
    1. Marca `current` como `FREE` y suma $+1$ a su número de visitas en `visitCounts`.
    2. Recorre las 4 direcciones de `GridAction`: si `bumpers.isBlocked(dir)` es `true`, marca la casilla vecina como `OBSTACLE`; si es `false`, la marca como `FREE`.
  - `isTransitable(pos)`: devuelve `true` si la casilla no es `OBSTACLE` (tanto `FREE` como `UNKNOWN` son transitables bajo el principio de optimismo).
  - `renderAscii(current, target)`: genera la tabla ASCII con cabeceras de fila/columna mostrando `@` (agente), `$` (meta), `★` (agente en la meta), `#` (muro/borde), `+` (casilla ya pisada), `.` (casilla libre vista por bumper) y `?` (desconocida).

#### 8. [`PathPlanner.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/grid/navigation/PathPlanner.java)
- **Qué hace**: Implementa el algoritmo **Optimistic Online $A^*$** para calcular en cada paso el camino más corto desde la posición actual hasta la meta sobre el `MentalMap`.
- **Por qué resuelve obstáculos en U, C y arco**:
  - Un algoritmo voraz sin memoria solo mira las 4 casillas vecinas y elige la que acerque más a la meta; al entrar en una U queda atrapado en el fondo porque retroceder aumenta la distancia Manhattan.
  - Con **Optimistic Online $A^*$**, cuando el agente entra en la U y sus bumpers descubren los muros del fondo, esas casillas quedan marcadas como `OBSTACLE` en `MentalMap`. En el siguiente turno, $A^*$ busca el camino completo desde dentro de la U hasta la meta evitando todos los `OBSTACLE` conocidos; el camino resultante sale limpiamente de la U, rodea las paredes exteriores y llega a la meta por la ruta más corta posible.
- **Función de evaluación y criterios de desempate (`SearchNode.compareTo`)**:
  1. Menor coste total $f(n) = g(n) + h(n)$, donde $g(n)$ son los pasos desde la posición actual y $h(n)$ la distancia Manhattan a la meta.
  2. A igual $f(n)$, menor heurística $h(n)$ (más cerca de la meta).
  3. A igual $f(n)$ y $h(n)$, menor `visitCount` en el `MentalMap` (prefiere explorar casillas nuevas antes que volver a pisar casillas ya recorridas).
  4. A igualdad total, prefiere casillas confirmadas `FREE` frente a `UNKNOWN`.
- **Parada controlada**: Si la meta estuviera completamente rodeada de muros, la cola de prioridad `openSet` se agota (o alcanza `MAX_EXPANDED_NODES`) y devuelve `Optional.empty()`, evitando que el agente se mueva a ciegas.

---

### 3.3. Paquete `grid/` (Agente JADE)

#### 9. [`GridAgent.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/main/java/com/mycompany/agentesp2/grid/GridAgent.java)
- **Qué hace**: Es el agente JADE que gobierna el ciclo de vida y el comportamiento de navegación.
- **Ciclo de vida**:
  - **`setup()`**:
    1. Crea `DbaClient.fromEnvOrConfig()`.
    2. Descubre y valida el reto `GRID_2D` con `resolveChallenge()`.
    3. Inicia el run con `client.startRunTyped(challengeId)`.
    4. Carga las 4 percepciones iniciales con `client.initGridState(runId)`.
    5. Inicializa `MentalMap` y `PathPlanner` y registra `addBehaviour(new NavegacionBehaviour())`.
  - **`NavegacionBehaviour` (`action()` y `done()`)**:
    - En cada ejecución de `action()` realiza **un único paso** (respetando la planificación cooperativa de JADE):
      1. Verifica si `state.getControl().isTerminal()`.
      2. Actualiza `mentalMap.updateFromPerceptions(...)` e imprime por consola el paso, la energía restante y el mapa ASCII.
      3. Verifica que no se haya agotado la energía (`state.getEnergy().hasEnergy()`).
      4. Llama a `selectValidatedSafeAction()`: obtiene la siguiente acción de `PathPlanner` y **valida obligatoriamente `state.getBumpers().isSafe(candidate)` antes de enviar**. Si el bumper estuviera bloqueado, marca la casilla como `OBSTACLE` y replanifica en memoria sin enviar petición al servidor.
      5. Envía el movimiento con `client.sendGridAction(runId, nextAction)` y actualiza `RunControl`.
      6. Si tras la acción `state.getControl().isTerminal()` es `true` (por ejemplo, `SUCCEEDED` al pisar la meta), muestra el mapa final con `★` y termina con `doDelete()`.
      7. Si sigue `ACTIVE`, invoca `client.refreshGridState(state)` para leer la nueva posición, los nuevos bumpers y la nueva energía.
  - **`takeDown()`**:
    - Si el agente se detiene mientras `state.getControl().isActive()` sigue siendo `true`, llama a `client.closeRun(runId)` para cerrar el run en DBA Lab y no dejar bloqueado el token del estudiante.
    - Si el agente se ejecutó en un contenedor secundario ligero (`JADE_PERIPHERAL_CONTAINER=true`) o en modo sin ventana (`JADE_GUI=false`), destruye su propio contenedor secundario (`getContainerController().kill()`) y cierra la JVM del agente con `System.exit(0)`, dejando intacto el `Main-Container` y la interfaz gráfica de JADE que esté corriendo de fondo.

---

### 3.4. Paquete `src/test/java/com/mycompany/agentesp2/grid/` (Tests Unitarios Offline)

#### 10. [`GridPositionTest.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/test/java/com/mycompany/agentesp2/grid/GridPositionTest.java)
- Comprueba que los desplazamientos Norte ($y-1$), Sur ($y+1$), Este ($x+1$) y Oeste ($x-1$) son exactos, que la distancia Manhattan es simétrica, que `actionTowards` y `opposite` son inversos y que el parseo JSON de `GridPosition` y `GridBumpers` funciona correctamente.

#### 11. [`PathPlannerTest.java`](file:///home/jose/26_27/DBA/P2/Termostato/Codigo/agentesp2/src/test/java/com/mycompany/agentesp2/grid/PathPlannerTest.java)
- Incluye un motor de simulación local sin red (`runOnlineSimulation`) donde el agente empieza a ciegas y en cada paso solo recibe los 4 bumpers de su casilla actual.
- Verifica que el agente resuelve sin un solo choque y sin agotar la energía los 7 escenarios de la Tanda 1 (*Campo abierto*, *Muro vertical*, *Muro horizontal*, *Muro diagonal*, *Barrera en U*, *Barrera en C*, *Barrera en arco*) y que se detiene de forma controlada cuando la meta es inalcanzable.
