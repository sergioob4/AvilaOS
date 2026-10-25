package avilaos.hardware;

import avilaos.enums.ModoEjecucion;
import avilaos.interfaces.IComponenteReloj;
import avilaos.proceso.Proceso;

/**
 * Representa la Unidad Central de Procesamiento (CPU) de un computador en ÁvilaOS.
 *
 * <p>Implementa la interfaz {@link IComponenteReloj} conforme al Requerimiento 1:
 * <em>"Los componentes que reaccionan al reloj (CPU, DMA, etc.) deben compartir una interfaz común."</em>
 * </p>
 *
 * <p>Reglas de operación estandarizadas según el PDF:
 * <ul>
 *   <li>Cada instrucción se ejecuta en un único ciclo de reloj.</li>
 *   <li>El Program Counter (PC) y el MAR se incrementan linealmente por ciclo.</li>
 *   <li>Conmuta entre {@link ModoEjecucion#USUARIO} y {@link ModoEjecucion#SISTEMA_OPERATIVO}.</li>
 *   <li>Lleva la cuenta de ciclos activos e inactivos para el cálculo de utilización de CPU.</li>
 * </ul>
 * </p>
 */
public class CPU implements IComponenteReloj {

    /** Identificador o nombre de la CPU (ej. "CPU-C1"). */
    private final String id;

    /** Proceso actualmente despachado en la CPU (o {@code null} si está ociosa). */
    private Proceso procesoActual;

    /** Modo de ejecución actual de la CPU (Usuario o Sistema Operativo). */
    private ModoEjecucion modoEjecucion;

    /** Registro Contador de Programa (PC) reflejado en hardware. */
    private int programCounter;

    /** Registro de Dirección de Memoria (MAR) reflejado en hardware. */
    private int memoryAddressRegister;

    /** Ciclos continuos ejecutados por el proceso actual en la ráfaga presente (para quantum). */
    private int ciclosEnRafagaActual;

    // -------------------------------------------------------------------------
    // Métricas de Rendimiento
    // -------------------------------------------------------------------------

    /** Total de ciclos en los que la CPU ejecutó instrucciones (útiles o de SO). */
    private long ciclosOcupada;

    /** Total de ciclos en los que la CPU estuvo ociosa (sin proceso asignado). */
    private long ciclosOciosa;

    /** Total general de ciclos de reloj procesados por esta CPU. */
    private long totalCiclosProcesados;

    /** Capacidad máxima del historial de utilización (muestras más recientes). */
    private static final int CAPACIDAD_HISTORIAL = 1000;

    /** Buffer circular con el % de utilización acumulado tras cada ciclo. */
    private final double[] historialUtilizacion = new double[CAPACIDAD_HISTORIAL];

    /** Cantidad de muestras válidas en el historial (hasta {@link #CAPACIDAD_HISTORIAL}). */
    private int muestrasHistorial;

    /**
     * Construye una nueva CPU para un computador.
     *
     * @param id identificador de la CPU
     */
    public CPU(String id) {
        this.id = id;
        this.procesoActual = null;
        this.modoEjecucion = ModoEjecucion.SISTEMA_OPERATIVO;
        this.programCounter = 0;
        this.memoryAddressRegister = 0;
        this.ciclosEnRafagaActual = 0;
        this.ciclosOcupada = 0;
        this.ciclosOciosa = 0;
        this.totalCiclosProcesados = 0;
    }

    // -------------------------------------------------------------------------
    // Reacción al Reloj (IComponenteReloj)
    // -------------------------------------------------------------------------

    @Override
    public synchronized void ejecutarCiclo(long cicloActual) {
        totalCiclosProcesados++;

        if (procesoActual != null) {
            ciclosOcupada++;
            ciclosEnRafagaActual++;

            // Ejecuta un ciclo de instrucción del proceso
            boolean finalizo = procesoActual.ejecutarInstruccion(cicloActual);

            // Sincronizar registros visibles de hardware con el PCB
            this.programCounter = procesoActual.getPcb().getProgramCounter();
            this.memoryAddressRegister = procesoActual.getPcb().getMemoryAddressRegister();

            if (finalizo) {
                // Al finalizar la última instrucción, la CPU entra en modo SO (syscall de salida)
                // y el SO se encargará de desalojar, liberar RAM y registrar en terminados
                modoEjecucion = ModoEjecucion.SISTEMA_OPERATIVO;
            }
        } else {
            ciclosOciosa++;
            ciclosEnRafagaActual = 0;
            modoEjecucion = ModoEjecucion.SISTEMA_OPERATIVO;
        }

        registrarMuestraUtilizacion();
    }

