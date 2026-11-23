package avilaos.sistemaarchivos;

import avilaos.enums.Permiso;
import avilaos.enums.TipoNodo;

/**
 * Archivo del sistema de archivos.
 *
 * <p>Su contenido no se simula: solo su tamaño en bloques y el primer bloque de su
 * cadena en el disco (asignación encadenada; cada bloque del disco apunta al siguiente).</p>
 */
public class Archivo extends NodoSistemaArchivos {

    /** Valor de {@link #getPrimerBloque()} mientras el archivo no tiene bloques asignados. */
    public static final int SIN_BLOQUES = -1;

    private final int tamanoBloques;
    private int primerBloque;
    private final String procesoCreador;

    /**
     * @param nombre nombre del archivo
     * @param dueno usuario dueño
     * @param permiso permiso de acceso
     * @param tamanoBloques tamaño en bloques (mayor que cero)
     * @param procesoCreador ID del proceso que lo creó, o {@code null} si viene de la carga inicial
     */
    public Archivo(String nombre, String dueno, Permiso permiso, int tamanoBloques, String procesoCreador) {
        super(validarNombre(nombre), dueno, permiso);
        if (tamanoBloques < 1) {
            throw new IllegalArgumentException("El archivo debe ocupar al menos un bloque.");
        }
        this.tamanoBloques = tamanoBloques;
        this.primerBloque = SIN_BLOQUES;
        this.procesoCreador = procesoCreador;
    }

    @Override
    public TipoNodo getTipo() {
        return TipoNodo.ARCHIVO;
    }

    @Override
    public int getTamanoBloques() {
        return tamanoBloques;
    }

    public int getPrimerBloque() {
        return primerBloque;
    }

    /** @return ID del proceso creador o "Carga inicial" si vino del JSON */
    public String getProcesoCreador() {
        return procesoCreador == null ? "Carga inicial" : procesoCreador;
    }

    void setPrimerBloque(int primerBloque) {
        this.primerBloque = primerBloque;
    }
}
