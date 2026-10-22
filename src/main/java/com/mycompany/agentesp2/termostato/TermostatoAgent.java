package com.mycompany.agentesp2.termostato;

import com.mycompany.agentesp2.api.DbaClient;
import com.mycompany.agentesp2.api.DbaException;
import com.mycompany.agentesp2.model.RunControl;
import com.mycompany.agentesp2.model.ThermostatAction;
import com.mycompany.agentesp2.model.ThermostatState;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;

/**
 * Agente JADE para el reto del Termostato de DBA Lab.
 * Refactorizado para usar modelos tipados (ThermostatState, RunControl) y enums (ThermostatAction),
 * manteniendo el código limpio, conciso y libre de manipulación directa de JSON.
 */
public class TermostatoAgent extends Agent {

    private DbaClient client;
    private ThermostatState state;

    @Override
    protected void setup() {
        System.out.println("[TermostatoAgent] Iniciando agente: " + getLocalName());

        try {
            this.client = DbaClient.fromEnvOrConfig();

            long challengeId = resolveChallengeId();
            if (challengeId == -1) {
                System.err.println("[TermostatoAgent] No se encontró ningún reto activo de tipo THERMOSTAT.");
                doDelete();
                return;
            }

            RunControl initialControl = client.startRunTyped(challengeId);
            this.state = client.initThermostatState(initialControl.getRunId());

            System.out.printf("[TermostatoAgent] Run %d iniciado. Rango permitido [%.2f, %.2f] °C | Objetivo: %.2f °C%n",
                    state.getControl().getRunId(), state.getLowerLimit(), state.getUpperLimit(), state.getTargetTemp());

            addBehaviour(new ControlTemperaturaBehaviour());

        } catch (Exception e) {
            System.err.println("[TermostatoAgent] Error en inicialización: " + e.getMessage());
            doDelete();
        }
    }

    private long resolveChallengeId() throws DbaException {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] != null) {
            return Long.parseLong(args[0].toString());
        }
        return client.findChallengeIdByType("THERMOSTAT");
    }

    @Override
    protected void takeDown() {
        System.out.println("[TermostatoAgent] Finalizando agente " + getLocalName());
        if (state != null && state.getControl().isActive() && client != null) {
            try {
                long runId = state.getControl().getRunId();
                System.out.println("[TermostatoAgent] Cerrando run activo " + runId + " en DBA Lab...");
                client.closeRun(runId);
            } catch (DbaException e) {
                System.err.println("[TermostatoAgent] No se pudo cerrar el run: " + e.getMessage());
            }
        }
    }

    /**
     * Comportamiento cíclico de Percepción -> Decisión -> Acción.
     */
    private class ControlTemperaturaBehaviour extends Behaviour {

        private int step = 0;

        @Override
        public void action() {
            try {
                step++;
                long runId = state.getControl().getRunId();

                // 1. PERCEPCIÓN: Actualizar temperatura y estado del run
                client.refreshThermostatTemperature(state);
                System.out.printf("[Paso %d] %s%n", step, state);

                if (state.getControl().isTerminal()) {
                    finishRun();
                    return;
                }

                // 2. DECISIÓN: Elegir acción mediante enum tipado
                ThermostatAction decision = state.isAboveTarget()
                        ? ThermostatAction.REFRIGERATION_ON
                        : ThermostatAction.REFRIGERATION_OFF;

                // 3. ACCIÓN: Enviar acción física y actualizar estado de control
                RunControl updatedControl = client.sendAction(runId, decision);
                state.updateControl(updatedControl);

                System.out.printf("[Paso %d] Decisión: %s | Resultado: %s | Run: %s%n",
                        step, decision, updatedControl.getActionStatus(), updatedControl.getRunStatus());

                if (state.getControl().isTerminal()) {
                    finishRun();
                    return;
                }

                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("[TermostatoAgent] Error en ciclo de control: " + e.getMessage());
                doDelete();
            }
        }

        private void finishRun() {
            RunControl ctrl = state.getControl();
            System.out.println("=================================================");
            System.out.printf("  FIN DEL RUN: %s (Razón: %s)%n", ctrl.getRunStatus(), ctrl.getReasonCode());
            System.out.println("=================================================");
            doDelete();
        }

        @Override
        public boolean done() {
            return state == null || state.getControl().isTerminal();
        }
    }
}
