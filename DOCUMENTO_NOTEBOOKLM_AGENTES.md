# ARQUITECTURA DE SISTEMAS MULTIAGENTE: TERMOSTATO Y GRID 2D EN JADE
**Documento técnico y didáctico para generación de resúmenes, guiones y contenido en NotebookLM**
*Proyecto: Prácticas de Sistemas Inteligentes y Multiagente (UGR / DBA Lab)*

---

## 1. VISIÓN GENERAL DEL PROYECTO

Este proyecto implementa dos tipos fundamentales de agentes inteligentes en Java utilizando la plataforma estándar **JADE** (*Java Agent Development Framework*) y conectándose a un entorno de simulación remoto llamado **DBA Lab** mediante una API REST:

1. **Agente Reactivo Simple (`TermostatoAgent`):** Control de temperatura en un entorno continuo unidimensional basado en reglas directas condición-acción (sin memoria espacial).
2. **Agente Cognitivo con Estado Interno y Planificación (`GridAgent`):** Navegación autónoma en laberintos bidimensionales desconocidos (entorno parcialmente observable con niebla de guerra), utilizando memoria espacial incremental (**`MentalMap`**) y búsqueda heurística en línea (**`Optimistic Online A*`** en **`PathPlanner`**).

Ambos agentes implementan el ciclo de vida canónico de JADE (`setup()`, `takeDown()`, `doDelete()`) y se comunican con el servidor a través de un cliente HTTP robusto (**`DbaClient`**) con control estricto de idempotencia y tolerancia a fallos.

---

## 2. AGENTE 1: EL TERMOSTATO (`TermostatoAgent`)

### 2.1. Tipología y Naturaleza del Entorno
* **Tipo de Agente:** Reactivo simple (basado en reglas condición-acción).
* **Entorno:** Unidimensional, continuo (temperatura en grados Celsius), completamente observable respecto a la variable crítica y determinista.
* **Objetivo:** Mantener la temperatura de una cámara dentro de un rango seguro $[T_{min}, T_{max}]$, aproximándola a una temperatura objetivo ($T_{target}$).

### 2.2. Modelo de Decisión y Ciclo de Ejecución
El agente ejecuta un comportamiento cíclico (`ControlTemperaturaBehaviour`) derivado de la clase base `Behaviour` de JADE:

1. **Fase de Percepción:** Llama a `client.refreshThermostatTemperature(state)` para obtener la temperatura actual del sensor.
2. **Fase de Decisión (Regla Reactiva):**
   * Si $T_{actual} > T_{target} \implies$ Acción: `REFRIGERATION_ON` (Encender refrigeración).
   * Si $T_{actual} \le T_{target} \implies$ Acción: `REFRIGERATION_OFF` (Apagar refrigeración).
3. **Fase de Acción:** Envía el comando mediante `POST /api/v1/runs/{id}/actions` con el enum tipado `ThermostatAction`.
4. **Condición de Parada:** Cuando el servidor de DBA Lab notifica que el run ha pasado a estado terminal (`COMPLETED`, `FAILED`, etc.), `done()` devuelve `true` y el agente invoca `doDelete()`.

---

## 3. AGENTE 2: EL NAVEGADOR DEL LABERINTO (`GridAgent`)

### 3.1. Tipología y Desafío del Entorno
* **Tipo de Agente:** Cognitivo, deliberativo, con estado interno y memoria espacial.
* **Entorno:** Rejilla 2D discreta, parcialmente observable (*niebla de guerra*), determinista pero desconocido a priori.
* **Sensores Disponibles:**
  * `grid.position`: Coordenadas cartesianas actuales $(x, y)$.
  * `grid.target`: Coordenadas estáticas de la meta $(x_G, y_G)$.
  * `grid.bumpers`: Sensores de colisión en las 4 direcciones cardinales (Norte, Sur, Este, Oeste). Detectan si la casilla inmediatamente adyacente es un muro antes de chocar.
  * `grid.energy`: Batería restante y número de pasos físicos realizados.
