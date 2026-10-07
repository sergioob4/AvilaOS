package avilaos.estructuras;

/**
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

    // Constructor

    /**
     * Crea una cola vacía.
     */
    public Cola() {
        this.frente = null;
        this.fin = null;
        this.tamano = 0;
    }

    // Operaciones principales

    public void encolar(T dato) {
        Nodo<T> nuevoNodo = new Nodo<>(dato);

        if (estaVacia()) {
            // La cola estaba vacía: frente y fin apuntan al único nodo
            frente = nuevoNodo;
        } else {
            // Enlazar el nuevo nodo al final de la cadena
            fin.setSiguiente(nuevoNodo);
        }

        fin = nuevoNodo;
        tamano++;
    }

    /**
     * Elimina y devuelve el elemento del frente de la cola.
     * 
     * @return el dato del elemento que estaba al frente
     * @throws ColaVaciaException si la cola no contiene elementos
     */
    public T desencolar() {
        if (estaVacia()) {
            throw new ColaVaciaException(
                    "No se puede desencolar: la cola está vacía.");
        }

        T datoExtraido = frente.getDato();
        frente = frente.getSiguiente();

        // Si tras desencolar ya no hay elementos, fin también debe ser null
        if (frente == null) {
            fin = null;
        }

        tamano--;
        return datoExtraido;
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
        return frente == null;
    }

    /**
     * Devuelve la cantidad de elementos actualmente en la cola.
     *
     * @return número de elementos (≥ 0)
     */
    public int obtenerTamano() {
        return tamano;
    }

    /**
     * Devuelve el elemento al frente de la cola <strong>sin eliminarlo</strong>.
     *
     * @return el dato del frente
     * @throws ColaVaciaException si la cola no contiene elementos
     */
    public T obtenerFrente() {
        if (estaVacia()) {
            throw new ColaVaciaException(
                    "No se puede consultar el frente: la cola está vacía.");
        }
        return frente.getDato();
    }

    // -------------------------------------------------------------------------
    // Object

    @Override
    public String toString() {
        if (estaVacia()) {
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
    }

    // Excepción interna

    /**
     * Excepción no verificada que se lanza al intentar operar sobre una cola vacía.
     */
    public static final class ColaVaciaException extends RuntimeException {

        /**
         * Crea la excepción con un mensaje descriptivo.
         */
        public ColaVaciaException(String mensaje) {
            super(mensaje);
        }
    }
}
