package avilaos.sincronizacion;

/**
 * Representa una unidad discreta de dato o elemento generado por un proceso productor
 * y depositado en un {@code BufferAcotado} para su posterior consumo.
 *
 * <p>Cumple con el <strong>Requerimiento 4 (Sincronización y Comunicación)</strong>:
 * registra la trazabilidad completa del ciclo de vida del dato dentro del clúster distribuido:
 * <ul>
 *   <li>Identificador único secuencial del elemento.</li>
 *   <li>Identificador del proceso creador (ej. "P1").</li>
 *   <li>Computador del clúster donde se originó (ej. "C1").</li>
 *   <li>Ciclo de reloj global en que fue insertado.</li>
 *   <li>Ciclo de reloj global en que fue consumido y por cuál proceso.</li>
 * </ul>
 * </p>
 */
public class ElementoBuffer {

    /** Identificador secuencial único del elemento. */
    private final int id;

    /** Contenido o descripción textual del dato simulado. */
    private final String contenido;

    /** Identificador del proceso que produjo el elemento (ej. "P1"). */
    private final String idProcesoProductor;

    /** Identificador del computador donde residía el productor (ej. "C1"). */
    private final String idComputadorOrigen;

    /** Ciclo de reloj global en el que fue depositado en el buffer. */
    private final long cicloProduccion;

    /** Ciclo de reloj global en el que fue extraído por el consumidor (-1 si aún está en buffer). */
    private long cicloConsumo;

    /** Identificador del proceso que consumió el elemento (null si aún está en buffer). */
    private String idProcesoConsumidor;

    /**
     * Construye un nuevo elemento producido en el buffer.
     *
     * @param id identificador numérico único del elemento
     * @param contenido descripción o carga útil del dato
     * @param idProcesoProductor ID del proceso creador
     * @param idComputadorOrigen ID del nodo donde se ejecutó la producción
     * @param cicloProduccion ciclo de reloj global de inserción
     */
    public ElementoBuffer(int id, String contenido, String idProcesoProductor,
                          String idComputadorOrigen, long cicloProduccion) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID del elemento debe ser un entero positivo.");
        }
        this.id = id;
        this.contenido = (contenido != null && !contenido.trim().isEmpty()) ? contenido : "Dato #" + id;
        this.idProcesoProductor = idProcesoProductor != null ? idProcesoProductor : "Desconocido";
        this.idComputadorOrigen = idComputadorOrigen != null ? idComputadorOrigen : "Desconocido";
        this.cicloProduccion = cicloProduccion;
        this.cicloConsumo = -1;
        this.idProcesoConsumidor = null;
    }

    /**
     * Registra el consumo del elemento por parte de un proceso consumidor.
     *
     * @param idConsumidor identificador del proceso que retira el elemento
     * @param cicloActual ciclo global de reloj al momento de la extracción
     */
    public void registrarConsumo(String idConsumidor, long cicloActual) {
        this.idProcesoConsumidor = idConsumidor;
        this.cicloConsumo = cicloActual;
    }

    public int getId() {
        return id;
    }

    public String getContenido() {
        return contenido;
    }

    public String getIdProcesoProductor() {
        return idProcesoProductor;
    }

    public String getIdComputadorOrigen() {
        return idComputadorOrigen;
    }

    public long getCicloProduccion() {
        return cicloProduccion;
    }

    public long getCicloConsumo() {
        return cicloConsumo;
    }

    public String getIdProcesoConsumidor() {
        return idProcesoConsumidor;
    }

    /**
     * Indica si este elemento ya fue retirado y consumido del buffer.
     *
     * @return {@code true} si ya fue consumido; {@code false} si aún reside en el buffer
     */
    public boolean estaConsumido() {
        return cicloConsumo != -1;
    }

    @Override
    public String toString() {
        if (estaConsumido()) {
            return String.format("Item#%d['%s' | Creado: %s@%s en T=%d | Consumido: %s en T=%d]",
                    id, contenido, idProcesoProductor, idComputadorOrigen, cicloProduccion,
                    idProcesoConsumidor, cicloConsumo);
        }
        return String.format("Item#%d['%s' | Creado: %s@%s en T=%d | En Buffer]",
                id, contenido, idProcesoProductor, idComputadorOrigen, cicloProduccion);
    }
}
