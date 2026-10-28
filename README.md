# agentesp2 - Práctica 2: Percepción y acción en entornos virtuales

Proyecto para la asignatura de **Desarrollo Basado en Agentes (DBA)** de la Universidad de Granada (Curso 2026/2027).

Implementación de un agente inteligente en **JADE** que interactúa con el laboratorio remoto **DBA Lab** para resolver el reto del **Termostato** (control térmico de una cámara frigorífica).

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
├── model/
│   ├── RunControl.java       # DTO para el objeto control de DBA Lab
│   ├── RunStatus.java        # Enum para estados del run
│   ├── ThermostatAction.java # Enum para acciones (on/off)
│   └── ThermostatState.java  # DTO con estado completo de la cámara
└── termostato/
    └── TermostatoAgent.java  # Agente JADE para el control de temperatura
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
