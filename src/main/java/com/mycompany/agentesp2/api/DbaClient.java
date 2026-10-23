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
import java.util.Properties;
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

    private final HttpClient http;
    private final String baseUrl;
    private final String token;
    private volatile long currentActiveRunId = -1L;
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    public DbaClient(String baseUrl, String token) {
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

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        registerJvmShutdownHook();
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

        if (url == null || token == null) {
            Path configPath = Paths.get("config.properties");
            if (Files.exists(configPath)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                    if (url == null) url = props.getProperty("DBA_LAB_URL");
                    if (token == null) token = props.getProperty("DBA_TOKEN");
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

        return new DbaClient(url, token);
    }

    public String getBaseUrl() {
        return baseUrl;
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

    /**
     * Inicializa el estado completo de percepción para un run del entorno Grid 2D.
     * Consulta posición inicial, celda objetivo, bumpers y saldo inicial de energía.
     */
    public GridState initGridState(long runId) throws DbaException {
        JSONObject posJson = getPerception(runId, "grid.position");
        JSONObject targetJson = getPerception(runId, "grid.target");
        JSONObject bumpersJson = getPerception(runId, "grid.bumpers");
        JSONObject energyJson = getPerception(runId, "grid.energy");
        RunControl ctrl = RunControl.fromJson(posJson);
        GridState state = GridState.fromInitialPerceptions(posJson, targetJson, bumpersJson, energyJson, ctrl);
        trackControlState(state.getControl());
        return state;
    }

    /**
     * Refresca los sensores dinámicos de Grid 2D (posición, bumpers y energía).
     * Nota: 'grid.target' es estático y no cambia a lo largo del reto.
     */
    public void refreshGridState(GridState state) throws DbaException {
        long runId = state.getControl().getRunId();
        JSONObject posJson = getPerception(runId, "grid.position");
        state.updatePosition(posJson);

        JSONObject bumpersJson = getPerception(runId, "grid.bumpers");
        state.updateBumpers(bumpersJson);

        JSONObject energyJson = getPerception(runId, "grid.energy");
        state.updateEnergy(energyJson);

        trackControlState(state.getControl());
    }

    /**
     * Envía una acción de movimiento en Grid 2D usando el enum tipado GridAction.
     */
    public RunControl sendGridAction(long runId, GridAction action) throws DbaException {
        JSONObject response = sendAction(runId, action.getActionCode());
        RunControl ctrl = RunControl.fromJson(response);
        trackControlState(ctrl);
        return ctrl;
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

        String body = sendRequest(request);
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

        String body = sendRequest(request);
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

        String body = sendRequest(request);
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

        String body = sendRequest(request);
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

        String body = sendRequest(request);
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
            String body = sendRequest(request);
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
    // MÉTODOS AUXILIARES HTTP
    // ==========================================

    private String sendRequest(HttpRequest request) throws DbaException {
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
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DbaException("Error de conexión con DBA Lab: " + e.getMessage(), e);
        }
    }

    private JSONObject parseJsonObject(String jsonStr) throws DbaException {
        try {
            return new JSONObject(jsonStr);
        } catch (JSONException e) {
            throw new DbaException("Respuesta no válida de DBA Lab: " + jsonStr, e);
        }
    }
}
