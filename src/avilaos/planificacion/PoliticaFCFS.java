package avilaos.planificacion;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.proceso.Proceso;

/**
 * Implementación de la política de planificación First-Come, First-Served (FCFS).
 *
 * <p>El primer proceso que llega a la cola de listos es el primero en ejecutarse.
 * Es un algoritmo no apropiativo (no expulsa de la CPU al proceso en ejecución).</p>
 */
public class PoliticaFCFS implements IPoliticaPlanificacion {

    @Override
    public PoliticaPlanificacion getTipo() {
        return PoliticaPlanificacion.FCFS;
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
        // En FCFS el orden es estrictamente por orden de llegada (no se reordena)
    }

    @Override
    public boolean debeDesalojar(Proceso procesoEjecutando, Proceso procesoNuevo, int ciclosEjecutadosEnRafaga, int quantum) {
        // FCFS nunca desaloja preventivamente
        return false;
    }

    @Override
    public String toString() {
        return "FCFS";
    }
}
