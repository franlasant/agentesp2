package com.mycompany.agentesp2;

import com.mycompany.agentesp2.api.DbaClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Punto de entrada principal para la Práctica 2 de DBA (Termostato y Grid 2D).
 *
 * Soporta dos arquitecturas de despliegue JADE:
 * 1. Arquitectura distribuida en dos contenedores (recomendada por el profesor):
 *    - Terminal 1: './run.sh server' levanta el Main-Container con su GUI de forma persistente.
 *    - Terminal 2: './run.sh grid 1' detecta automáticamente el Main-Container en localhost:1099
 *      y lanza el agente en un contenedor secundario ligero (-container), sin abrir otra GUI.
 * 2. Modo autónomo (fallback automático):
 *    - Si no hay un Main-Container escuchando en localhost:1099, levanta la plataforma completa.
 */
public class Main {

    public static final String DEFAULT_JADE_HOST = "localhost";
    public static final int DEFAULT_JADE_PORT = 1099;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   DBA Lab - Práctica 2: Termostato y Navegación Grid 2D    ");
        System.out.println("============================================================");

        String mode = (args != null && args.length > 0) ? args[0].trim().toLowerCase() : null;

        // Modo 1: Levantar únicamente el servidor/plataforma JADE (Main-Container persistente)
        if ("server".equalsIgnoreCase(mode) || "platform".equalsIgnoreCase(mode)) {
            startJadeMainContainerServer();
            return;
        }

