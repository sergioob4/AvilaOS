package avilaos.estructuras;

import java.util.concurrent.Semaphore;

/**
 * Cola genérica basada en nodos enlazados con soporte para exclusión mutua
 * garantizada mediante un semáforo binario de Java.
 *
 * <p>Cumple con las restricciones de la cátedra:
 * <ul>
 *   <li>Sin colecciones de {@code java.util.*} (List, Queue, Stack, etc.).</li>
 *   <li>Uso de {@link Semaphore} de Java para exclusión mutua entre hilos.</li>
 * </ul>
 * </p>
 *
 * @param <T> el tipo de los elementos almacenados en la cola
 */
public class Cola<T> {

    /** Nodo apuntado al frente (primer elemento en salir). */
    private Nodo<T> frente;

    /** Nodo apuntado al final (último elemento en entrar). */
    private Nodo<T> fin;

    /** Cantidad actual de elementos en la cola. */
    private int tamano;

    /** Semáforo binario de Java para exclusión mutua. */
    private final Semaphore mutex;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Crea una cola vacía.
     */
    public Cola() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
        this.mutex = new Semaphore(1);
    }

    // -------------------------------------------------------------------------
    // Sincronización
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Operaciones principales
    // -------------------------------------------------------------------------

    /**
     * Inserta un nuevo dato al final de la cola de manera segura para hilos.
     *
     * @param dato elemento a encolar
     */
    public void encolar(T dato) {
        adquirir();
        try {
            Nodo<T> nuevoNodo = new Nodo<>(dato);

            if (frente == null) {
                frente = nuevoNodo;
            } else {
                fin.setSiguiente(nuevoNodo);
            }

            fin = nuevoNodo;
            tamano++;
        } finally {
            liberar();
        }
    }

    /**
     * Elimina y devuelve el elemento del frente de la cola de manera segura para hilos.
     *
     * @return el dato del elemento que estaba al frente
     * @throws ColaVaciaException si la cola no contiene elementos
     */
    public T desencolar() {
        adquirir();
        try {
            if (frente == null) {
                throw new ColaVaciaException("No se puede desencolar: la cola está vacía.");
            }

            T datoExtraido = frente.getDato();
            frente = frente.getSiguiente();

            if (frente == null) {
                fin = null;
            }

            tamano--;
            return datoExtraido;
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    /**
     * Indica si la cola no contiene elementos.
     *
     * @return {@code true} si la cola está vacía; {@code false} en caso contrario
     */
    public boolean estaVacia() {
        adquirir();
        try {
            return frente == null;
        } finally {
            liberar();
        }
    }

    /**
     * Devuelve la cantidad de elementos actualmente en la cola.
     *
     * @return número de elementos (≥ 0)
     */
    public int obtenerTamano() {
        adquirir();
        try {
            return tamano;
        } finally {
            liberar();
        }
    }

    /**
     * Devuelve el elemento al frente de la cola sin eliminarlo.
     *
     * @return el dato del frente
     * @throws ColaVaciaException si la cola no contiene elementos
     */
    public T obtenerFrente() {
        adquirir();
        try {
            if (frente == null) {
                throw new ColaVaciaException("No se puede consultar el frente: la cola está vacía.");
            }
            return frente.getDato();
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        adquirir();
        try {
            if (frente == null) {
                return "Cola[vacía]";
            }

            StringBuilder sb = new StringBuilder("Cola[");
            Nodo<T> actual = frente;

            while (actual != null) {
                sb.append(actual.getDato());
                if (actual.getSiguiente() != null) {
                    sb.append(" -> ");
                }
                actual = actual.getSiguiente();
            }

            sb.append("]");
            return sb.toString();
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Excepción interna
    // -------------------------------------------------------------------------

    /**
     * Excepción no verificada que se lanza al intentar operar sobre una cola vacía.
     */
    public static final class ColaVaciaException extends RuntimeException {

        public ColaVaciaException(String mensaje) {
            super(mensaje);
        }
    }
}
