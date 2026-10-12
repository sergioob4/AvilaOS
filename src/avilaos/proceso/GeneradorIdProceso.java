package avilaos.proceso;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador centralizado de identificadores únicos para procesos en todo el clúster distribuido.
 *
 * <p>Garantiza que el ID de cada proceso sea generado de manera dinámica,
 * secuencial y absolutamente único en todo el clúster distribuido de ÁvilaOS,
 * conforme al Requerimiento 2:
 * <em>"ID (generado dinámicamente y único en todo el sistema distribuido)"</em>.
 * </p>
 */
public final class GeneradorIdProceso {

    /** Contador atómico global para asignación concurrente segura de identificadores. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(0);

    // Constructor privado para clase utilitaria
    private GeneradorIdProceso() {
    }

    /**
     * Genera el siguiente ID numérico único.
     *
     * @return identificador numérico único (1, 2, 3, ...)
     */
    public static int siguienteIdNumero() {
        return SECUENCIA.incrementAndGet();
    }

    /**
     * Genera el siguiente ID en formato estándar de ÁvilaOS (ej. "P1", "P2").
     *
     * @return identificador textual único
     */
    public static String siguienteIdTexto() {
        return "P" + siguienteIdNumero();
    }

    /**
     * Reinicia la secuencia (útil al reiniciar o resetear simulaciones).
     */
    public static void reiniciar() {
        SECUENCIA.set(0);
    }
}
