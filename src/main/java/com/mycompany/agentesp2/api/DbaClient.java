package com.mycompany.agentesp2.api;

import com.mycompany.agentesp2.model.RunControl;
import com.mycompany.agentesp2.model.ThermostatAction;
import com.mycompany.agentesp2.model.ThermostatState;
import com.mycompany.agentesp2.model.grid.GridAction;
import com.mycompany.agentesp2.model.grid.GridState;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cliente HTTP desacoplado y reutilizable para la API de DBA Lab.
 * Incluye protección en doble capa frente a cierres abruptos del programa:
 * 1. ShutdownHook de la JVM para cerrar el run al pulsar Stop / Ctrl+C.
 * 2. Persistencia local en '.active_run_id' para cerrar sesiones huérfanas al reiniciar.
 */
public class DbaClient {

    private static final Path ACTIVE_RUN_FILE = Paths.get(".active_run_id");

    /** Delay por defecto entre peticiones a DBA Lab (al menos 0.5 segundos / 500 ms) */
    public static final long DEFAULT_REQUEST_DELAY_MS = 500L;

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private final long requestDelayMs;
    private volatile long currentActiveRunId = -1L;
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    public DbaClient(String baseUrl, String token) {
        this(baseUrl, token, DEFAULT_REQUEST_DELAY_MS);
    }

