package avilaos.estructuras;

import java.util.concurrent.Semaphore;

/**
 * Lista simplemente enlazada genérica propia con soporte para exclusión mutua.
 *
 * <p>Diseñada específicamente para cumplir con las restricciones del proyecto ÁvilaOS:
 * <ul>
 *   <li>No utiliza ninguna colección del framework de Java ({@code java.util.*}).</li>
 *   <li>Utiliza un {@link Semaphore} de Java inicializado en 1 (mutex) para garantizar
 *       exclusión mutua sobre operaciones concurrentes entre hilos.</li>
 *   <li>Permite inserción, eliminación arbitraria, búsqueda, indexación y ordenamiento
 *       manual necesario para las colas de planificación (EDF, Prioridades).</li>
 * </ul>
 * </p>
 *
 * @param <T> el tipo de dato contenido en los nodos
 */
public class ListaEnlazada<T> {

    /** Nodo cabeza de la lista. */
    private Nodo<T> cabeza;

    /** Cantidad de elementos actuales en la lista. */
    private int tamano;

    /** Semáforo binario de Java para exclusión mutua en accesos concurrentes. */
    private final Semaphore mutex;

    /**
     * Crea una lista enlazada vacía y prepara el semáforo de exclusión mutua.
     */
    public ListaEnlazada() {
        this.cabeza = null;
        this.tamano = 0;
        this.mutex = new Semaphore(1);
    }

    // -------------------------------------------------------------------------
    // Control de exclusión mutua
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
     * Agrega un nuevo elemento al final de la lista de forma thread-safe.
     *
     * @param dato el elemento a insertar
     */
    public void agregar(T dato) {
        adquirir();
        try {
            Nodo<T> nuevo = new Nodo<>(dato);
            if (cabeza == null) {
                cabeza = nuevo;
            } else {
                Nodo<T> actual = cabeza;
                while (actual.getSiguiente() != null) {
                    actual = actual.getSiguiente();
                }
                actual.setSiguiente(nuevo);
            }
            tamano++;
        } finally {
            liberar();
        }
    }

    /**
     * Agrega un elemento al inicio de la lista de forma thread-safe.
     *
     * @param dato el elemento a insertar al inicio
     */
    public void agregarAlInicio(T dato) {
        adquirir();
        try {
            Nodo<T> nuevo = new Nodo<>(dato);
            nuevo.setSiguiente(cabeza);
            cabeza = nuevo;
            tamano++;
        } finally {
            liberar();
        }
    }

    /**
     * Obtiene el elemento ubicado en una posición específica (0-indexada).
     *
     * @param indice índice del elemento a consultar
     * @return el dato en el índice indicado
     * @throws IndexOutOfBoundsException si el índice es menor a 0 o mayor o igual al tamaño
     */
    public T obtener(int indice) {
        adquirir();
        try {
            validarIndice(indice);
            Nodo<T> actual = cabeza;
            for (int i = 0; i < indice; i++) {
                actual = actual.getSiguiente();
            }
            return actual.getDato();
        } finally {
            liberar();
        }
    }

    /**
     * Reemplaza el elemento en un índice específico con un nuevo valor.
     *
     * @param indice índice del elemento a reemplazar
     * @param nuevoDato nuevo dato
     */
    public void reemplazar(int indice, T nuevoDato) {
        adquirir();
        try {
            validarIndice(indice);
            Nodo<T> actual = cabeza;
            for (int i = 0; i < indice; i++) {
                actual = actual.getSiguiente();
            }
            actual.setDato(nuevoDato);
        } finally {
            liberar();
        }
    }

    /**
     * Elimina el elemento en un índice específico y lo devuelve.
     *
     * @param indice posición del elemento a eliminar
     * @return el elemento eliminado
     * @throws IndexOutOfBoundsException si el índice está fuera de rango
     */
    public T eliminarEn(int indice) {
        adquirir();
        try {
            validarIndice(indice);
            if (indice == 0) {
                T datoEliminado = cabeza.getDato();
                cabeza = cabeza.getSiguiente();
                tamano--;
                return datoEliminado;
            }

            Nodo<T> anterior = cabeza;
            for (int i = 0; i < indice - 1; i++) {
                anterior = anterior.getSiguiente();
            }

            Nodo<T> objetivo = anterior.getSiguiente();
            T datoEliminado = objetivo.getDato();
            anterior.setSiguiente(objetivo.getSiguiente());
            tamano--;
            return datoEliminado;
        } finally {
            liberar();
        }
    }

    /**
     * Elimina la primera ocurrencia del dato especificado en la lista.
     *
     * @param dato elemento a buscar y eliminar
     * @return {@code true} si el elemento fue encontrado y eliminado, {@code false} de lo contrario
     */
    public boolean eliminar(T dato) {
        adquirir();
        try {
            if (cabeza == null) {
                return false;
            }

            if ((cabeza.getDato() == null && dato == null) ||
                (cabeza.getDato() != null && cabeza.getDato().equals(dato))) {
                cabeza = cabeza.getSiguiente();
                tamano--;
                return true;
            }

            Nodo<T> anterior = cabeza;
            while (anterior.getSiguiente() != null) {
                Nodo<T> actual = anterior.getSiguiente();
                if ((actual.getDato() == null && dato == null) ||
                    (actual.getDato() != null && actual.getDato().equals(dato))) {
                    anterior.setSiguiente(actual.getSiguiente());
                    tamano--;
                    return true;
                }
                anterior = actual;
            }
            return false;
        } finally {
            liberar();
        }
    }

    /**
     * Verifica si el elemento se encuentra presente en la lista.
     *
     * @param dato elemento a buscar
     * @return {@code true} si está en la lista; {@code false} si no
     */
    public boolean contiene(T dato) {
        adquirir();
        try {
            Nodo<T> actual = cabeza;
            while (actual != null) {
                if ((actual.getDato() == null && dato == null) ||
                    (actual.getDato() != null && actual.getDato().equals(dato))) {
                    return true;
                }
                actual = actual.getSiguiente();
            }
            return false;
        } finally {
            liberar();
        }
    }

    /**
     * Vacía completamente la lista.
     */
    public void vaciar() {
        adquirir();
        try {
            cabeza = null;
            tamano = 0;
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    public boolean estaVacia() {
        adquirir();
        try {
            return tamano == 0;
        } finally {
            liberar();
        }
    }

    public int obtenerTamano() {
        adquirir();
        try {
            return tamano;
        } finally {
            liberar();
        }
    }

    private void validarIndice(int indice) {
        if (indice < 0 || indice >= tamano) {
            throw new IndexOutOfBoundsException("Índice fuera de rango: " + indice + " (Tamaño: " + tamano + ")");
        }
    }

    @Override
    public String toString() {
        adquirir();
        try {
            if (cabeza == null) {
                return "ListaEnlazada[]";
            }
            StringBuilder sb = new StringBuilder("ListaEnlazada[");
            Nodo<T> actual = cabeza;
            while (actual != null) {
                sb.append(actual.getDato());
                if (actual.getSiguiente() != null) {
                    sb.append(", ");
                }
                actual = actual.getSiguiente();
            }
            sb.append("]");
            return sb.toString();
        } finally {
            liberar();
        }
    }
}