        // Verificar conectividad y mostrar retos anunciados por DBA Lab
        try {
            DbaClient client = DbaClient.fromEnvOrConfig();
            System.out.println("URL configurada: " + client.getBaseUrl());

            JSONArray challenges = client.getChallenges();
            System.out.println("Conexión con DBA Lab correcta. Retos disponibles: " + challenges.length());
            int gridIndex = 0;
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.getJSONObject(i);
                String envType = ch.optString("environmentType", "UNKNOWN");
                String tag = "GRID_2D".equalsIgnoreCase(envType) ? " [GRID #" + (++gridIndex) + "]" : "";
                System.out.printf("  - ID %d [%s]%s %s | sensores=%s | acciones=%s%n",
                        ch.getLong("id"),
                        envType,
                        tag,
                        ch.optString("name", ch.optString("code", "")),
                        ch.optJSONArray("sensorCodes"),
                        ch.optJSONArray("actionCodes"));
            }
            System.out.println("------------------------------------------------------------");
        } catch (Exception e) {
            System.err.println("AVISO: No se pudo verificar la conexión inicial con DBA Lab: " + e.getMessage());
            System.err.println("Asegúrate de tener activa la red UGR / VPN y configuradas DBA_LAB_URL y DBA_TOKEN.");
            System.out.println("------------------------------------------------------------");
        }

        // Determinar qué agente lanzar (por argumento, variable de entorno o menú interactivo)
        String challengeArg = (args != null && args.length > 1)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim()
                : null;

        if (mode == null || mode.isEmpty()) {
            mode = promptUserModeSelection();
        }

        if ("server".equalsIgnoreCase(mode) || "platform".equalsIgnoreCase(mode)) {
            startJadeMainContainerServer();
            return;
        }

        if (challengeArg == null && !"termostato".equalsIgnoreCase(mode) && System.console() != null) {
            System.out.print("Introduce el número del reto Grid (1, 2, 3...) [1 por defecto]: ");
            try {
                Scanner scanner = new Scanner(System.in);
                if (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        challengeArg = line;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String agentSpec;
        long timestamp = System.currentTimeMillis() % 100000;
        if ("termostato".equalsIgnoreCase(mode) || "thermostat".equalsIgnoreCase(mode) || "1".equals(mode)) {
            System.out.println("Preparando TermostatoAgent...");
            String agentName = "termostato_" + timestamp;
            agentSpec = agentName + ":com.mycompany.agentesp2.termostato.TermostatoAgent"
                    + (challengeArg != null && !challengeArg.isEmpty() ? "(" + challengeArg + ")" : "");
        } else {
            System.out.println("Preparando GridAgent...");
            String agentName = "grid_" + timestamp;
            agentSpec = agentName + ":com.mycompany.agentesp2.grid.GridAgent"
                    + (challengeArg != null && !challengeArg.isEmpty() ? "(" + challengeArg + ")" : "");
        }

        launchAgentInJade(agentSpec);
    }

    /**
     * Arranca el Main-Container persistente con GUI (equivalente a lanzar_server_dba.sh).
     */
    private static void startJadeMainContainerServer() {
        if (isMainContainerActive(DEFAULT_JADE_HOST, DEFAULT_JADE_PORT)) {
            System.out.println("ℹ️ El Main-Container de JADE ya está activo en "
                    + DEFAULT_JADE_HOST + ":" + DEFAULT_JADE_PORT + ".");
            return;
        }
        System.out.println("🚀 Levantando el Main-Container de JADE (Servidor de plataforma) en "
                + DEFAULT_JADE_HOST + ":" + DEFAULT_JADE_PORT + "...");
        System.out.println("👉 Deja esta terminal abierta y lanza tus agentes en otra terminal con: ./run.sh grid 1");

        boolean useGui = !"false".equalsIgnoreCase(System.getenv("JADE_GUI"));
        String[] jadeArgs = useGui
                ? new String[]{"-gui", "-name", "dba_server", "-local-host", DEFAULT_JADE_HOST, "-local-port", String.valueOf(DEFAULT_JADE_PORT)}
                : new String[]{"-name", "dba_server", "-local-host", DEFAULT_JADE_HOST, "-local-port", String.valueOf(DEFAULT_JADE_PORT)};

        jade.Boot.main(jadeArgs);
    }

    /**
     * Lanza el agente conectándose como contenedor secundario (-container) si el Main-Container
     * ya está corriendo, o levanta un Main-Container autónomo si no lo está.
     */
    private static void launchAgentInJade(String agentSpec) {
        boolean forceStandalone = "true".equalsIgnoreCase(System.getenv("JADE_STANDALONE"));
        if (!forceStandalone && isMainContainerActive(DEFAULT_JADE_HOST, DEFAULT_JADE_PORT)) {
            System.out.println("🔗 Detectado Main-Container activo en " + DEFAULT_JADE_HOST + ":" + DEFAULT_JADE_PORT
                    + ". Lanzando agente en contenedor secundario ligero (-container)...");
            System.setProperty("JADE_PERIPHERAL_CONTAINER", "true");
            String[] jadeArgs = new String[]{
                "-container",
                "-host", DEFAULT_JADE_HOST,
                "-port", String.valueOf(DEFAULT_JADE_PORT),
                "-agents", agentSpec
            };
            jade.Boot.main(jadeArgs);
        } else {
            System.out.println("ℹ️ No se detectó Main-Container previo en " + DEFAULT_JADE_HOST + ":" + DEFAULT_JADE_PORT
                    + ". Iniciando contenedor autónomo (puedes usar './run.sh server' en otra terminal para dejarlo fijo)...");
            System.setProperty("JADE_PERIPHERAL_CONTAINER", "false");
            boolean useGui = !"false".equalsIgnoreCase(System.getenv("JADE_GUI"));
            String[] jadeArgs = useGui
                    ? new String[]{"-gui", "-local-host", DEFAULT_JADE_HOST, "-local-port", String.valueOf(DEFAULT_JADE_PORT), "-agents", agentSpec}
                    : new String[]{"-local-host", DEFAULT_JADE_HOST, "-local-port", String.valueOf(DEFAULT_JADE_PORT), "-agents", agentSpec};
            jade.Boot.main(jadeArgs);
        }
    }

    /**
     * Comprueba mediante un socket TCP rápido si ya hay un Main-Container de JADE escuchando en host:port.
     */
    public static boolean isMainContainerActive(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 250);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String promptUserModeSelection() {
        String envMode = System.getenv("AGENT_MODE");
        if (envMode != null && !envMode.isBlank()) {
            return envMode.trim().toLowerCase();
        }
        if (System.console() == null) {
            return "grid";
        }
        System.out.println("Selecciona qué deseas ejecutar:");
        System.out.println("  [0] Levantar solo el Main-Container JADE (Servidor GUI persistente)");
        System.out.println("  [1] TermostatoAgent (Bloque 1)");
        System.out.println("  [2] GridAgent       (Bloque 2 - por defecto)");
        System.out.print("Opción [2]: ");
        try {
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if ("0".equals(line) || "server".equalsIgnoreCase(line)) {
                    return "server";
                }
                if ("1".equals(line) || "termostato".equalsIgnoreCase(line)) {
                    return "termostato";
                }
            }
        } catch (Exception ignored) {
        }
        return "grid";
    }
}
