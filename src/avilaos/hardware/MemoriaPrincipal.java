package avilaos.hardware;

import java.util.concurrent.Semaphore;

/**
 * Representa la memoria principal (RAM) de un computador en ÁvilaOS.
 *
 * <p>Conforme a la especificación del PDF:
 * <ul>
 *   <li>Cada computador posee una RAM de tamaño configurable.</li>
 *   <li>No es necesario implementar asignación contigua ni particiones complejas:
 *       basta con controlar la memoria total, la memoria usada y la memoria libre.</li>
 *   <li>Controla la admisión de procesos (solo admite si hay memoria suficiente).</li>
 *   <li>Utiliza exclusión mutua con {@link Semaphore} de Java para seguridad concurrente.</li>
 * </ul>
 * </p>
 */
public class MemoriaPrincipal {

    /** Capacidad total de memoria RAM en MB. */
    private final int capacidadTotal;

    /** Cantidad actual de memoria RAM utilizada en MB. */
    private int memoriaUsada;

    /** Semáforo binario de Java para exclusión mutua. */
    private final Semaphore mutex;

    /**
     * Inicializa la memoria RAM con una capacidad especificada.
     *
     * @param capacidadTotal capacidad total en MB (debe ser > 0)
     */
    public MemoriaPrincipal(int capacidadTotal) {
        if (capacidadTotal <= 0) {
            throw new IllegalArgumentException("La capacidad de RAM debe ser mayor a 0 MB.");
        }
        this.capacidadTotal = capacidadTotal;
        this.memoriaUsada = 0;
        this.mutex = new Semaphore(1);
    }

    private void adquirir() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void liberar() {
        mutex.release();
    }

    /**
     * Verifica si existe suficiente memoria libre para alojar una cantidad solicitada.
     *
     * @param cantidadMb memoria solicitada en MB
     * @return {@code true} si hay espacio suficiente, {@code false} de lo contrario
     */
    public boolean hayEspacioDisponible(int cantidadMb) {
        adquirir();
        try {
            return (capacidadTotal - memoriaUsada) >= cantidadMb;
        } finally {
            liberar();
        }
    }

    /**
     * Asigna memoria a un proceso si hay espacio disponible.
     *
     * @param cantidadMb cantidad de MB a asignar
     * @return {@code true} si la asignación fue exitosa; {@code false} si no había suficiente memoria
     */
    public boolean asignarMemoria(int cantidadMb) {
        adquirir();
        try {
            if ((capacidadTotal - memoriaUsada) >= cantidadMb) {
                memoriaUsada += cantidadMb;
                return true;
            }
            return false;
        } finally {
            liberar();
        }
    }

    /**
     * Libera la memoria previamente ocupada por un proceso o buffer.
     *
     * @param cantidadMb cantidad de MB a liberar
     */
    public void liberarMemoria(int cantidadMb) {
        adquirir();
        try {
            memoriaUsada = Math.max(0, memoriaUsada - cantidadMb);
        } finally {
            liberar();
        }
    }

    public int getCapacidadTotal() {
        return capacidadTotal;
    }

    public int getMemoriaUsada() {
        adquirir();
        try {
            return memoriaUsada;
        } finally {
            liberar();
        }
    }

    public int getMemoriaLibre() {
        adquirir();
        try {
            return capacidadTotal - memoriaUsada;
        } finally {
            liberar();
        }
    }

    /**
     * Devuelve el porcentaje de ocupación de la memoria (0.0% - 100.0%).
     *
     * @return porcentaje ocupado
     */
    public double getPorcentajeOcupacion() {
        adquirir();
        try {
            return (double) memoriaUsada / capacidadTotal * 100.0;
        } finally {
            liberar();
        }
    }

    @Override
    public String toString() {
        adquirir();
        try {
            return "RAM[" + memoriaUsada + "MB / " + capacidadTotal + "MB (" +
                    String.format("%.1f", (double) memoriaUsada / capacidadTotal * 100.0) + "%)]";
        } finally {
            liberar();
        }
    }
}
