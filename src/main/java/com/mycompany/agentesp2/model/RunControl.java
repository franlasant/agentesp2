package com.mycompany.agentesp2.model;

import org.json.JSONObject;

/**
 * Modelo de datos (DTO) que encapsula el bloque 'control' devuelto por DBA Lab.
 * Desacopla al agente de la manipulación directa de JSONObject.
 */
public class RunControl {

    private final long runId;
    private final RunStatus runStatus;
    private final String reasonCode;
    private final int remainingObjectives;
    private final String actionStatus;

    public RunControl(long runId, RunStatus runStatus, String reasonCode, int remainingObjectives, String actionStatus) {
        this.runId = runId;
        this.runStatus = runStatus;
        this.reasonCode = reasonCode;
        this.remainingObjectives = remainingObjectives;
        this.actionStatus = actionStatus;
    }

    /**
     * Construye una instancia de RunControl a partir del JSON de respuesta de DBA Lab.
     * Soporta tanto el objeto 'control' directo como la respuesta raíz que contiene 'control'.
     */
    public static RunControl fromJson(JSONObject json) {
        JSONObject ctrl = json.has("control") ? json.getJSONObject("control") : json;
        long id = ctrl.optLong("runId", -1L);
        RunStatus status = RunStatus.fromString(ctrl.optString("runStatus", "UNKNOWN"));
        String reason = ctrl.optString("reasonCode", "NONE");
        int remaining = ctrl.optInt("remainingObjectives", 0);
        String actStatus = json.optString("actionStatus", ctrl.optString("actionStatus", "N/A"));

        return new RunControl(id, status, reason, remaining, actStatus);
    }

    public long getRunId() {
        return runId;
    }

    public RunStatus getRunStatus() {
        return runStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public int getRemainingObjectives() {
        return remainingObjectives;
    }

    public String getActionStatus() {
        return actionStatus;
    }

    public boolean isActive() {
        return runStatus.isActive();
    }

    public boolean isTerminal() {
        return runStatus.isTerminal();
    }

    @Override
    public String toString() {
        return String.format("RunControl[runId=%d, status=%s, reason=%s]", runId, runStatus, reasonCode);
    }
}