* **Acciones Físicas:** `grid.move.north`, `grid.move.east`, `grid.move.south`, `grid.move.west`. Cada paso consume 1 unidad de energía. Chocar contra un muro consume energía pero no desplaza al robot.

### 3.2. El Ciclo Formal de Agentes con Estado Interno (Tema 2)
`GridAgent` implementa estrictamente las funciones teóricas de la arquitectura de agentes cognitivos:

```
Percepción sensorial (see)
         │
         ▼
Actualización del Estado Interno (next) ──► MentalMap (Mapa Disperso)
         │
         ▼
Deliberación y Planificación (action)   ──► PathPlanner (Online A*)
         │
         ▼
Validación de Seguridad y Ejecución Fís. ──► DbaClient (HTTP)
```

1. **Fase `next` (Actualización Cognitiva):**
   El agente traslada las percepciones de los bumpers a su mapa mental (`MentalMap.updateFromPerceptions`). La casilla actual se confirma como libre (`FREE`) y se incrementa su contador de visitas. Las casillas adyacentes bloqueadas por bumpers se catalogan como obstáculos (`OBSTACLE`), y las despejadas como `FREE`.
2. **Fase de Comprobación de Guardas:**
   Verifica si ya se alcanzó la meta o si la batería se ha agotado. Si la batería llega a 0, se realiza una parada controlada sin intentar movimientos infructuosos.
3. **Fase `action` (Deliberación $A^*$ y Prevención de Choque):**
   El planificador calcula la ruta óptima teórica. Antes de transmitir el movimiento por red, se comprueba una regla de seguridad: `bumpers.isSafe(candidate)`. Si hay un obstáculo imprevisto, se corrige el mapa mental y se replanifica en memoria en 0 pasos físicos.
4. **Fase `see` (Sensórica Post-Paso):**
   Tras ejecutarse el movimiento físico en el servidor, se refrescan los sensores dinámicos (posición, bumpers y energía) para el siguiente ciclo.

---

## 4. LA MEMORIA ESPACIAL: `MentalMap`

### 4.1. Almacenamiento Disperso (*Sparse Map*)
En lugar de matrices fijas (ej. `CellType[10][10]`), utiliza tablas hash:
* `Map<GridPosition, CellType> cells = new HashMap<>()`: Coordenadas descubiertas.
* `Map<GridPosition, Integer> visitCounts = new HashMap<>()`: Frecuencia de visitas por coordenada.

**Ventajas clave:**
* **Independencia de escala:** Soporta indistintamente mapas de $10 \times 10$, de $50 \times 50$ o mundos abiertos sin dimensiones declaradas.
* **Eficiencia de memoria:** Solo almacena casillas exploradas o en el rango de los bumpers.

### 4.2. La Hipótesis del Mundo Libre (*Optimistic Assumption*)
Cualquier casilla no percibida tiene el estado `UNKNOWN`. El método `isTransitable(pos)` establece:
$$\text{Transitable}(pos) \iff \text{Tipo}(pos) \neq \text{OBSTACLE}$$
El agente es optimista: asume que la niebla de guerra está despejada hasta que un sensor físico demuestre lo contrario. Esto le permite trazar planes tentativos directos hacia la meta.

---

## 5. EL CEREBRO PLANIFICADOR: `PathPlanner` Y EL ALGORITMO $A^*$

### 5.1. Búsqueda Heurística Admisible
Para encontrar el camino más corto en una cuadrícula ortogonal con movimientos en 4 direcciones de coste unitario ($g(n) = g(padre) + 1$), la heurística teórica ideal es la **Distancia Manhattan**:
$$h(n) = |x_n - x_G| + |y_n - y_G|$$
$$f(n) = g(n) + h(n)$$

Al ser una distancia en línea recta ortogonal sin diagonales, $h(n)$ nunca sobreestima el coste real (es **admisible** y **consistente**), garantizando que la primera solución que extrae de la cola de prioridad es óptima en el mapa mental conocido.

### 5.2. Escalera Canónica de Desempate (Rompe-Bucles)
En laberintos complejos (ej. muros cóncavos en forma de U o C), muchos nodos comparten idéntico coste $f(n)$. Para evitar oscilaciones infinitas, la clase `SearchNode` implementa un criterio de desempate en cuatro niveles:

