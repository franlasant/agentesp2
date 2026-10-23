
package com.mycompany.agentesp2.grid;

import com.mycompany.agentesp2.api.DbaClient;
import jade.core.Agent;

/**
 * Plantilla de Agente JADE para los retos de Navegación 2D (Grid).
 * Se implementará para el bloque 2 de la práctica.
 * 
 * Percepciones disponibles:
 *  - grid.position: {x, y}
 *  - grid.bumpers: {north, east, south, west}
 *  - grid.target: {x, y}
 *  - grid.energy: {mode, remaining}
 * 
 * Acciones de movimiento:
 *  - grid.move.north, grid.move.east, grid.move.south, grid.move.west
 */
public class GridAgent extends Agent {

    private DbaClient client;
    private long runId = -1;

    @Override
    protected void setup() {
        System.out.println("[GridAgent] Iniciando agente de navegación 2D: " + getLocalName());
        // Lógica de inicialización para Grid (se completará en el bloque 2)
    }

    @Override
    protected void takeDown() {
        System.out.println("[GridAgent] Finalizando agente " + getLocalName());
    }
}
