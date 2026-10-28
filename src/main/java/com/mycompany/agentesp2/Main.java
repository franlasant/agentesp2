package com.mycompany.agentesp2;

import com.mycompany.agentesp2.api.DbaClient;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Clase principal de inicio para NetBeans.
 * Permite comprobar la conexión y lanzar la plataforma JADE con el agente.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   DBA Lab - Práctica 2: Termostato              ");
        System.out.println("=================================================");

        // 1. Comprobación rápida de conexión y credenciales
        try {
            System.out.println("Comprobando configuración y conexión con DBA Lab...");
            DbaClient client = DbaClient.fromEnvOrConfig();
            System.out.println("URL configurada: " + client.getBaseUrl());

            JSONArray challenges = client.getChallenges();
            System.out.println("Conexión con DBA Lab correcta. Retos disponibles: " + challenges.length());
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.getJSONObject(i);
                System.out.printf("  - Reto ID %d [%s]: sensores=%s, acciones=%s%n",
                        ch.getLong("id"),
                        ch.getString("environmentType"),
                        ch.optJSONArray("sensorCodes"),
                        ch.optJSONArray("actionCodes"));
            }
            System.out.println("-------------------------------------------------");
        } catch (Exception e) {
            System.err.println("AVISO: No se pudo verificar la conexión con DBA Lab.");
            System.err.println(e.getMessage());
            System.err.println("Asegúrate de tener la VPN activa y configurar config.properties o variables de entorno.");
            System.out.println("-------------------------------------------------");
        }

        // 2. Lanzar la plataforma JADE con el agente de termostato
        System.out.println("Iniciando contenedor JADE con TermostatoAgent...");
        String[] jadeArgs = new String[] {
            "-gui", // Abre la consola gráfica de JADE (RMA)
            "termostato:com.mycompany.agentesp2.termostato.TermostatoAgent"
        };

        jade.Boot.main(jadeArgs);
    }
}
