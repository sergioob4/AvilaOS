package avilaos.hardware;

import avilaos.interfaces.IComponenteReloj;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import avilaos.enums.PoliticaDisco;
import avilaos.interfaces.IPoliticaDisco;
import avilaos.estructuras.ListaEnlazada;

/** Disco local con asignación encadenada. Las direcciones de swap ocupan el extremo superior. */
public final class DiscoSimulado implements IComponenteReloj {
    public enum Operacion { LECTURA, ESCRITURA }
    public enum Direccion { ASCENDENTE, DESCENDENTE }
    public record Bloque(int numero, boolean swap, String ocupante, int siguiente) {}
    public record Vista(Bloque[] bloques, int cabezal, Direccion direccion, Operacion operacion,
                        PoliticaDisco politica, SolicitudDisco activa, SolicitudDisco[] pendientes,
                        long desplazamiento) {}

    private final Semaphore mutex = new Semaphore(1, true);
    private final ConfiguracionDisco config;
    private final Bloque[] bloques;
    private int cabezal;
    private Direccion direccion = Direccion.ASCENDENTE;
    private final ListaEnlazada<SolicitudDisco> cola = new ListaEnlazada<>();
    private final Consumer<String> registrarEvento;
    private PoliticaDisco politica;
    private SolicitudDisco activa;
    private boolean barridoAscendente = true;
    private long desplazamiento;
    private long siguienteId = 1;
    private int objetivo = -1;

    public DiscoSimulado(ConfiguracionDisco config) {
        this(config, mensaje -> {});
    }

    public DiscoSimulado(ConfiguracionDisco config, Consumer<String> registrarEvento) {
        this.registrarEvento = registrarEvento;
        this.config = config;
        politica = config.politica();
        bloques = new Bloque[config.bloques()];
        cabezal = config.cabezal();
        for (int i = 0; i < bloques.length; i++) {
            bloques[i] = new Bloque(i, i >= bloques.length - config.swap(), null, -1);
        }
    }

    public int asignarArchivo(String ruta, int cantidad) {
        return asignar(ruta, cantidad, false);
    }

    public int asignarSwap(String proceso, int unidadesRam) {
        return asignar(proceso, unidadesRam, true);
    }

    private int asignar(String ocupante, int cantidad, boolean swap) {
        if (ocupante == null || ocupante.isBlank() || cantidad < 1) {
            throw new IllegalArgumentException("Indique ocupante y tamaño positivo.");
        }
        mutex.acquireUninterruptibly();
        try {
            int libres = 0;
            for (Bloque bloque : bloques) {
                if (bloque.swap() != swap) continue;
                if (ocupante.equals(bloque.ocupante())) throw new IllegalStateException("Ya tiene bloques asignados.");
                if (bloque.ocupante() == null) libres++;
            }
            if (libres < cantidad) throw new IllegalStateException("Bloques libres insuficientes.");
            int primero = -1;
            // Cada bloque nuevo apunta a la cadena ya construida.
            for (int i = 0; i < bloques.length && cantidad > 0; i++) {
                Bloque bloque = bloques[i];
                if (bloque.swap() == swap && bloque.ocupante() == null) {
                    bloques[i] = new Bloque(i, swap, ocupante, primero);
                    primero = i;
                    cantidad--;
                }
            }
            return primero;
        } finally {
            mutex.release();
        }
    }

    private void validarInicio(int inicio) {
        if (inicio < 0 || inicio >= bloques.length || bloques[inicio].ocupante() == null) {
            throw new IllegalArgumentException("Primer bloque inválido.");
        }
        for (Bloque bloque : bloques) {
            if (bloque.siguiente() == inicio) throw new IllegalArgumentException("Se requiere el inicio de la cadena.");
        }
    }

    public void liberar(int inicio) {
        mutex.acquireUninterruptibly();
        try {
            validarInicio(inicio);
            if (activa != null && activa.inicio() == inicio) {
                throw new IllegalStateException("Transferencia en curso.");
            }
            for (int i = 0; i < cola.obtenerTamano(); i++) {
                if (cola.obtener(i).inicio() == inicio) throw new IllegalStateException("Transferencia pendiente.");
            }
            for (int i = inicio; i >= 0;) {
                Bloque bloque = bloques[i];
                bloques[i] = new Bloque(i, bloque.swap(), null, -1);
                i = bloque.siguiente();
            }
        } finally {
            mutex.release();
        }
    }

    public SolicitudDisco iniciarTransferencia(int inicio, Operacion tipo) {
        mutex.acquireUninterruptibly();
        try {
            validarInicio(inicio);
            if (tipo == null) throw new IllegalArgumentException("Indique una operación.");
            SolicitudDisco solicitud = new SolicitudDisco(siguienteId++, inicio, tipo, bloques[inicio].ocupante());
            cola.agregar(solicitud);
            return solicitud;
        } finally {
            mutex.release();
        }
    }

    public void cambiarPolitica(PoliticaDisco nueva) {
        if (nueva == null) throw new IllegalArgumentException("Seleccione una política.");
        mutex.acquireUninterruptibly();
        try {
            politica = nueva;
        } finally {
            mutex.release();
        }
    }

    @Override
    public void ejecutarCiclo(long cicloActual) {
        String evento = null;
        mutex.acquireUninterruptibly();
        try {
            if (activa == null) {
                if (cola.estaVacia()) return;
                IPoliticaDisco.Decision decision = politica.elegir(cola, cabezal, bloques.length - 1, barridoAscendente);
                if (decision.indice() < 0) {
                    if (decision.salto()) cabezal = decision.destino();
                    else moverHacia(decision.destino());
                    return;
                }
                activa = cola.eliminarEn(decision.indice());
                objetivo = activa.inicio();
                // Mantener el sentido del barrido mientras se recorre la cadena del archivo.
                if (objetivo != cabezal) barridoAscendente = objetivo > cabezal;
                evento = "Ciclo " + cicloActual + ": disco atiende " + activa + " (" + politica + ").";
            }
            if (objetivo != cabezal) {
                moverHacia(objetivo);
            } else {
                objetivo = bloques[cabezal].siguiente();
                if (objetivo < 0) activa = null;
            }
        } finally {
            mutex.release();
        }
        if (evento != null) registrarEvento.accept(evento);
    }

    private void moverHacia(int destino) {
        int distancia = destino - cabezal;
        direccion = distancia > 0 ? Direccion.ASCENDENTE : Direccion.DESCENDENTE;
        int paso = Math.min(Math.abs(distancia), config.velocidad());
        cabezal += Integer.signum(distancia) * paso;
        desplazamiento += paso;
    }

    public Vista vista() {
        mutex.acquireUninterruptibly();
        try {
            SolicitudDisco[] pendientes = new SolicitudDisco[cola.obtenerTamano()];
            for (int i = 0; i < pendientes.length; i++) pendientes[i] = cola.obtener(i);
            return new Vista(bloques.clone(), cabezal, direccion, activa == null ? null : activa.operacion(),
                    politica, activa, pendientes, desplazamiento);
        } finally {
            mutex.release();
        }
    }
}
