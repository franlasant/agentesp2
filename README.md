# agentesp2 - Práctica 2: Percepción y acción en entornos virtuales

Proyecto para la asignatura de **Desarrollo Basado en Agentes (DBA)** de la Universidad de Granada (Curso 2026/2027).

Implementación de agentes inteligentes en **JADE** que interactúan con el laboratorio remoto **DBA Lab** para resolver los retos del **Termostato** y la **Navegación 2D (Grid)**.

---

## 🚀 Requisitos previos

1. **Java:** OpenJDK 17 o superior (desarrollado con Java 26).
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
src/main/java/com/mycompany/agentesp2/
├── Main.java              # Lanzador principal en NetBeans (comprueba conexión y arranca JADE)
├── TestConexion.java      # Test rápido de conexión y listado de retos sin consumir runs
├── api/
│   ├── DbaClient.java     # Cliente REST desacoplado para DBA Lab
│   └── DbaException.java  # Manejo de excepciones y códigos HTTP
├── termostato/
│   └── TermostatoAgent.java # Agente JADE para el control de temperatura
└── grid/
    └── GridAgent.java     # Plantilla para el reto de Navegación 2D
```

---

## ▶️ Ejecución

### Desde NetBeans
- **Comprobar conexión:** Clic derecho en `TestConexion.java` -> **Run File** (`Shift + F6`).
- **Ejecutar agente:** Abrir `Main.java` y pulsar **Run** (`F6`).

### Desde la terminal
```bash
# Probar conexión
mvn compile exec:java -Dexec.mainClass="com.mycompany.agentesp2.TestConexion"

# Ejecutar TermostatoAgent
mvn compile exec:java
```
