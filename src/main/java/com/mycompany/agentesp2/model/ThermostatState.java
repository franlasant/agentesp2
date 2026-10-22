package com.mycompany.agentesp2.model;

import org.json.JSONObject;

/**
 * Modelo de datos (DTO) que representa el estado completo de percepción del Termostato.
 * Centraliza temperatura actual, límites permitidos, temperatura objetivo y estado de control.
 */
public class ThermostatState {

    private double currentTemp;
    private final double lowerLimit;
    private final double upperLimit;
    private final double targetTemp;
    private RunControl control;

    public ThermostatState(double lowerLimit, double upperLimit, double targetTemp, RunControl control) {
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.targetTemp = targetTemp;
        this.control = control;
    }

    /**
     * Construye el estado inicial a partir de los JSON de límites y objetivo.
     */
    public static ThermostatState fromInitialPerceptions(JSONObject limitsJson, JSONObject targetJson) {
        JSONObject limitsVal = limitsJson.getJSONObject("value");
        double lower = limitsVal.getDouble("lower");
        double upper = limitsVal.getDouble("upper");
        double target = targetJson.getJSONObject("value").getDouble("celsius");
        RunControl ctrl = RunControl.fromJson(targetJson);

        return new ThermostatState(lower, upper, target, ctrl);
    }

    /**
     * Actualiza la temperatura actual y el estado de control a partir del JSON de percepción de temperatura.
     */
    public void updateFromTemperatureJson(JSONObject tempJson) {
        this.currentTemp = tempJson.getJSONObject("value").getDouble("celsius");
        this.control = RunControl.fromJson(tempJson);
    }

    /**
     * Actualiza el objeto de control tras ejecutar una acción.
     */
    public void updateControl(RunControl newControl) {
        this.control = newControl;
    }

    public double getCurrentTemp() {
        return currentTemp;
    }

    public double getLowerLimit() {
        return lowerLimit;
    }

    public double getUpperLimit() {
        return upperLimit;
    }

    public double getTargetTemp() {
        return targetTemp;
    }

    public RunControl getControl() {
        return control;
    }

    /**
     * Indica si la temperatura actual supera la temperatura objetivo orientativa.
     */
    public boolean isAboveTarget() {
        return currentTemp > targetTemp;
    }

    /**
     * Indica si la temperatura actual es inferior o igual a la temperatura objetivo.
     */
    public boolean isBelowOrEqualTarget() {
        return currentTemp <= targetTemp;
    }

    /**
     * Indica si la temperatura se encuentra dentro del rango seguro [lowerLimit, upperLimit].
     */
    public boolean isSafe() {
        return currentTemp >= lowerLimit && currentTemp <= upperLimit;
    }

    @Override
    public String toString() {
        return String.format("Temperatura: %.2f °C (Objetivo: %.2f °C | Rango: [%.2f, %.2f] °C)",
                currentTemp, targetTemp, lowerLimit, upperLimit);
    }
}
