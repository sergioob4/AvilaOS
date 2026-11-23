package avilaos.sistemaarchivos;

import avilaos.enums.ModoUsuario;
import avilaos.enums.Permiso;
import avilaos.estructuras.ListaEnlazada;
import java.util.concurrent.Semaphore;

/**
 * Sistema de archivos jerárquico de ÁvilaOS (capa del sistema operativo).
 *
 * <p>Mantiene el árbol de directorios y archivos a partir de la raíz "/". Es una estructura
 * compartida entre el hilo del reloj (procesos de E/S que completan operaciones) y el hilo de la
 * interfaz (que la observa), por lo que toda lectura o modificación del árbol se hace en
 * exclusión mutua con un {@link Semaphore} de Java.</p>
 *
 * <p>La interfaz nunca llama a los métodos que modifican el árbol: solo crea procesos de E/S,
 * y es el sistema operativo quien ejecuta la operación cuando el disco atiende la solicitud.</p>
 */
public class SistemaArchivos {

    /** Dueño de la raíz y de los archivos del sistema. */
    public static final String ADMINISTRADOR = "admin";

    private final Directorio raiz;
    private final Semaphore mutex;

    public SistemaArchivos() {
        this.raiz = Directorio.crearRaiz(ADMINISTRADOR);
        this.mutex = new Semaphore(1, true);
    }

    // -------------------------------------------------------------------------
    // Exclusión mutua
    // -------------------------------------------------------------------------

    private void adquirir() {
        mutex.acquireUninterruptibly();
    }

    private void liberar() {
        mutex.release();
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    public Directorio getRaiz() {
        return raiz;
    }

    /**
     * Busca un nodo por su ruta absoluta.
     *
     * @param ruta ruta como {@code /sistema/boot.bin}
     * @return el nodo o {@code null} si no existe
     */
    public NodoSistemaArchivos buscar(String ruta) {
        adquirir();
        try {
            return buscarSinBloqueo(ruta);
        } finally {
            liberar();
        }
    }

    /**
     * Resuelve una lectura autorizada y devuelve el inicio de la cadena que leerá el disco.
     * El SO proporciona la identidad y el modo de la sesión que solicitó la operación.
     */
    public int resolverLectura(String ruta, String usuario, ModoUsuario modo) {
        if (modo == null || usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("La lectura requiere un usuario y un modo.");
        }
        adquirir();
        try {
            if (!(nodoExistente(ruta) instanceof Archivo archivo)) {
                throw new IllegalArgumentException(ruta + " no es un archivo.");
            }
            if (!modo.esAdministrador() && archivo.getPermiso() == Permiso.PRIVADO
                    && !archivo.getDueno().equals(usuario.trim())) {
                throw new SecurityException("Permiso de lectura denegado para " + ruta + ".");
            }
            int inicio = archivo.getPrimerBloque();
            if (inicio == Archivo.SIN_BLOQUES) {
                throw new IllegalStateException("El archivo todavía no tiene bloques asignados.");
            }
            return inicio;
        } finally {
            liberar();
        }
    }

    /** @return todos los archivos del árbol en recorrido en profundidad (para la tabla de asignación) */
    public ListaEnlazada<Archivo> listarArchivos() {
        adquirir();
        try {
            ListaEnlazada<Archivo> resultado = new ListaEnlazada<>();
            recolectarArchivos(raiz, resultado);
            return resultado;
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Operaciones sobre el árbol
    // -------------------------------------------------------------------------

    /**
     * Crea un directorio dentro de otro.
     *
     * @param rutaPadre ruta del directorio contenedor
     * @param nombre nombre del nuevo directorio
     * @param dueno usuario dueño
     * @param permiso permiso de acceso
     * @return el directorio creado
     * @throws IllegalArgumentException si la ruta padre no existe o no es un directorio
     * @throws IllegalStateException si ya existe un elemento con ese nombre
     */
    public Directorio crearDirectorio(String rutaPadre, String nombre, String dueno, Permiso permiso) {
        adquirir();
        try {
            Directorio padre = directorioExistente(rutaPadre);
            Directorio nuevo = new Directorio(nombre, dueno, permiso);
            padre.agregarHijo(nuevo);
            return nuevo;
        } finally {
            liberar();
        }
    }

    /**
     * Cambia el nombre de un archivo o directorio. Las rutas de su contenido cambian solas
     * porque se calculan a partir de los padres.
     *
     * @param ruta ruta actual
     * @param nuevoNombre nombre nuevo
     * @throws IllegalArgumentException si la ruta no existe, es la raíz o el nombre es inválido
     * @throws IllegalStateException si ya existe un elemento con ese nombre en el mismo directorio
     */
    public void renombrar(String ruta, String nuevoNombre) {
        adquirir();
        try {
            NodoSistemaArchivos nodo = nodoExistente(ruta);
            if (nodo == raiz) {
                throw new IllegalArgumentException("La raíz no puede renombrarse.");
            }
            String limpio = NodoSistemaArchivos.validarNombre(nuevoNombre);
            NodoSistemaArchivos existente = nodo.getPadre().buscarHijo(limpio);
            if (existente != null && existente != nodo) {
                throw new IllegalStateException("Ya existe \"" + limpio + "\" en " + nodo.getPadre().getRuta() + ".");
            }
            nodo.setNombre(limpio);
        } finally {
            liberar();
        }
    }

    // -------------------------------------------------------------------------
    // Auxiliares (se llaman con el semáforo ya adquirido)
    // -------------------------------------------------------------------------

    private NodoSistemaArchivos buscarSinBloqueo(String ruta) {
        if (ruta == null || !ruta.startsWith(NodoSistemaArchivos.SEPARADOR)) {
            return null;
        }
        NodoSistemaArchivos actual = raiz;
        String[] partes = ruta.split(NodoSistemaArchivos.SEPARADOR);
        for (String parte : partes) {
            if (parte.isEmpty()) {
                continue;
            }
            if (!(actual instanceof Directorio directorio)) {
                return null;
            }
            actual = directorio.buscarHijo(parte);
            if (actual == null) {
                return null;
            }
        }
        return actual;
    }

    private NodoSistemaArchivos nodoExistente(String ruta) {
        NodoSistemaArchivos nodo = buscarSinBloqueo(ruta);
        if (nodo == null) {
            throw new IllegalArgumentException("No existe la ruta " + ruta + ".");
        }
        return nodo;
    }

    private Directorio directorioExistente(String ruta) {
        NodoSistemaArchivos nodo = nodoExistente(ruta);
        if (!(nodo instanceof Directorio directorio)) {
            throw new IllegalArgumentException(ruta + " no es un directorio.");
        }
        return directorio;
    }

    private void recolectarArchivos(Directorio directorio, ListaEnlazada<Archivo> resultado) {
        int total = directorio.getCantidadHijos();
        for (int i = 0; i < total; i++) {
            NodoSistemaArchivos hijo = directorio.getHijo(i);
            if (hijo instanceof Archivo archivo) {
                resultado.agregar(archivo);
            } else if (hijo instanceof Directorio subdirectorio) {
                recolectarArchivos(subdirectorio, resultado);
            }
        }
    }
}
