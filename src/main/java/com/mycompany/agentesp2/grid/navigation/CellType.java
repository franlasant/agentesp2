package com.mycompany.agentesp2.grid.navigation;

/**
 * Tipos de celda dentro del mapa mental que el agente construye dinámicamente.
 *
 * - UNKNOWN ('?'): Casilla aún no observada por los sensores.
 *                  Bajo la estrategia Optimistic Online A*, se asume transitable hasta comprobar lo contrario.
 * - FREE    ('.'): Casilla confirmada como libre (pisada por el agente o verificada con bumper = false).
 * - OBSTACLE('#'): Casilla bloqueada por un muro interno o por el límite exterior del mundo (bumper = true).
 */
public enum CellType {

    UNKNOWN('?'),
    FREE('.'),
    OBSTACLE('#');

    private final char symbol;

    CellType(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }
}
