package avilaos.planificacion;

import avilaos.enums.PoliticaPlanificacion;
import avilaos.interfaces.IPoliticaPlanificacion;

/**
 * Fábrica para la creación desacoplada de instancias de políticas de planificación.
 *
 * <p>Permite mapear directamente las opciones del enum {@link PoliticaPlanificacion}
 * a sus clases implementadoras concretas, facilitando la conmutación dinámica
 * de políticas en tiempo de ejecución tanto desde la GUI como desde la configuración.</p>
 */
public final class FabricaPoliticas {

    private FabricaPoliticas() {
        // Constructor privado para clase utilitaria
    }

    /**
     * Construye y devuelve una instancia de la política de planificación solicitada.
     *
     * @param tipo enum de la política a instanciar
     * @return nueva instancia de {@link IPoliticaPlanificacion} (por defecto FCFS si es nula)
     */
    public static IPoliticaPlanificacion crear(PoliticaPlanificacion tipo) {
        if (tipo == null) {
            return new PoliticaFCFS();
        }
        return switch (tipo) {
            case FCFS -> new PoliticaFCFS();
            case ROUND_ROBIN -> new PoliticaRoundRobin();
            case PRIORIDADES_APROPIATIVAS -> new PoliticaPrioridadesApropiativas();
            case EDF -> new PoliticaEDF();
        };
    }
}
