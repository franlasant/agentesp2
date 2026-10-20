package com.mycompany.agentesp2.termostato;

import com.mycompany.agentesp2.api.DbaClient;
import com.mycompany.agentesp2.api.DbaException;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Agente JADE para el reto del Termostato de DBA Lab.
 * Mantiene la temperatura de la cámara dentro del rango [lower, upper]
 * guiándose por la temperatura orientativa (target).
 */
public class TermostatoAgent extends Agent {

    private DbaClient client;
    private long runId = -1;
    private double lowerLimit;
    private double upperLimit;
    private double targetTemp;
    private boolean runActive = false;

    @Override
    protected void setup() {
        System.out.println("[TermostatoAgent] Iniciando agente: " + getLocalName());

        try {
            // Inicializar cliente HTTP
            this.client = DbaClient.fromEnvOrConfig();

            // Parámetro opcional: challengeId pasado como argumento del agente
            Object[] args = getArguments();
            long challengeId = -1;

            if (args != null && args.length > 0 && args[0] != null) {
                challengeId = Long.parseLong(args[0].toString());
            } else {
                // Auto-descubrimiento del reto de termostato si no se especifica por argumento
                System.out.println("[TermostatoAgent] Buscando retos de termostato disponibles en DBA Lab...");
                JSONArray challenges = client.getChallenges();
                for (int i = 0; i < challenges.length(); i++) {
                    JSONObject ch = challenges.getJSONObject(i);
                    if ("THERMOSTAT".equalsIgnoreCase(ch.optString("environmentType"))) {
                        challengeId = ch.getLong("id");
                        System.out.println("[TermostatoAgent] Reto de termostato seleccionado: ID " + challengeId);
                        break;
                    }
                }
            }

            if (challengeId == -1) {
                System.err.println("[TermostatoAgent] No se encontró ningún reto activo de tipo THERMOSTAT.");
                doDelete();
                return;
            }

            // Iniciar el run
            System.out.println("[TermostatoAgent] Iniciando run para el reto " + challengeId + "...");
            JSONObject runStart = client.startRun(challengeId);
            JSONObject control = runStart.getJSONObject("control");
            this.runId = control.getLong("runId");
            this.runActive = "ACTIVE".equalsIgnoreCase(control.optString("runStatus"));

            System.out.println("[TermostatoAgent] Run iniciado con éxito. RunId = " + runId + ", Estado: " + control.optString("runStatus"));

            // Leer límites y objetivo orientativo una vez al inicio
            JSONObject limitsPerc = client.getPerception(runId, "thermostat.limits");
            JSONObject limitsVal = limitsPerc.getJSONObject("value");
            this.lowerLimit = limitsVal.getDouble("lower");
            this.upperLimit = limitsVal.getDouble("upper");

            JSONObject targetPerc = client.getPerception(runId, "thermostat.target");
            this.targetTemp = targetPerc.getJSONObject("value").getDouble("celsius");

            System.out.printf("[TermostatoAgent] Parámetros recibidos: Rango permitido [%.2f, %.2f] °C, Objetivo orientativo: %.2f °C%n",
                    lowerLimit, upperLimit, targetTemp);

            // Añadir comportamiento cíclico de control
            addBehaviour(new ControlTemperaturaBehaviour());

        } catch (Exception e) {
            System.err.println("[TermostatoAgent] Error durante la inicialización: " + e.getMessage());
            e.printStackTrace();
            doDelete();
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("[TermostatoAgent] Finalizando agente " + getLocalName());
        // Si el run aún sigue activo al detener el agente, lo cerramos en el laboratorio
        if (runActive && runId > 0 && client != null) {
            try {
                System.out.println("[TermostatoAgent] Cerrando run activo " + runId + " en DBA Lab...");
                client.closeRun(runId);
            } catch (DbaException e) {
                System.err.println("[TermostatoAgent] No se pudo cerrar el run: " + e.getMessage());
            }
        }
    }

    /**
     * Comportamiento que ejecuta el ciclo de percepción - decisión - acción.
     */
    private class ControlTemperaturaBehaviour extends Behaviour {

        private boolean finished = false;
        private int step = 0;

        @Override
        public void action() {
            if (!runActive || finished) {
                return;
            }

            try {
                step++;
                // 1. PERCEPCIÓN: Leer temperatura actual
                JSONObject tempPerc = client.getPerception(runId, "thermostat.temperature");
                double currentTemp = tempPerc.getJSONObject("value").getDouble("celsius");
                JSONObject control = tempPerc.getJSONObject("control");

                System.out.printf("[Paso %d] Temperatura actual: %.2f °C (Objetivo: %.2f °C, Rango: [%.2f, %.2f] °C)%n",
                        step, currentTemp, targetTemp, lowerLimit, upperLimit);

                if (!"ACTIVE".equalsIgnoreCase(control.optString("runStatus"))) {
                    handleTerminalStatus(control);
                    finished = true;
                    return;
                }

                // 2. DECISIÓN: Determinar si encender o apagar la refrigeración
                String actionCode;
                if (currentTemp > targetTemp) {
                    // Temperatura superior al objetivo: refrigerar
                    actionCode = "thermostat.refrigeration.on";
                } else {
                    // Temperatura inferior o igual: apagar refrigeración
                    actionCode = "thermostat.refrigeration.off";
                }

                // 3. ACCIÓN: Enviar la acción a DBA Lab
                System.out.println("[Paso " + step + "] Decisión: " + actionCode);
                JSONObject actionResult = client.sendAction(runId, actionCode);

                String actionStatus = actionResult.optString("actionStatus");
                control = actionResult.getJSONObject("control");
                String runStatus = control.optString("runStatus");

                System.out.println("[Paso " + step + "] Acción: " + actionStatus + " | Estado del run: " + runStatus);

                // 4. VERIFICACIÓN DEL ESTADO DEL RUN
                if (!"ACTIVE".equalsIgnoreCase(runStatus)) {
                    handleTerminalStatus(control);
                    finished = true;
                }

                // Pequeña pausa para no saturar peticiones (opcional, configurable)
                Thread.sleep(500);

            } catch (DbaException e) {
                System.err.println("[TermostatoAgent] Error en la API: " + e.getMessage());
                finished = true;
                doDelete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finished = true;
            } catch (Exception e) {
                System.err.println("[TermostatoAgent] Error inesperado en el comportamiento: " + e.getMessage());
                e.printStackTrace();
                finished = true;
                doDelete();
            }
        }

        private void handleTerminalStatus(JSONObject control) {
            runActive = false;
            String status = control.optString("runStatus");
            String reason = control.optString("reasonCode", "NONE");
            System.out.println("=================================================");
            System.out.println("  FIN DEL RUN: " + status + " (Razón: " + reason + ")");
            System.out.println("=================================================");
            doDelete();
        }

        @Override
        public boolean done() {
            return finished;
        }
    }
}
