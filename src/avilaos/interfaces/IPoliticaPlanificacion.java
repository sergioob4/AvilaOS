package avilaos.interfaces;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.proceso.Proceso;

/**
 * Contrato formal para las estrategias y algoritmos de planificación de CPU en ÁvilaOS.
 *
 * <p>Obligatoria según el Requerimiento 1:
 * <em>"La política de planificación debe definirse mediante una interfaz, de forma que agregar
 * una política nueva no requiera modificar el código del planificador sino extenderlo."</em>
 * </p>
 */
public interface IPoliticaPlanificacion {

    /**
     * Devuelve el tipo enumerado asociado a esta política de planificación.
     *
     * @return política de planificación representada
     */
    PoliticaPlanificacion getTipo();

    /**
     * Selecciona el siguiente proceso a despachar a la CPU desde la cola de listos.
     *
     * @param colaListos lista de procesos actualmente en estado {@code LISTO}
     * @return el proceso seleccionado para ejecutar, o {@code null} si la cola está vacía
     */
    Proceso seleccionarSiguiente(ListaEnlazada<Proceso> colaListos);

    /**
     * Reordena la cola de listos según las reglas específicas del algoritmo
     * (por ejemplo, orden ascendente por deadline en EDF, o por prioridad).
     *
     * @param colaListos lista de procesos en estado {@code LISTO} a ordenar
     */
    void ordenarCola(ListaEnlazada<Proceso> colaListos);

    /**
     * Determina si el proceso actualmente en CPU debe ser desalojado preventivamente
     * (preempted) debido a la llegada de un nuevo proceso o al agotamiento de su quantum.
     *
     * @param procesoEjecutando proceso actualmente asignado a la CPU
     * @param procesoNuevo proceso que acaba de ingresar a la cola de listos
     * @param ciclosEjecutadosEnRafaga ciclos continuos que lleva el proceso en la ráfaga actual
     * @param quantum límite de quantum configurado en el procesador (para Round Robin)
     * @return {@code true} si debe ocurrir desalojo, {@code false} de lo contrario
     */
    boolean debeDesalojar(Proceso procesoEjecutando, Proceso procesoNuevo, int ciclosEjecutadosEnRafaga, int quantum);
}
