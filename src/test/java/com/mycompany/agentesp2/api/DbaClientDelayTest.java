package com.mycompany.agentesp2.api;

/**
 * Tests unitarios offline para verificar la configuración del delay entre peticiones en DbaClient.
 * Compatible con ejecución directa por Maven Surefire (POJO test) y por main().
 */
public class DbaClientDelayTest {

    public static void main(String[] args) {
        DbaClientDelayTest test = new DbaClientDelayTest();
        test.testDefaultDelay();
        test.testCustomDelay();
        test.testNegativeDelaySanitizedToZero();
        System.out.println("✅ [DbaClientDelayTest] Todos los tests de delay superados con éxito.");
    }

    public void testDefaultDelay() {
        DbaClient client = new DbaClient("http://dummy:8080", "test-token");
        assertEquals(500L, client.getRequestDelayMs(), "El delay por defecto debe ser 500 ms (0.5 s)");
        assertEquals(DbaClient.DEFAULT_REQUEST_DELAY_MS, client.getRequestDelayMs(), "Debe coincidir con DEFAULT_REQUEST_DELAY_MS");
    }

    public void testCustomDelay() {
        DbaClient client = new DbaClient("http://dummy:8080", "test-token", 750L);
        assertEquals(750L, client.getRequestDelayMs(), "El delay personalizado debe ser 750 ms");
    }

    public void testNegativeDelaySanitizedToZero() {
        DbaClient client = new DbaClient("http://dummy:8080", "test-token", -100L);
        assertEquals(0L, client.getRequestDelayMs(), "Un delay negativo debe sanearse a 0 ms");
    }

    private static void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " -> Esperado: " + expected + ", Actual: " + actual);
        }
    }
}
