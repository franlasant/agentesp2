package com.mycompany.agentesp2;

import com.mycompany.agentesp2.api.DbaClient;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Clase de prueba rápida para comprobar la conexión y el listado de retos
 * sin iniciar ningún run (no consume la sesión activa).
 */
public class TestConexion {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("       Prueba de Conexión con DBA Lab           ");
        System.out.println("=================================================");

        try {
            DbaClient client = DbaClient.fromEnvOrConfig();
            System.out.println("URL de DBA Lab : " + client.getBaseUrl());
            System.out.println("Conectando con el servidor...");

            JSONArray challenges = client.getChallenges();
            System.out.println("¡Conexión establecida con éxito!");
            System.out.println("Número de retos encontrados: " + challenges.length());
            System.out.println("-------------------------------------------------");

            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.getJSONObject(i);
                System.out.println("Reto #" + (i + 1) + ":");
                System.out.println("  - ID: " + ch.getLong("id"));
                System.out.println("  - Nombre: " + ch.optString("name", "N/A"));
                System.out.println("  - Tipo: " + ch.getString("environmentType"));
                System.out.println("  - Sensores: " + ch.optJSONArray("sensorCodes"));
                System.out.println("  - Acciones: " + ch.optJSONArray("actionCodes"));
            }
            System.out.println("=================================================");
            System.out.println("¡Todo está listo para empezar a trabajar con el agente!");

        } catch (Exception e) {
            System.err.println("Error al conectar con DBA Lab: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
