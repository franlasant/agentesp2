package com.mycompany.agentesp2.model.grid;

import com.mycompany.agentesp2.model.RunControl;
import org.json.JSONObject;

/**
 * Modelo de datos (DTO) que centraliza el estado completo percibido por el agente en Grid 2D:
 * - Posición actual (grid.position)
 * - Posición objetivo (grid.target)
 * - Sensores de proximidad (grid.bumpers)
 * - Energía disponible (grid.energy)
 * - Estado oficial de control del run (RunControl)
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
     * Construye el estado inicial a partir de las 4 lecturas de sensores al iniciar el run.
     */
    public static GridState fromInitialPerceptions(JSONObject posJson, JSONObject targetJson,
                                                   JSONObject bumpersJson, JSONObject energyJson) {
        GridPosition current = GridPosition.fromJson(posJson);
        GridPosition target = GridPosition.fromJson(targetJson);
        GridBumpers bumpers = GridBumpers.fromJson(bumpersJson);
        GridEnergy energy = GridEnergy.fromJson(energyJson);
        RunControl control = RunControl.fromJson(energyJson);

        return new GridState(current, target, bumpers, energy, control);
    }

    public void updatePosition(JSONObject posJson) {
        this.currentPosition = GridPosition.fromJson(posJson);
        if (posJson != null && posJson.has("control")) {
            this.control = RunControl.fromJson(posJson);
        }
    }

    public void updateBumpers(JSONObject bumpersJson) {
        this.bumpers = GridBumpers.fromJson(bumpersJson);
        if (bumpersJson != null && bumpersJson.has("control")) {
            this.control = RunControl.fromJson(bumpersJson);
        }
    }

    public void updateEnergy(JSONObject energyJson) {
        this.energy = GridEnergy.fromJson(energyJson);
        if (energyJson != null && energyJson.has("control")) {
            this.control = RunControl.fromJson(energyJson);
        }
    }

    public void updateControl(RunControl newControl) {
        if (newControl != null) {
            this.control = newControl;
        }
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

    public boolean isAtTarget() {
        return currentPosition != null && currentPosition.equals(targetPosition);
    }

    public int getDistanceToTarget() {
        if (currentPosition == null || targetPosition == null) {
            return Integer.MAX_VALUE;
        }
        return currentPosition.manhattanDistance(targetPosition);
    }

    @Override
    public String toString() {
        return String.format("Pos: %s -> Meta: %s (Dist: %d) | Energía: %s | Bumpers: %s | Run: %s",
                currentPosition, targetPosition, getDistanceToTarget(),
                energy, bumpers, control != null ? control.getRunStatus() : "N/A");
    }
}
