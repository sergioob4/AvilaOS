package avilaos.planificacion;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IPoliticaPlanificacion;
import avilaos.proceso.Proceso;

/**
 * Implementación de la política de planificación Earliest Deadline First (EDF).
 *
 * <p>Reglas del algoritmo:
 * <ul>
 *   <li>Política de tiempo real donde el proceso con el tiempo límite (deadline) restante
 *       más próximo a cero obtiene la mayor urgencia de ejecución.</li>
 *   <li>La cola de listos se ordena de forma ascendente según el {@code deadlineRestante}.</li>
 *   <li>En caso de empate en deadline restante, se mantiene el orden de llegada (FCFS).</li>
 *   <li>Es apropiativa (preemptive): si un proceso que ingresa a la cola de listos tiene
 *       un deadline menor que el proceso actualmente en CPU, este último es desalojado.</li>
 *   <li>El ordenamiento manual de la cola se realiza sin colecciones externas de Java.</li>
 * </ul>
 * </p>
 */
public class PoliticaEDF implements IPoliticaPlanificacion {

    @Override
    public PoliticaPlanificacion getTipo() {
        return PoliticaPlanificacion.EDF;
    }

    @Override
    public Proceso seleccionarSiguiente(ListaEnlazada<Proceso> colaListos) {
        if (colaListos == null || colaListos.estaVacia()) {
            return null;
        }
        // Asegurar que la cola esté ordenada por menor deadline restante antes de extraer el primero
        ordenarCola(colaListos);
        return colaListos.eliminarEn(0);
    }

    /**
     * Ordena manualmente la cola de listos por deadline restante de forma ascendente
     * (menor deadline primero, mayor urgencia) mediante un algoritmo de burbuja optimizado y estable.
     * En caso de empate en deadline, preserva el orden de llegada (FCFS).
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
                    int deadlineActual = actual.getPcb().getDeadlineRestante();
                    int deadlineSiguiente = siguiente.getPcb().getDeadlineRestante();

                    // Orden ascendente: si el siguiente tiene menor deadline (más urgente), se intercambian
                    if (deadlineSiguiente < deadlineActual) {
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

        // Desalojo preventivo: si el proceso nuevo tiene un deadline restante menor que el proceso en CPU
        return procesoNuevo.getPcb().getDeadlineRestante() < procesoEjecutando.getPcb().getDeadlineRestante();
    }

    @Override
    public String toString() {
        return "EDF";
    }
}
