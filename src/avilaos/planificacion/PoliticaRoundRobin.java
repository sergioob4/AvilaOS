package avilaos.planificacion;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.proceso.Proceso;

/**
 * Implementación de la política de planificación Round Robin (RR).
 *
 * <p>Algoritmo apropiativo basado en turno rotatorio con quantum de tiempo configurable.
 * Cada proceso recibe una fracción de tiempo de procesador (quantum). Si la ráfaga
 * de ejecución alcanza o supera el quantum y existen otros procesos listos en espera,
 * el proceso en CPU es desalojado y ubicado al final de la cola de listos.</p>
 */
public class PoliticaRoundRobin implements IPoliticaPlanificacion {

    @Override
    public PoliticaPlanificacion getTipo() {
        return PoliticaPlanificacion.ROUND_ROBIN;
    }

    @Override
    public Proceso seleccionarSiguiente(ListaEnlazada<Proceso> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }
        return colaListos.eliminarEn(0);
    }

    @Override
    public void ordenarCola(ListaEnlazada<Proceso> colaListos) {
        // En Round Robin la cola mantiene un estricto orden de llegada (FIFO)
    }

    @Override
    public boolean debeDesalojar(Proceso procesoEjecutando, Proceso procesoNuevo, int ciclosEjecutadosEnRafaga, int quantum) {
        if (procesoEjecutando == null) {
            return false;
        }
        // Desaloja cuando el proceso agota su quantum y hay otro proceso esperando turno en listos
        return ciclosEjecutadosEnRafaga >= quantum && procesoNuevo != null;
    }

    @Override
    public String toString() {
        return "Round Robin";
    }
}
