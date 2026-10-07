package avilaos.estructuras;

/**
 * Nodo genérico que actúa como bloque fundamental para estructuras de datos enlazadas.
 *
 * <p>Cada nodo almacena un dato de tipo {@code T} y una referencia al siguiente nodo
 * en la cadena, permitiendo construir estructuras lineales sin depender de las
 * colecciones del framework nativo de Java.</p>
 *
 * @param <T> el tipo del dato almacenado en el nodo
 */
public class Nodo<T> {

    /** Dato almacenado en este nodo. */
    private T dato;

    /** Referencia al siguiente nodo en la cadena; {@code null} si es el último. */
    private Nodo<T> siguiente;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Crea un nuevo nodo con el dato especificado y sin sucesor.
     *
     * @param dato el valor que almacenará este nodo
     */
    public Nodo(T dato) {
        this.dato = dato;
        this.siguiente = null;
    }

    // -------------------------------------------------------------------------
    // Getters y Setters
    // -------------------------------------------------------------------------

    /**
     * Devuelve el dato almacenado en este nodo.
     *
     * @return el dato del nodo
     */
    public T getDato() {
        return dato;
    }

    /**
     * Establece el dato almacenado en este nodo.
     *
     * @param dato el nuevo valor para el nodo
     */
    public void setDato(T dato) {
        this.dato = dato;
    }

    /**
     * Devuelve la referencia al siguiente nodo.
     *
     * @return el nodo siguiente, o {@code null} si no existe
     */
    public Nodo<T> getSiguiente() {
        return siguiente;
    }

    /**
     * Establece el nodo siguiente en la cadena.
     *
     * @param siguiente el nodo que sucederá a este
     */
    public void setSiguiente(Nodo<T> siguiente) {
        this.siguiente = siguiente;
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "Nodo{dato=" + dato + "}";
    }
}
