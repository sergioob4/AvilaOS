package avilaos.sistemaarchivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoNodo;
import avilaos.estructuras.ListaEnlazada;

/**
 * Directorio del sistema de archivos. Sus hijos se guardan en una {@link ListaEnlazada} propia.
 */
public class Directorio extends NodoSistemaArchivos {

    private final ListaEnlazada<NodoSistemaArchivos> hijos;

    /**
     * Crea un directorio con nombre (no la raíz).
     */
    public Directorio(String nombre, String dueno, Permiso permiso) {
        super(validarNombre(nombre), dueno, permiso);
        this.hijos = new ListaEnlazada<>();
    }

    /** Constructor exclusivo para la raíz "/". */
    private Directorio(String dueno) {
        super(SEPARADOR, dueno, Permiso.PUBLICO);
        this.hijos = new ListaEnlazada<>();
    }

    /** @return una raíz nueva cuyo dueño es el administrador indicado */
    static Directorio crearRaiz(String dueno) {
        return new Directorio(dueno);
    }

    @Override
    public TipoNodo getTipo() {
        return TipoNodo.DIRECTORIO;
    }

    /** Los directorios no ocupan bloques de datos. */
    @Override
    public int getTamanoBloques() {
        return 0;
    }

    // -------------------------------------------------------------------------
    // Hijos
    // -------------------------------------------------------------------------

    /** @return hijo con ese nombre o {@code null} */
    public NodoSistemaArchivos buscarHijo(String nombre) {
        int total = hijos.obtenerTamano();
        for (int i = 0; i < total; i++) {
            NodoSistemaArchivos hijo = hijos.obtener(i);
            if (hijo != null && hijo.getNombre().equals(nombre)) {
                return hijo;
            }
        }
        return null;
    }

    public int getCantidadHijos() {
        return hijos.obtenerTamano();
    }

    public NodoSistemaArchivos getHijo(int indice) {
        return hijos.obtener(indice);
    }

    /** @return posición del hijo o -1 (lo usa el TreeModel del JTree) */
    public int indiceDe(NodoSistemaArchivos hijo) {
        int total = hijos.obtenerTamano();
        for (int i = 0; i < total; i++) {
            if (hijos.obtener(i) == hijo) {
                return i;
            }
        }
        return -1;
    }

    void agregarHijo(NodoSistemaArchivos hijo) {
        if (buscarHijo(hijo.getNombre()) != null) {
            throw new IllegalStateException("Ya existe \"" + hijo.getNombre() + "\" en " + getRuta() + ".");
        }
        hijo.setPadre(this);
        hijos.agregar(hijo);
    }

    void quitarHijo(NodoSistemaArchivos hijo) {
        if (hijos.eliminar(hijo)) {
            hijo.setPadre(null);
        }
    }
}