    public DbaClient(String baseUrl, String token, long requestDelayMs) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("La URL de DBA Lab no puede ser nula ni vacía.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de DBA Lab no puede ser nulo ni vacío.");
        }

        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.token = token.trim();
        this.requestDelayMs = Math.max(0L, requestDelayMs);

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        registerJvmShutdownHook();
        System.out.println("[DbaClient] Cliente inicializado (" + this.baseUrl + "). Delay entre peticiones: "
                + this.requestDelayMs + " ms (protección térmica del servidor).");
    }

    /**
     * Registra un gancho de apagado en la JVM para que, si el usuario detiene el programa
     * antes de que termine (Ctrl+C o cierre de ventana), se cierre automáticamente el run en el servidor.
     */
    private void registerJvmShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                long runToClose = this.currentActiveRunId;
                if (runToClose <= 0) {
                    runToClose = readSavedRunId();
                }
                if (runToClose > 0) {
                    System.out.println("\n[ShutdownHook] Aborto detectado. Cerrando automáticamente el run " + runToClose + " en DBA Lab...");
                    try {
                        closeRun(runToClose);
                        System.out.println("[ShutdownHook] Run " + runToClose + " cerrado correctamente en el servidor.");
                    } catch (Exception e) {
                        // Si ya estaba cerrado o expirado, limpiamos el archivo local igualmente
                        clearSavedRunId();
                    }
                }
            }, "dba-lab-run-closer"));
        }
    }

    /**
     * Constructor por defecto que busca la configuración en variables de entorno o en config.properties.
     */
    public static DbaClient fromEnvOrConfig() {
        String url = System.getenv("DBA_LAB_URL");
        String token = System.getenv("DBA_TOKEN");
        String delayStr = System.getenv("DBA_REQUEST_DELAY_MS");
        if (delayStr == null) {
            delayStr = System.getenv("DBA_DELAY_MS");
        }

        if (url == null || token == null || delayStr == null) {
            Path configPath = Paths.get("config.properties");
            if (Files.exists(configPath)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                    if (url == null) url = props.getProperty("DBA_LAB_URL");
                    if (token == null) token = props.getProperty("DBA_TOKEN");
                    if (delayStr == null) {
                        delayStr = props.getProperty("DBA_REQUEST_DELAY_MS", props.getProperty("DBA_DELAY_MS"));
                    }
                } catch (IOException ignored) {
                }
            }
        }

        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException(
                "Faltan credenciales de DBA Lab. Configura las variables de entorno DBA_LAB_URL y DBA_TOKEN " +
                "o crea un archivo config.properties en la raíz del proyecto."
            );
        }

        long delayMs = DEFAULT_REQUEST_DELAY_MS;
        if (delayStr != null && !delayStr.isBlank()) {
            try {
                delayMs = Long.parseLong(delayStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        return new DbaClient(url, token, delayMs);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public long getRequestDelayMs() {
        return requestDelayMs;
    }

    // ==========================================
    // MÉTODOS DE ALTO NIVEL (TIPADOS CON DTOs)
    // ==========================================

    /**
     * Busca automáticamente el ID del primer reto disponible para el tipo de entorno indicado.
     */
    public long findChallengeIdByType(String environmentType) throws DbaException {
        JSONArray challenges = getChallenges();
        for (int i = 0; i < challenges.length(); i++) {
            JSONObject ch = challenges.getJSONObject(i);
            if (environmentType.equalsIgnoreCase(ch.optString("environmentType"))) {
                return ch.getLong("id");
            }
        }
        return -1L;
    }

    /**
     * Inicia un run y devuelve directamente el objeto de control tipado.
     * Si quedó un run abierto de una ejecución anterior abortada, lo cierra antes automáticamente.
     */
    public RunControl startRunTyped(long challengeId) throws DbaException {
        JSONObject response = startRun(challengeId);
        return RunControl.fromJson(response);
    }

    /**
     * Obtiene los parámetros estáticos iniciales del termostato (límites y objetivo) en un objeto ThermostatState.
     */
    public ThermostatState initThermostatState(long runId) throws DbaException {
        JSONObject limitsJson = getPerception(runId, "thermostat.limits");
        JSONObject targetJson = getPerception(runId, "thermostat.target");
        ThermostatState state = ThermostatState.fromInitialPerceptions(limitsJson, targetJson);
        trackControlState(state.getControl());
        return state;
    }

    /**
     * Consulta el sensor de temperatura y actualiza directamente el objeto ThermostatState.
     */
    public void refreshThermostatTemperature(ThermostatState state) throws DbaException {
        long runId = state.getControl().getRunId();
        JSONObject tempJson = getPerception(runId, "thermostat.temperature");
        state.updateFromTemperatureJson(tempJson);
        trackControlState(state.getControl());
    }

    /**
     * Envía una acción del termostato usando el enum tipado y devuelve el nuevo estado de control.
     */
    public RunControl sendAction(long runId, ThermostatAction action) throws DbaException {
        JSONObject response = sendAction(runId, action.getActionCode());
        RunControl ctrl = RunControl.fromJson(response);
        trackControlState(ctrl);
        return ctrl;
    }

    // ==========================================
    // MÉTODOS DE ALTO NIVEL PARA GRID 2D
    // ==========================================

    private static final Set<String> REQUIRED_GRID_SENSORS = Set.of(
            "grid.position", "grid.bumpers", "grid.target", "grid.energy"
    );

    private static final Set<String> REQUIRED_GRID_ACTIONS = Set.of(
            "grid.move.north", "grid.move.east", "grid.move.south", "grid.move.west"
    );

    /**
     * Devuelve la lista de retos disponibles cuyo environmentType es "GRID_2D".
     */
    public List<JSONObject> findGridChallenges() throws DbaException {
        JSONArray all = getChallenges();
        List<JSONObject> gridList = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject ch = all.getJSONObject(i);
            if ("GRID_2D".equalsIgnoreCase(ch.optString("environmentType"))) {
                gridList.add(ch);
            }
        }
        return gridList;
    }

    /**
     * Selecciona un reto GRID_2D sin fijar IDs numéricos en el código, validando sus capacidades.
     * Soporta:
     * - null o vacío: devuelve el primer reto GRID_2D anunciado.
     * - "#1", "#2" o índice 1..N (si no coincide con un ID directo): selecciona el enésimo reto GRID_2D.
     * - ID exacto anunciado en /challenges o fragmento de nombre/código del reto (ej. "abierto", "vertical").
     */
    public JSONObject resolveGridChallenge(String selector) throws DbaException {
        List<JSONObject> gridChallenges = findGridChallenges();
        if (gridChallenges.isEmpty()) {
            return null;
        }

        JSONObject chosen = null;
        if (selector == null || selector.isBlank()) {
            chosen = gridChallenges.get(0);
        } else {
            String clean = selector.trim();
            if (clean.startsWith("#")) {
                clean = clean.substring(1).trim();
            }

            // 0. Si se especifica explícitamente id:<N> o id=<N>
            if (clean.toLowerCase().startsWith("id:") || clean.toLowerCase().startsWith("id=")) {
                try {
                    long targetId = Long.parseLong(clean.substring(3).trim());
                    for (JSONObject ch : gridChallenges) {
                        if (ch.getLong("id") == targetId) {
                            chosen = ch;
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // 1. Si es un número entero: dar prioridad absoluta al orden del reto Grid (1, 2, 3...)
            if (chosen == null) {
                try {
                    int num = Integer.parseInt(clean);
                    if (num >= 1 && num <= gridChallenges.size()) {
                        chosen = gridChallenges.get(num - 1);
                    } else {
                        // Si el número está fuera del rango 1..N, buscar si coincide con un ID exacto de la base de datos
                        for (JSONObject ch : gridChallenges) {
                            if (ch.getLong("id") == num) {
                                chosen = ch;
                                break;
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // 2. Si no es un número o no se encontró, buscar por coincidencia parcial de texto
            if (chosen == null) {
                String lower = clean.toLowerCase();
                for (JSONObject ch : gridChallenges) {
                    String text = (ch.optString("name", "") + " "
                            + ch.optString("code", "") + " "
                            + ch.optString("title", "") + " "
                            + ch.optString("description", "")).toLowerCase();
                    if (text.contains(lower)) {
                        chosen = ch;
                        break;
                    }
                }
            }
        }

        if (chosen != null) {
            validateGridCapabilities(chosen);
        }
        return chosen;
    }

    /**
     * Comprueba que el reto anuncia los sensorCodes y actionCodes requeridos para Grid 2D.
     */
    public void validateGridCapabilities(JSONObject challenge) throws DbaException {
        JSONArray sensors = challenge.optJSONArray("sensorCodes");
        JSONArray actions = challenge.optJSONArray("actionCodes");

        if (sensors != null) {
            List<String> announcedSensors = new ArrayList<>();
            for (int i = 0; i < sensors.length(); i++) {
                announcedSensors.add(sensors.getString(i));
            }
            for (String req : REQUIRED_GRID_SENSORS) {
                if (!announcedSensors.contains(req)) {
                    throw new DbaException(422, "MISSING_SENSOR_CAPABILITY",
                            "El reto no anuncia el sensor requerido: " + req);
                }
            }
        }

        if (actions != null) {
            List<String> announcedActions = new ArrayList<>();
            for (int i = 0; i < actions.length(); i++) {
                announcedActions.add(actions.getString(i));
            }
            for (String req : REQUIRED_GRID_ACTIONS) {
                if (!announcedActions.contains(req)) {
                    throw new DbaException(422, "MISSING_ACTION_CAPABILITY",
                            "El reto no anuncia la acción requerida: " + req);
                }
            }
        }
    }

    /**
     * Lee las 4 percepciones iniciales del entorno Grid 2D (position, target, bumpers, energy)
     * comprobando control.runStatus tras cada interacción.
     */
    public GridState initGridState(long runId) throws DbaException {
        JSONObject posJson = getPerception(runId, "grid.position");
        RunControl ctrl = RunControl.fromJson(posJson);
        trackControlState(ctrl);
        if (ctrl.isTerminal()) {
            return new GridState(null, null, null, null, ctrl);
        }

        JSONObject targetJson = getPerception(runId, "grid.target");
        ctrl = RunControl.fromJson(targetJson);
        trackControlState(ctrl);
        if (ctrl.isTerminal()) {
            return new GridState(null, null, null, null, ctrl);
        }

        JSONObject bumpersJson = getPerception(runId, "grid.bumpers");
        ctrl = RunControl.fromJson(bumpersJson);
        trackControlState(ctrl);
        if (ctrl.isTerminal()) {
            return new GridState(null, null, null, null, ctrl);
        }

        JSONObject energyJson = getPerception(runId, "grid.energy");
        GridState state = GridState.fromInitialPerceptions(posJson, targetJson, bumpersJson, energyJson);
        trackControlState(state.getControl());
        return state;
    }

    /**
     * Refresca los sensores dinámicos del Grid (position, bumpers, energy) tras un movimiento,
     * verificando después de cada lectura si el run ha pasado a un estado terminal.
     */
    public void refreshGridState(GridState state) throws DbaException {
        long runId = state.getControl().getRunId();

        JSONObject posJson = getPerception(runId, "grid.position");
        state.updatePosition(posJson);
        trackControlState(state.getControl());
        if (state.getControl().isTerminal()) {
            return;
        }

        JSONObject bumpersJson = getPerception(runId, "grid.bumpers");
        state.updateBumpers(bumpersJson);
        trackControlState(state.getControl());
        if (state.getControl().isTerminal()) {
            return;
        }

        JSONObject energyJson = getPerception(runId, "grid.energy");
        state.updateEnergy(energyJson);
        trackControlState(state.getControl());
    }

    /**
     * Envía una acción de movimiento cardinal en el Grid.
     * Regla de idempotencia: si se pierde la respuesta o se recibe 503 (resultado no confirmado),
     * NO se reintenta el POST automáticamente; se consulta GET /runs/{runId} y GET /runs/{runId}/actions/last.
     */
    public RunControl sendGridAction(long runId, GridAction action) throws DbaException {
        try {
            JSONObject response = sendAction(runId, action.getActionCode());
            RunControl ctrl = RunControl.fromJson(response);
            trackControlState(ctrl);
            return ctrl;
        } catch (DbaException e) {
            if (e.getStatusCode() == 503 || e.getStatusCode() == -1) {
                System.err.println("[DbaClient] Respuesta incierta (" + e.getErrorCode()
                        + ") al enviar acción. Auditando estado del run y última acción sin reintentar POST...");
                return recoverControlAfterUncertainAction(runId, e);
            }
            throw e;
        }
    }

    private RunControl recoverControlAfterUncertainAction(long runId, DbaException originalError) throws DbaException {
        try {
            JSONObject statusJson = getRunStatus(runId);
            RunControl statusCtrl = RunControl.fromJson(statusJson);
            trackControlState(statusCtrl);

            try {
                JSONObject lastActionJson = getLastAction(runId);
                RunControl lastCtrl = RunControl.fromJson(lastActionJson);
                trackControlState(lastCtrl);
                return lastCtrl;
            } catch (DbaException ignored) {
                return statusCtrl;
            }
        } catch (DbaException recoveryEx) {
            throw originalError;
        }
    }

    // ==========================================
    // MÉTODOS DE BAJO NIVEL (JSON CRUDO)
    // ==========================================

    /**
     * Descubre los retos disponibles en el laboratorio.
     * GET /api/v1/challenges
     */
    public JSONArray getChallenges() throws DbaException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/challenges"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        String body = sendRequest(request, true);
        try {
            String trimmed = body.trim();
            if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                if (obj.has("challenges")) {
                    return obj.getJSONArray("challenges");
                }
            }
            return new JSONArray(trimmed);
        } catch (JSONException e) {
            throw new DbaException("Respuesta inesperada al listar retos: " + body, e);
        }
    }

    /**
     * Inicia una nueva ejecución (run) para un reto específico.
     * Si existe un run previo registrado localmente que quedó colgado tras abortar el programa,
     * lo cierra automáticamente antes de iniciar el nuevo.
     */
    public JSONObject startRun(long challengeId) throws DbaException {
        // 1. Si hay un runId guardado de una ejecución previa abortada (ej. Stop en NetBeans), cerrarlo primero
        closePreviousOrphanRunIfNeeded();

        JSONObject payload = new JSONObject();
        payload.put("challengeId", challengeId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        String body = sendRequest(request, false);
        JSONObject result = parseJsonObject(body);
        RunControl ctrl = RunControl.fromJson(result);
        trackControlState(ctrl);
        return result;
    }

    /**
     * Consulta una percepción o sensor del agente en el run especificado.
     * POST /api/v1/runs/{runId}/perceptions/{sensorId}
     */
    public JSONObject getPerception(long runId, String sensorId) throws DbaException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs/" + runId + "/perceptions/" + sensorId))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        String body = sendRequest(request, true);
        return parseJsonObject(body);
    }

    /**
     * Envía una acción física a ejecutar en el entorno.
     * POST /api/v1/runs/{runId}/actions con {"actionCode": code}
     */
    public JSONObject sendAction(long runId, String actionCode) throws DbaException {
        JSONObject payload = new JSONObject();
        payload.put("actionCode", actionCode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs/" + runId + "/actions"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        String body = sendRequest(request, false);
        return parseJsonObject(body);
    }

    /**
     * Consulta el estado de control de una ejecución sin leer un sensor.
     * GET /api/v1/runs/{runId}
     */
    public JSONObject getRunStatus(long runId) throws DbaException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs/" + runId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        String body = sendRequest(request, true);
        return parseJsonObject(body);
    }

    /**
     * Consulta el resultado de la última acción procesada en el run (auditoría e idempotencia).
     * GET /api/v1/runs/{runId}/actions/last
     */
    public JSONObject getLastAction(long runId) throws DbaException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs/" + runId + "/actions/last"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        String body = sendRequest(request, true);
        return parseJsonObject(body);
    }

    /**
     * Cierra o abandona deliberadamente una ejecución activa.
     * POST /api/v1/runs/{runId}/close
     */
    public JSONObject closeRun(long runId) throws DbaException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs/" + runId + "/close"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            String body = sendRequest(request, false);
            return parseJsonObject(body);
        } finally {
            if (this.currentActiveRunId == runId) {
                this.currentActiveRunId = -1L;
            }
            clearSavedRunId();
        }
    }

    // ==========================================
    // GESTIÓN DE SESIONES HUÉRFANAS Y ARCHIVO LOCAL
    // ==========================================

    private void closePreviousOrphanRunIfNeeded() {
        long previousRunId = readSavedRunId();
        if (previousRunId > 0) {
            System.out.println("[DbaClient] Detectado run previo sin cerrar (runId=" + previousRunId + "). Cerrándolo en el servidor...");
            try {
                closeRun(previousRunId);
                System.out.println("[DbaClient] Run previo " + previousRunId + " cerrado con éxito.");
            } catch (DbaException e) {
                System.out.println("[DbaClient] El run previo " + previousRunId + " ya no estaba activo (" + e.getErrorCode() + "). Limpiando registro local.");
                clearSavedRunId();
            }
        }
    }

    private void trackControlState(RunControl ctrl) {
        if (ctrl == null) return;
        if (ctrl.isActive() && ctrl.getRunId() > 0) {
            this.currentActiveRunId = ctrl.getRunId();
            saveRunId(ctrl.getRunId());
        } else if (ctrl.isTerminal()) {
            this.currentActiveRunId = -1L;
            clearSavedRunId();
        }
    }

    private void saveRunId(long runId) {
        try {
            Files.writeString(ACTIVE_RUN_FILE, Long.toString(runId), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private long readSavedRunId() {
        try {
            if (Files.exists(ACTIVE_RUN_FILE)) {
                String content = Files.readString(ACTIVE_RUN_FILE, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return Long.parseLong(content);
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private void clearSavedRunId() {
        try {
            Files.deleteIfExists(ACTIVE_RUN_FILE);
        } catch (IOException ignored) {
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES HTTP Y CONTROL DE TASA
    // ==========================================

    /**
     * Aplica la pausa obligatoria de seguridad antes de cada petición saliente
     * para no sobrecalentar el procesador del servidor DBA Lab.
     */
    private void applyRequestDelay() {
        if (this.requestDelayMs > 0) {
            try {
                Thread.sleep(this.requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Envía una petición HTTP con control estricto de idempotencia y retardo térmico:
     * - Aplica un retardo de al menos 0.5s (requestDelayMs) antes de cada envío al servidor.
     * - Si retryable = true (GET o lecturas pasivas): reintenta hasta 3 veces ante IOException.
     * - Si retryable = false (POST /actions, POST /runs): no reintenta; lanza DbaException (código -1)
     *   para que el llamador active la auditoría de estado sin enviar movimientos duplicados.
     */
    private String sendRequest(HttpRequest request, boolean retryable) throws DbaException {
        applyRequestDelay();
        int maxAttempts = retryable ? 3 : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String responseBody = response.body();

                if (status < 200 || status >= 300) {
                    String errorCode = "HTTP_" + status;
                    String message = responseBody;

                    try {
                        JSONObject errObj = new JSONObject(responseBody);
                        if (errObj.has("code")) errorCode = errObj.getString("code");
                        if (errObj.has("message")) message = errObj.getString("message");
                    } catch (Exception ignored) {
                    }

                    throw new DbaException(status, errorCode, message);
                }

                return responseBody;
            } catch (IOException e) {
                if (attempt >= maxAttempts) {
                    String msg = retryable
                            ? "Error de conexión con DBA Lab tras " + maxAttempts + " intentos: " + e.getMessage()
                            : "Error de conexión con DBA Lab (operación no reintentable): " + e.getMessage();
                    throw new DbaException(msg, e);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new DbaException("Operación interrumpida durante el reintento de conexión", ie);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DbaException("Operación interrumpida: " + e.getMessage(), e);
            }
        }
        throw new DbaException("Error inesperado en sendRequest");
    }

    private String sendRequest(HttpRequest request) throws DbaException {
        return sendRequest(request, false);
    }

    private JSONObject parseJsonObject(String jsonStr) throws DbaException {
        try {
            return new JSONObject(jsonStr);
        } catch (JSONException e) {
            throw new DbaException("Respuesta no válida de DBA Lab: " + jsonStr, e);
        }
    }
}
