package com.mycompany.agentesp2.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cliente HTTP desacoplado y reutilizable para la API de DBA Lab.
 * Cumple con la especificación de guia-api-dba-lab.md, guia-api-termostato.md y guia-api-grid.md.
 */
public class DbaClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String token;

    public DbaClient(String baseUrl, String token) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("La URL de DBA Lab no puede ser nula ni vacía.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token de DBA Lab no puede ser nulo ni vacío.");
        }

        // Limpiar posible barra al final de la URL
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.token = token.trim();

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Constructor por defecto que busca la configuración en variables de entorno o en config.properties.
     */
    public static DbaClient fromEnvOrConfig() {
        String url = System.getenv("DBA_LAB_URL");
        String token = System.getenv("DBA_TOKEN");

        // Intentar leer de config.properties local si falta alguna variable de entorno
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
    // MÉTODOS DE LA API DE DBA LAB
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
     * POST /api/v1/runs con {"challengeId": id}
     */
    public JSONObject startRun(long challengeId) throws DbaException {
        JSONObject payload = new JSONObject();
        payload.put("challengeId", challengeId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/runs"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        String body = sendRequest(request);
        return parseJsonObject(body);
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

        String body = sendRequest(request);
        return parseJsonObject(body);
    }

    // ==========================================
    // MÉTODOS AUXILIARES
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
