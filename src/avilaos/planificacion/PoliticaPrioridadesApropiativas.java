package avilaos.planificacion;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.proceso.Proceso;

/**
 * Implementación de la política de planificación por Prioridades Apropiativas
 * (Preemptive Priority Scheduling).
 *
 * <p>Reglas del algoritmo:
 * <ul>
 *   <li>Cada proceso tiene un nivel de prioridad asignado (mayor valor numérico = mayor prioridad).</li>
 *   <li>El proceso con mayor prioridad en la cola de listos es el primero en ser despachado a la CPU.</li>
 *   <li>En caso de empate en prioridad, se atiende por orden de llegada (FCFS).</li>
 *   <li>Es apropiativa (preemptive): si ingresa a la cola de listos un proceso con prioridad
 *       estrictamente mayor que el que está actualmente en ejecución en la CPU, este último es desalojado.</li>
 *   <li>El ordenamiento manual de la cola de listos se realiza sin librerías externas de Java.</li>
 * </ul>
 * </p>
 */
public class PoliticaPrioridadesApropiativas implements IPoliticaPlanificacion {

    @Override
    public PoliticaPlanificacion getTipo() {
        return PoliticaPlanificacion.PRIORIDADES_APROPIATIVAS;
    }

    @Override
    public Proceso seleccionarSiguiente(ListaEnlazada<Proceso> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }
        // Asegurar que la cola esté ordenada por prioridad antes de extraer el primero
        ordenarCola(colaListos);
        return colaListos.eliminarEn(0);
    }

    /**
     * Ordena manualmente la cola de listos por prioridad de forma descendente
     * (mayor prioridad al inicio) mediante un algoritmo de burbuja optimizado y estable.
     * En caso de prioridades iguales, preserva el orden de llegada (FCFS).
     *
     * @param colaListos lista de procesos en estado LISTO
     */
    @Override
    public void ordenarCola(ListaEnlazada<Proceso> colaListos) {
        if (colaListos == null || colaListos.obtenerTamano() <= 1) {
            return;
        }

        int n = colaListos.obtenerTamano();
        for (int i = 0; i < n - 1; i++) {
            boolean huboIntercambio = false;

            for (int j = 0; j < n - 1 - i; j++) {
                Proceso actual = colaListos.obtener(j);
                Proceso siguiente = colaListos.obtener(j + 1);

                if (actual != null && siguiente != null) {
                    int prioridadActual = actual.getPcb().getPrioridad();
                    int prioridadSiguiente = siguiente.getPcb().getPrioridad();

                    // Orden descendente: si el siguiente tiene mayor prioridad, se intercambian
                    if (prioridadSiguiente > prioridadActual) {
                        colaListos.intercambiar(j, j + 1);
                        huboIntercambio = true;
                    }
                }
            }

            // Si no hubo intercambios en esta pasada, la cola ya está ordenada
            if (!huboIntercambio) {
                break;
            }
        }
    }

    @Override
    public boolean debeDesalojar(Proceso procesoEjecutando, Proceso procesoNuevo, int ciclosEjecutadosEnRafaga, int quantum) {
        if (procesoEjecutando == null || procesoNuevo == null) {
            return false;
        }

        // Desalojo preventivo: si el proceso nuevo tiene mayor prioridad que el que está en CPU
        return procesoNuevo.getPcb().getPrioridad() > procesoEjecutando.getPcb().getPrioridad();
    }

    @Override
    public String toString() {
        return "Prioridades Apropiativas";
    }
}
