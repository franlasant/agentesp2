package com.mycompany.agentesp2.model.grid;

import com.mycompany.agentesp2.model.RunControl;
import org.json.JSONObject;

/**
 * Modelo de datos (DTO) que representa el estado completo de percepción del agente en Grid 2D.
 * Centraliza:
 * 1. La posición actual del agente (x, y).
 * 2. La posición de la meta/objetivo (target).
 * 3. El estado de los sensores de choque en los 4 puntos cardinales (bumpers).
 * 4. El saldo de energía disponible (energy).
 * 5. El estado oficial de control de DBA Lab (runId, runStatus, reasonCode).
 */
public class GridState {

    private GridPosition currentPosition;
    private final GridPosition targetPosition;
    private GridBumpers bumpers;
    private GridEnergy energy;
    private RunControl control;

    public GridState(GridPosition currentPosition, GridPosition targetPosition,
                     GridBumpers bumpers, GridEnergy energy, RunControl control) {
        this.currentPosition = currentPosition;
        this.targetPosition = targetPosition;
        this.bumpers = bumpers;
        this.energy = energy;
        this.control = control;
    }

    /**
     * Construye el estado inicial a partir de las 4 lecturas de sensores requeridas al arrancar el run.
     */
    public static GridState fromInitialPerceptions(JSONObject posJson, JSONObject targetJson,
                                                  JSONObject bumpersJson, JSONObject energyJson,
                                                  RunControl control) {
        GridPosition current = GridPosition.fromJson(posJson);
        GridPosition target = GridPosition.fromJson(targetJson);
        GridBumpers bumpers = GridBumpers.fromJson(bumpersJson);
        GridEnergy energy = GridEnergy.fromJson(energyJson);

        return new GridState(current, target, bumpers, energy, control);
    }

    /**
     * Actualiza la posición del agente con la respuesta del sensor 'grid.position'.
     */
    public void updatePosition(JSONObject posJson) {
        this.currentPosition = GridPosition.fromJson(posJson);
        if (posJson != null && posJson.has("control")) {
            this.control = RunControl.fromJson(posJson);
        }
    }

    /**
     * Actualiza los sensores de choque con la respuesta de 'grid.bumpers'.
     */
    public void updateBumpers(JSONObject bumpersJson) {
        this.bumpers = GridBumpers.fromJson(bumpersJson);
        if (bumpersJson != null && bumpersJson.has("control")) {
            this.control = RunControl.fromJson(bumpersJson);
        }
    }

    /**
     * Actualiza el saldo de energía con la respuesta de 'grid.energy'.
     */
    public void updateEnergy(JSONObject energyJson) {
        this.energy = GridEnergy.fromJson(energyJson);
        if (energyJson != null && energyJson.has("control")) {
            this.control = RunControl.fromJson(energyJson);
        }
    }

    /**
     * Actualiza el bloque de control tras la ejecución de una acción.
     */
    public void updateControl(RunControl newControl) {
        this.control = newControl;
    }

    public GridPosition getCurrentPosition() {
        return currentPosition;
    }

    public GridPosition getTargetPosition() {
        return targetPosition;
    }

    public GridBumpers getBumpers() {
        return bumpers;
    }

    public GridEnergy getEnergy() {
        return energy;
    }

    public RunControl getControl() {
        return control;
    }

    /**
     * Comprueba si el agente ha alcanzado físicamente la casilla de destino.
     */
    public boolean isAtTarget() {
        return currentPosition != null && targetPosition != null && currentPosition.equals(targetPosition);
    }

    /**
     * Devuelve la distancia Manhattan actual hasta el objetivo.
     */
    public int getDistanceToTarget() {
        if (currentPosition == null || targetPosition == null) return Integer.MAX_VALUE;
        return currentPosition.manhattanDistance(targetPosition);
    }

    @Override
    public String toString() {
        return String.format("Posición: %s | Meta: %s (Distancia: %d) | Bumpers: %s | Energía: %s",
                currentPosition, targetPosition, getDistanceToTarget(), bumpers, energy);
    }
}
