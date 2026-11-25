package avilaos.sistemaarchivos;

import avilaos.enums.ModoUsuario;
import avilaos.enums.OperacionArchivo;
import avilaos.proceso.PeticionArchivo;
import avilaos.enums.Permiso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.ConfiguracionDisco;
import avilaos.hardware.DiscoSimulado;
import avilaos.hardware.SolicitudDisco;
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
    private final DiscoSimulado disco;
    private record Escritura(long id, PeticionArchivo peticion, NodoSistemaArchivos nodo) {}
    private final ListaEnlazada<Escritura> escrituras = new ListaEnlazada<>();

    public SistemaArchivos() {
        this(new DiscoSimulado(ConfiguracionDisco.PREDETERMINADA));
    }

    public SistemaArchivos(DiscoSimulado disco) {
        if (disco == null) {
            throw new IllegalArgumentException("El sistema de archivos requiere un disco.");
        }
        this.disco = disco;
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

    /** Raíz de una copia estable que se puede recorrer fuera del hilo del SO. */
    public Directorio getRaiz() {
        adquirir();
        try {
            return raiz.copiar();
        } finally {
            liberar();
        }
    }

    /**
     * Busca un nodo por su ruta absoluta.
     *
     * @param ruta ruta como {@code /sistema/boot.bin}
     * @return el nodo o {@code null} si no existe
     */
    public NodoSistemaArchivos buscar(String ruta) {
        return buscarDesde(getRaiz(), ruta);
    }

    /**
     * Autoriza la lectura y encola la cadena mientras el árbol sigue protegido.
     * El SO proporciona la identidad y el modo de la sesión que solicitó la operación.
     */
    public SolicitudDisco solicitarLectura(String ruta, String usuario, ModoUsuario modo) {
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
            return disco.iniciarTransferencia(inicio, DiscoSimulado.Operacion.LECTURA);
        } finally {
            liberar();
        }
    }

    /** @return todos los archivos del árbol en recorrido en profundidad (para la tabla de asignación) */
    public ListaEnlazada<Archivo> listarArchivos() {
        ListaEnlazada<Archivo> resultado = new ListaEnlazada<>();
        recolectarArchivos(getRaiz(), resultado);
        return resultado;
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
    public Directorio crearDirectorio(String rutaPadre, String nombre, String dueno, Permiso permiso,
                                      ModoUsuario modo) {
        exigirAdministrador(modo);
        adquirir();
        try {
            Directorio padre = directorioExistente(rutaPadre);
            Directorio nuevo = new Directorio(nombre, dueno, permiso);
            padre.agregarHijo(nuevo);
            return (Directorio) buscarDesde(raiz.copiar(), nuevo.getRuta());
        } finally {
            liberar();
        }
    }

    /** Limpia todos los elementos del árbol y las escrituras pendientes (al cargar JSON 1). */
    public void reiniciar() {
        adquirir();
        try {
            while (raiz.getCantidadHijos() > 0) {
                raiz.quitarHijo(raiz.getHijo(0));
            }
            escrituras.vaciar();
        } finally {
            liberar();
        }
    }

    /** Carga un directorio desde JSON creando los directorios padres intermedios si aún no existen. */
    public void cargarDirectorioDesdeJson(String rutaAbsoluta, String dueno, Permiso permiso) {
        if (rutaAbsoluta == null || !rutaAbsoluta.startsWith(NodoSistemaArchivos.SEPARADOR)) {
            throw new IllegalArgumentException("La ruta debe comenzar con '/': " + rutaAbsoluta);
        }
        if (rutaAbsoluta.equals(NodoSistemaArchivos.SEPARADOR)) {
            return;
        }
        adquirir();
        try {
            int sep = rutaAbsoluta.lastIndexOf('/');
            String rutaPadre = sep == 0 ? "/" : rutaAbsoluta.substring(0, sep);
            String nombre = rutaAbsoluta.substring(sep + 1);
            Directorio padre = asegurarDirectorio(rutaPadre);
            NodoSistemaArchivos existente = padre.buscarHijo(nombre);
            if (existente != null) {
                if (!(existente instanceof Directorio)) {
                    throw new IllegalStateException("Ya existe un archivo en la ruta " + rutaAbsoluta);
                }
                return;
            }
            padre.agregarHijo(new Directorio(nombre, dueno, permiso));
        } finally {
            liberar();
        }
    }

    /** Carga un archivo desde JSON exactamente en {@code startBlock} con {@code tamanoBloques} bloques. */
    public void cargarArchivoDesdeJson(String rutaAbsoluta, String dueno, Permiso permiso,
                                       int startBlock, int tamanoBloques) {
        if (rutaAbsoluta == null || !rutaAbsoluta.startsWith(NodoSistemaArchivos.SEPARADOR)
                || rutaAbsoluta.equals(NodoSistemaArchivos.SEPARADOR)) {
            throw new IllegalArgumentException("Ruta de archivo inválida: " + rutaAbsoluta);
        }
        adquirir();
        try {
            int sep = rutaAbsoluta.lastIndexOf('/');
            String rutaPadre = sep == 0 ? "/" : rutaAbsoluta.substring(0, sep);
            String nombre = rutaAbsoluta.substring(sep + 1);
            Directorio padre = asegurarDirectorio(rutaPadre);
            Archivo archivo = new Archivo(nombre, dueno, permiso, tamanoBloques, null);
            padre.agregarHijo(archivo);
            try {
                archivo.setPrimerBloque(disco.asignarArchivoEn(archivo.getRuta(), startBlock, tamanoBloques));
            } catch (RuntimeException fallo) {
                padre.quitarHijo(archivo);
                throw fallo;
            }
        } finally {
            liberar();
        }
    }

    private Directorio asegurarDirectorio(String ruta) {
        if (ruta == null || ruta.equals(NodoSistemaArchivos.SEPARADOR)) {
            return raiz;
        }
        Directorio actual = raiz;
        String[] partes = ruta.split(NodoSistemaArchivos.SEPARADOR);
        for (String parte : partes) {
            if (parte.isEmpty()) continue;
            NodoSistemaArchivos hijo = actual.buscarHijo(parte);
            if (hijo == null) {
                Directorio nuevo = new Directorio(parte, ADMINISTRADOR, Permiso.PUBLICO);
                actual.agregarHijo(nuevo);
                actual = nuevo;
            } else if (hijo instanceof Directorio dir) {
                actual = dir;
            } else {
                throw new IllegalArgumentException("El componente '" + parte + "' de " + ruta + " no es un directorio.");
            }
        }
        return actual;
    }

    /** Registra un archivo y reserva su cadena de bloques en el disco asociado al árbol. */
    public Archivo crearArchivo(String rutaPadre, String nombre, String dueno, Permiso permiso,
                                int tamanoBloques, String procesoCreador, String usuario, ModoUsuario modo) {
        if (modo == null || usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("La creación requiere un usuario y un modo.");
        }
        adquirir();
        try {
            Directorio padre = directorioExistente(rutaPadre);
            Archivo archivo = new Archivo(nombre, dueno, permiso, tamanoBloques, procesoCreador);
            validarCreacion(padre, archivo, usuario, modo);
            padre.agregarHijo(archivo);
            try {
                archivo.setPrimerBloque(disco.asignarArchivo(archivo.getRuta(), tamanoBloques));
            } catch (RuntimeException fallo) {
                padre.quitarHijo(archivo);
                throw fallo;
            }
            return (Archivo) buscarDesde(raiz.copiar(), archivo.getRuta());
        } finally {
            liberar();
        }
    }

    /** Elimina un archivo en modo administrador una vez que el disco permite liberar su cadena. */
    public void eliminarArchivo(String ruta, ModoUsuario modo) {
        exigirAdministrador(modo);
        adquirir();
        try {
            if (!(nodoExistente(ruta) instanceof Archivo archivo)) {
                throw new IllegalArgumentException(ruta + " no es un archivo.");
            }
            borrarArchivo(archivo);
        } finally {
            liberar();
        }
    }

    /** Elimina el subárbol solo cuando se pueden liberar todas sus cadenas. */
    public void eliminarDirectorio(String ruta, ModoUsuario modo) {
        exigirAdministrador(modo);
        adquirir();
        try {
            borrarDirectorio(directorioExistente(ruta));
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
    public void renombrar(String ruta, String nuevoNombre, ModoUsuario modo) {
        exigirAdministrador(modo);
        adquirir();
        try {
            NodoSistemaArchivos nodo = nodoExistente(ruta);
            if (nodo == raiz) {
                throw new IllegalArgumentException("La raíz no puede renombrarse.");
            }
            renombrarNodo(nodo, nuevoNombre);
        } finally {
            liberar();
        }
    }

    private void renombrarNodo(NodoSistemaArchivos nodo, String nuevoNombre) {
        String limpio = NodoSistemaArchivos.validarNombre(nuevoNombre);
        NodoSistemaArchivos existente = nodo.getPadre().buscarHijo(limpio);
        if (existente != null && existente != nodo) {
            throw new IllegalStateException("Ya existe \"" + limpio + "\" en " + nodo.getPadre().getRuta() + ".");
        }
        String anterior = nodo.getRuta();
        String nueva = anterior.substring(0, anterior.lastIndexOf('/') + 1) + limpio;
        disco.renombrarRuta(anterior, nueva);
        nodo.setNombre(limpio);
    }

    private void borrarDirectorio(Directorio directorio) {
        if (directorio == raiz) throw new IllegalArgumentException("La raíz no puede eliminarse.");
        for (int i = 0; i < escrituras.obtenerTamano(); i++) {
            if (escrituras.obtener(i).nodo().estaDentroDe(directorio)) {
                throw new IllegalStateException("Hay una escritura pendiente en el directorio.");
            }
        }
        ListaEnlazada<Archivo> archivos = new ListaEnlazada<>();
        recolectarArchivos(directorio, archivos);
        int[] inicios = new int[archivos.obtenerTamano()];
        for (int i = 0; i < inicios.length; i++) inicios[i] = archivos.obtener(i).getPrimerBloque();
        disco.liberarCadenas(inicios);
        directorio.getPadre().quitarHijo(directorio);
        for (int i = 0; i < inicios.length; i++) archivos.obtener(i).setPrimerBloque(Archivo.SIN_BLOQUES);
    }

    private void borrarArchivo(Archivo archivo) {
        disco.liberar(archivo.getPrimerBloque());
        archivo.getPadre().quitarHijo(archivo);
        archivo.setPrimerBloque(Archivo.SIN_BLOQUES);
    }

    private static void validarCreacion(Directorio padre, Archivo archivo, String usuario, ModoUsuario modo) {
        if (!modo.esAdministrador() && (!padre.getDueno().equals(usuario.trim())
                || !archivo.getDueno().equals(usuario.trim()))) {
            throw new SecurityException("Solo puede crear archivos propios dentro de sus directorios.");
        }
    }

    /** La creación reserva su cadena; los cambios del árbol esperan la confirmación del disco. */
    public SolicitudDisco solicitarOperacion(PeticionArchivo peticion, String proceso) {
        if (peticion.operacion() == OperacionArchivo.LEER) {
            return solicitarLectura(peticion.ruta(), peticion.usuario(), peticion.modo());
        }
        adquirir();
        try {
            Archivo archivo;
            boolean crear = peticion.operacion() == OperacionArchivo.CREAR;
            if (crear) {
                archivo = reservarCreacion(peticion, proceso);
            } else {
                exigirAdministrador(peticion.modo());
                NodoSistemaArchivos destino = nodoExistente(peticion.ruta());
                if (destino == raiz) throw new IllegalArgumentException("La raíz no puede modificarse.");
                if (destino instanceof Directorio directorio) {
                    SolicitudDisco solicitud = disco.escribirDirectorio(directorio.getRuta());
                    escrituras.agregar(new Escritura(solicitud.id(), peticion, directorio));
                    return solicitud;
                }
                archivo = (Archivo) destino;
            }
            SolicitudDisco solicitud;
            try {
                solicitud = disco.iniciarTransferencia(archivo.getPrimerBloque(), DiscoSimulado.Operacion.ESCRITURA);
            } catch (RuntimeException fallo) {
                if (crear) disco.liberar(archivo.getPrimerBloque());
                throw fallo;
            }
            escrituras.agregar(new Escritura(solicitud.id(), peticion, archivo));
            return solicitud;
        } finally {
            liberar();
        }
    }

    private Archivo reservarCreacion(PeticionArchivo peticion, String proceso) {
        String ruta = peticion.ruta();
        int separador = ruta.lastIndexOf('/');
        Directorio padre = directorioExistente(separador == 0 ? "/" : ruta.substring(0, separador));
        Archivo archivo = new Archivo(ruta.substring(separador + 1), peticion.usuario(), peticion.permiso(),
                peticion.tamanoBloques(), proceso);
        validarCreacion(padre, archivo, peticion.usuario(), peticion.modo());
        if (padre.buscarHijo(archivo.getNombre()) != null) {
            throw new IllegalStateException("Ya existe un elemento en " + ruta);
        }
        archivo.setPadre(padre);
        archivo.setPrimerBloque(disco.asignarArchivo(archivo.getRuta(), archivo.getTamanoBloques()));
        return archivo;
    }

    public void completarOperacion(long id) {
        adquirir();
        try {
            Escritura escritura = retirarEscritura(id);
            if (escritura == null) return;
            NodoSistemaArchivos nodo = escritura.nodo();
            switch (escritura.peticion().operacion()) {
                case CREAR -> {
                    Archivo archivo = (Archivo) nodo;
                    try {
                        if (!archivo.estaDentroDe(raiz)) throw new IllegalStateException("El directorio fue eliminado.");
                        archivo.getPadre().agregarHijo(archivo);
                    } catch (RuntimeException fallo) {
                        disco.liberar(archivo.getPrimerBloque());
                        throw fallo;
                    }
                }
                case ACTUALIZAR, ELIMINAR -> {
                    if (!nodo.estaDentroDe(raiz)) throw new IllegalStateException("La ruta fue eliminada.");
                    if (escritura.peticion().operacion() == OperacionArchivo.ACTUALIZAR) {
                        renombrarNodo(nodo, escritura.peticion().nuevoNombre());
                    } else if (nodo instanceof Directorio directorio) {
                        borrarDirectorio(directorio);
                    } else {
                        borrarArchivo((Archivo) nodo);
                    }
                }
                default -> throw new IllegalStateException("Operación de escritura inválida.");
            }
        } finally {
            liberar();
        }
    }

    public void cancelarOperacion(long id) {
        adquirir();
        try {
            disco.cancelarTransferencia(id);
            Escritura escritura = retirarEscritura(id);
            if (escritura != null && escritura.peticion().operacion() == OperacionArchivo.CREAR) {
                disco.liberar(((Archivo) escritura.nodo()).getPrimerBloque());
            }
        } finally {
            liberar();
        }
    }

    private Escritura retirarEscritura(long id) {
        for (int i = 0; i < escrituras.obtenerTamano(); i++) {
            if (escrituras.obtener(i).id() == id) return escrituras.eliminarEn(i);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    private static void exigirAdministrador(ModoUsuario modo) {
        if (modo != ModoUsuario.ADMINISTRADOR) {
            throw new SecurityException("Se requiere el modo administrador para modificar el sistema de archivos.");
        }
    }

    private NodoSistemaArchivos buscarDesde(Directorio origen, String ruta) {
        if (ruta == null || !ruta.startsWith(NodoSistemaArchivos.SEPARADOR)) {
            return null;
        }
        NodoSistemaArchivos actual = origen;
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
        NodoSistemaArchivos nodo = buscarDesde(raiz, ruta);
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
