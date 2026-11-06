package com.mycompany.agentesp2;

import com.mycompany.agentesp2.api.DbaClient;
import java.util.Arrays;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Punto de entrada principal para la Práctica 2 de DBA (Termostato y Grid 2D).
 *
 * Modos de ejecución:
 * - Por argumento:
 *     java ... com.mycompany.agentesp2.Main grid [selector_reto]
 *     java ... com.mycompany.agentesp2.Main termostato [challenge_id]
 * - Sin argumentos (interactivo desde IDE o consola):
 *     Muestra los retos disponibles y permite elegir Termostato o Grid.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("   DBA Lab - Práctica 2: Termostato y Navegación Grid 2D    ");
        System.out.println("============================================================");

        // 1. Verificar conectividad y mostrar retos anunciados por DBA Lab
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

        // 2. Determinar qué agente lanzar (por argumento, variable de entorno o menú interactivo)
        String mode = (args != null && args.length > 0) ? args[0].trim().toLowerCase() : null;
        String challengeArg = (args != null && args.length > 1)
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim()
                : null;

        if (mode == null || mode.isEmpty()) {
            mode = promptUserModeSelection();
        }

        boolean useGui = !"false".equalsIgnoreCase(System.getenv("JADE_GUI"));
        String agentSpec;

        if ("termostato".equalsIgnoreCase(mode) || "thermostat".equalsIgnoreCase(mode) || "1".equals(mode)) {
            System.out.println("Iniciando contenedor JADE con TermostatoAgent...");
            agentSpec = "termostato:com.mycompany.agentesp2.termostato.TermostatoAgent"
                    + (challengeArg != null && !challengeArg.isEmpty() ? "(" + challengeArg + ")" : "");
        } else {
            System.out.println("Iniciando contenedor JADE con GridAgent...");
            agentSpec = "grid:com.mycompany.agentesp2.grid.GridAgent"
                    + (challengeArg != null && !challengeArg.isEmpty() ? "(" + challengeArg + ")" : "");
        }

        String[] jadeArgs = useGui
                ? new String[]{"-gui", "-agents", agentSpec}
                : new String[]{"-agents", agentSpec};

        jade.Boot.main(jadeArgs);
    }

    private static String promptUserModeSelection() {
        String envMode = System.getenv("AGENT_MODE");
        if (envMode != null && !envMode.isBlank()) {
            return envMode.trim().toLowerCase();
        }
        if (System.console() == null) {
            return "grid";
        }
        System.out.println("Selecciona el agente a ejecutar:");
        System.out.println("  [1] TermostatoAgent (Bloque 1)");
        System.out.println("  [2] GridAgent       (Bloque 2 - por defecto)");
        System.out.print("Opción [2]: ");
        try {
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if ("1".equals(line) || "termostato".equalsIgnoreCase(line)) {
                    return "termostato";
                }
            }
        } catch (Exception ignored) {
        }
        return "grid";
    }
}
