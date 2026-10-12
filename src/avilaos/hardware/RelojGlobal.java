package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.interfaces.IComponenteReloj;

/**
 * Reloj Global que sincroniza el clúster distribuido de ÁvilaOS.
 *
 * <p>Requerimiento 1 del PDF:
 * <em>"Debe existir un reloj global que sincronice el sistema distribuido y a los computadores:
 * en cada ciclo de reloj, cada computador avanza exactamente un ciclo de ejecución."</em>
 * </p>
 *
 * <p>Funcionalidades:
 * <ul>
 *   <li>Ejecución en un hilo independiente de Java ({@link Thread}).</li>
 *   <li>Sincroniza componentes que implementan {@link IComponenteReloj} (computadores, CPUs).</li>
 *   <li>Usa nuestra estructura {@link ListaEnlazada} (sin colecciones nativas de Java).</li>
 *   <li>Control dinámico de velocidad del ciclo en milisegundos.</li>
 *   <li>Pausa, reanudación, avance paso a paso y parada segura.</li>
 * </ul>
 * </p>
 */
public class RelojGlobal implements Runnable {

    /** Número del ciclo global actual de la simulación. */
    private volatile long cicloActual;

    /** Duración de un ciclo de reloj en milisegundos (por defecto 1000 ms = 1 seg). */
    private volatile long duracionCicloMs;

    /** Bandera de control para pausar/reanudar el reloj. */
    private volatile boolean enPausa;

    /** Bandera de control para la ejecución del hilo principal del reloj. */
    private volatile boolean ejecutando;

    /** Hilo dedicado para el tic-tac del reloj global. */
    private Thread hiloReloj;

    /** Lista de componentes sincronizados con el reloj (usando estructura propia). */
    private final ListaEnlazada<IComponenteReloj> suscriptores;

    /**
     * Construye el reloj global con una duración inicial de ciclo de 1000 ms.
     */
    public RelojGlobal() {
        this(1000);
    }

    /**
     * Construye el reloj global con una duración configurada.
     *
     * @param duracionCicloMs duración de cada ciclo en milisegundos
     */
    public RelojGlobal(long duracionCicloMs) {
        this.cicloActual = 0;
        this.duracionCicloMs = Math.max(10, duracionCicloMs);
        this.enPausa = true;
        this.ejecutando = false;
        this.suscriptores = new ListaEnlazada<>();
    }

    // -------------------------------------------------------------------------
    // Gestión de Suscriptores (IComponenteReloj)
    // -------------------------------------------------------------------------

    /**
     * Registra un componente para recibir los pulsos de reloj en cada ciclo.
     *
     * @param componente componente que reacciona al reloj
     */
    public void registrarComponente(IComponenteReloj componente) {
        if (componente != null && !suscriptores.contiene(componente)) {
            suscriptores.agregar(componente);
        }
    }

    /**
     * Da de baja a un componente para que no reciba más pulsos de reloj.
     *
     * @param componente componente a desuscribir
     */
    public void desregistrarComponente(IComponenteReloj componente) {
        if (componente != null) {
            suscriptores.eliminar(componente);
        }
    }

    // -------------------------------------------------------------------------
    // Avance de Ciclo
    // -------------------------------------------------------------------------

    /**
     * Ejecuta exactamente un ciclo de reloj global de forma síncrona.
     * Notifica a todos los componentes registrados para que avancen un ciclo.
     */
    public synchronized void avanzarUnCiclo() {
        cicloActual++;
        int total = suscriptores.obtenerTamano();
        for (int i = 0; i < total; i++) {
            try {
                IComponenteReloj comp = suscriptores.obtener(i);
                if (comp != null) {
                    comp.ejecutarCiclo(cicloActual);
                }
            } catch (Exception e) {
                System.err.println("Error al sincronizar ciclo " + cicloActual + " en componente: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hilo de Ejecución (Thread de Java)
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        while (ejecutando) {
            try {
                if (!enPausa) {
                    avanzarUnCiclo();
                }
                Thread.sleep(duracionCicloMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Inicia el hilo del reloj global de simulación.
     */
    public synchronized void iniciar() {
        if (!ejecutando) {
            ejecutando = true;
            enPausa = false;
            hiloReloj = new Thread(this, "Hilo-RelojGlobal");
            hiloReloj.setDaemon(true);
            hiloReloj.start();
        }
    }

    /**
     * Pausa el avance automático del reloj.
     */
    public synchronized void pausar() {
        this.enPausa = true;
    }

    /**
     * Reanuda el avance automático del reloj.
     */
    public synchronized void reanudar() {
        this.enPausa = false;
    }

    /**
     * Detiene definitivamente el hilo del reloj global.
     */
    public synchronized void detener() {
        this.ejecutando = false;
        if (hiloReloj != null) {
            hiloReloj.interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Consultas y Configuración
    // -------------------------------------------------------------------------

    public long getCicloActual() {
        return cicloActual;
    }

    public long getDuracionCicloMs() {
        return duracionCicloMs;
    }

    public void setDuracionCicloMs(long duracionCicloMs) {
        this.duracionCicloMs = Math.max(10, duracionCicloMs);
    }

    public boolean estaEnPausa() {
        return enPausa;
    }

    public boolean estaEjecutando() {
        return ejecutando;
    }

    /**
     * Reinicia el conteo de ciclos a 0.
     */
    public synchronized void reiniciar() {
        this.cicloActual = 0;
    }
}