    /** Guarda la utilización acumulada de este ciclo en el buffer circular. */
    private void registrarMuestraUtilizacion() {
        historialUtilizacion[(int) ((totalCiclosProcesados - 1) % CAPACIDAD_HISTORIAL)] = getPorcentajeUtilizacion();
        if (muestrasHistorial < CAPACIDAD_HISTORIAL) {
            muestrasHistorial++;
        }
    }

    /**
     * Devuelve una copia ordenada (de la más antigua a la más reciente) del historial
     * de utilización acumulada. La muestra {@code i} corresponde al ciclo
     * {@link #getCicloInicialHistorial()} {@code + i}.
     *
     * @return utilización (0.0 a 100.0) por ciclo
     */
    public synchronized double[] getHistorialUtilizacion() {
        double[] copia = new double[muestrasHistorial];
        long primero = totalCiclosProcesados - muestrasHistorial;
        for (int i = 0; i < muestrasHistorial; i++) {
            copia[i] = historialUtilizacion[(int) ((primero + i) % CAPACIDAD_HISTORIAL)];
        }
        return copia;
    }

    /** Ciclo (contado desde 1 en esta CPU) al que corresponde la primera muestra del historial. */
    public synchronized long getCicloInicialHistorial() {
        return totalCiclosProcesados - muestrasHistorial + 1;
    }

    // -------------------------------------------------------------------------
    // Despacho y Cambio de Contexto
    // -------------------------------------------------------------------------

    /**
     * Asigna un proceso a la CPU (cambio de contexto hacia el proceso).
     *
     * @param nuevoProceso proceso a ejecutar
     * @param cicloActual ciclo global actual
     */
    public synchronized void cargarProceso(Proceso nuevoProceso, long cicloActual) {
        this.procesoActual = nuevoProceso;
        this.ciclosEnRafagaActual = 0;

        if (nuevoProceso != null) {
            nuevoProceso.pasarAEjecucion(cicloActual);
            this.programCounter = nuevoProceso.getPcb().getProgramCounter();
            this.memoryAddressRegister = nuevoProceso.getPcb().getMemoryAddressRegister();
            this.modoEjecucion = ModoEjecucion.USUARIO;
        } else {
            this.modoEjecucion = ModoEjecucion.SISTEMA_OPERATIVO;
        }
    }

    /**
     * Desaloja el proceso actualmente en CPU (cambio de contexto hacia afuera).
     *
     * @return el proceso que estaba ejecutando, o {@code null}
     */
    public synchronized Proceso desalojarProceso() {
        Proceso desalojado = this.procesoActual;
        this.procesoActual = null;
        this.ciclosEnRafagaActual = 0;
        this.modoEjecucion = ModoEjecucion.SISTEMA_OPERATIVO;
        return desalojado;
    }

    // -------------------------------------------------------------------------
    // Consultas y Métricas
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public synchronized Proceso getProcesoActual() {
        return procesoActual;
    }

    public synchronized ModoEjecucion getModoEjecucion() {
        return modoEjecucion;
    }

    public synchronized void setModoEjecucion(ModoEjecucion modo) {
        this.modoEjecucion = modo;
    }

    public synchronized int getProgramCounter() {
        return programCounter;
    }

    public synchronized int getMemoryAddressRegister() {
        return memoryAddressRegister;
    }

    public synchronized int getCiclosEnRafagaActual() {
        return ciclosEnRafagaActual;
    }

    public synchronized long getCiclosOcupada() {
        return ciclosOcupada;
    }

    public synchronized long getCiclosOciosa() {
        return ciclosOciosa;
    }

    public synchronized long getTotalCiclosProcesados() {
        return totalCiclosProcesados;
    }

    /**
     * Calcula el porcentaje de utilización acumulado de la CPU.
     *
     * @return utilización entre 0.0% y 100.0%
     */
    public synchronized double getPorcentajeUtilizacion() {
        if (totalCiclosProcesados == 0) {
            return 0.0;
        }
        return ((double) ciclosOcupada / totalCiclosProcesados) * 100.0;
    }

    public synchronized boolean estaOciosa() {
        return procesoActual == null;
    }

    @Override
    public synchronized String toString() {
        return "CPU[" + id + " | " + modoEjecucion.getEtiquetaCorta() +
                " | PC:" + programCounter + " | " +
                (procesoActual != null ? procesoActual.getId() : "OCIOSA") +
                " | Util:" + String.format("%.1f", getPorcentajeUtilizacion()) + "%]";
    }
}