1. **Menor coste $f(n)$:** Principio estándar de $A^*$.
2. **Menor distancia estimada $h(n)$:** A igualdad de coste total, favorece acercarse físicamente a la meta.
3. **Menor número de visitas previas (`visits`):** **Mecanismo estrella.** Si una casilla ya fue pisada 2 veces y otra adyacente 0 veces, se prioriza la de 0 visitas. Esto fuerza al agente a salir de trampas y callejones sin salida sin quedar atrapado en ciclos.
4. **Mayor certidumbre:** Prefiere celdas confirmadas (`FREE`) frente a celdas no exploradas (`UNKNOWN`).

---

## 6. LA CAPA DE COMUNICACIÓN ROBUSTA: `DbaClient`

### 6.1. Idempotencia y Tolerancia a Fallos
En un sistema multiagente distribuido con latencia o caídas de red, reenviar un `POST /actions` ante un fallo de conexión puede ser catastrófico (el robot ejecutaría dos pasos, gastaría doble energía o chocaría contra una pared ya superada).

* **Peticiones Pasivas (`GET`):** Marcadas con `retryable = true`. Reintentan automáticamente hasta 3 veces con retroceso exponencial (*exponential backoff*).
* **Acciones Físicas (`POST`):** Marcadas con `retryable = false`. Si la respuesta es incierta (HTTP 503 o corte de red):
  1. No se repite el movimiento físico.
  2. Se activa una rutina de auditoría: `recoverControlAfterUncertainAction`.
  3. Se consultan de forma pasiva `GET /runs/{id}` y `GET /runs/{id}/actions/last` para comprobar si el servidor procesó el movimiento antes del corte de conexión.

---

## 7. TABLA COMPARATIVA: TERMOSTATO VS GRID 2D

| Dimensión | Agente Termostato (`TermostatoAgent`) | Agente Navegador (`GridAgent`) |
| :--- | :--- | :--- |
| **Arquitectura** | Reactivo Puro (sin estado interno) | Cognitivo / Deliberativo (con estado interno) |
| **Observabilidad** | Total (conoce la variable del entorno) | Parcial (niebla de guerra + bumpers locales) |
| **Memoria Espacial** | Inexistente (no almacena histórico) | `MentalMap` (mapa disperso con visitas) |
| **Algoritmo Central** | Umbral simple ($T > T_{target}$) | $A^*$ Optimista en línea con Heurística Manhattan |
| **Gestión de Bucles** | No aplica (estado escalar convergente) | Penalización por contador de visitas en desempates |
| **Prevención de Choques**| No aplica | Validación previa en memoria con `bumpers.isSafe()` |

---

## 8. PREGUNTAS FRECUENTES PARA LA DEFENSA (IDEAL PARA PODCAST / VÍDEO)

### ¿Por qué se utiliza $A^*$ en lugar de algoritmos ciegos como DFS o BFS?
BFS encuentra el camino más corto pero explora en círculos concéntricos consumiendo memoria exponencial. DFS puede descender por ramas infinitas y requerir retroceso físico continuo (*backtracking*), agotando la batería. $A^*$ con heurística Manhattan enfoca la búsqueda directamente hacia la meta y replanifica en memoria a coste cero.

### ¿Qué ocurre si un muro bloquea el camino que $A^*$ había planeado?
El agente nunca llega a chocar. El método `selectValidatedSafeAction` coteja la dirección planificada contra el sensor `bumpers`. Si detecta discrepancia, actualiza el `MentalMap` marcando el obstáculo y vuelve a llamar a $A^*$ en memoria en ese mismo milisegundo.

### ¿Cómo garantiza el agente una salida limpia en JADE?
En el método `takeDown()` de JADE, si el run en el servidor sigue activo, el agente emite un `client.closeRun(runId)`. Esto asegura que no queden ejecuciones huérfanas en DBA Lab y que el contenedor JADE pueda apagarse ordenadamente.
