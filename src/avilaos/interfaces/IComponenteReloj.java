package avilaos.interfaces;

/**
 * Interfaz común para todos los componentes de hardware o sistema
 * que reaccionan a cada pulso del reloj global en ÁvilaOS.
 *
 * <p>Exigida por el Requerimiento 1 de ÁvilaOS:
 * <em>"Los componentes que reaccionan al reloj (CPU, DMA, etc.) deben compartir una interfaz común."</em>
 * </p>
 */
public interface IComponenteReloj {

    /**
     * Ejecuta las operaciones correspondientes al ciclo de reloj actual.
     *
     * @param cicloActual número secuencial del ciclo de reloj global de la simulación
     */
    void ejecutarCiclo(long cicloActual);
}
