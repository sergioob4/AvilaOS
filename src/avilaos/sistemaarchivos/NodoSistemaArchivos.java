package avilaos.sistemaarchivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoNodo;

/**
 * Elemento del árbol del sistema de archivos: archivo o directorio.
 *
 * <p>Guarda los atributos comunes (nombre, dueño, permiso y directorio padre) y calcula
 * su ruta absoluta recorriendo los padres. La ruta no se almacena para que renombrar un
 * directorio actualice automáticamente la ruta de todo su contenido.</p>
 */
public abstract class NodoSistemaArchivos {

    /** Separador de rutas del sistema de archivos. */
    public static final String SEPARADOR = "/";

    private String nombre;
    private final String dueno;
    private Permiso permiso;
    private Directorio padre;

    /**
     * @param nombre nombre del nodo (sin separadores)
     * @param dueno usuario dueño
     * @param permiso permiso de acceso
     */
    protected NodoSistemaArchivos(String nombre, String dueno, Permiso permiso) {
        this.nombre = nombre;
        this.dueno = validarDueno(dueno);
        this.permiso = permiso == null ? Permiso.PRIVADO : permiso;
    }

    // -------------------------------------------------------------------------
    // Validación
    // -------------------------------------------------------------------------

    /**
     * Verifica que un nombre sea válido para un archivo o directorio.
     *
     * @param nombre nombre a validar
     * @return el nombre sin espacios al inicio ni al final
     * @throws IllegalArgumentException si está vacío, contiene "/" o es "." / ".."
     */
    public static String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío.");
        }
        String limpio = nombre.trim();
        if (limpio.contains(SEPARADOR) || limpio.equals(".") || limpio.equals("..")) {
            throw new IllegalArgumentException("Nombre inválido: \"" + limpio + "\".");
        }
        return limpio;
    }

    private static String validarDueno(String dueno) {
        if (dueno == null || dueno.trim().isEmpty()) {
            throw new IllegalArgumentException("Todo archivo o directorio debe tener dueño.");
        }
        return dueno.trim();
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    /** @return tipo del nodo (archivo o directorio) */
    public abstract TipoNodo getTipo();

    /** @return tamaño en bloques (0 para directorios) */
    public abstract int getTamanoBloques();

    /** @return ruta absoluta del nodo, por ejemplo {@code /usuarios/notas.txt} */
    public String getRuta() {
        if (padre == null) {
            return SEPARADOR;
        }
        String rutaPadre = padre.getRuta();
        return rutaPadre.equals(SEPARADOR) ? SEPARADOR + nombre : rutaPadre + SEPARADOR + nombre;
    }

    /** @return {@code true} si este nodo es {@code otro} o está contenido en él */
    public boolean estaDentroDe(Directorio otro) {
        for (NodoSistemaArchivos actual = this; actual != null; actual = actual.padre) {
            if (actual == otro) {
                return true;
            }
        }
        return false;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDueno() {
        return dueno;
    }

    public Permiso getPermiso() {
        return permiso;
    }

    public Directorio getPadre() {
        return padre;
    }

    // -------------------------------------------------------------------------
    // Modificación (solo desde el paquete: la hace SistemaArchivos bajo su semáforo)
    // -------------------------------------------------------------------------

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    void setPermiso(Permiso permiso) {
        this.permiso = permiso;
    }

    void setPadre(Directorio padre) {
        this.padre = padre;
    }

    @Override
    public String toString() {
        return padre == null ? SEPARADOR : nombre;
    }
}
