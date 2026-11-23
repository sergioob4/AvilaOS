package avilaos.hardware;

import avilaos.interfaces.IComponenteReloj;
import java.util.concurrent.Semaphore;

/** Disco local con asignación encadenada. Las direcciones de swap ocupan el extremo superior. */
public final class DiscoSimulado implements IComponenteReloj {
    public enum Operacion { LECTURA, ESCRITURA }
    public enum Direccion { ASCENDENTE, DESCENDENTE }
    public record Bloque(int numero, boolean swap, String ocupante, int siguiente) {}
    public record Vista(Bloque[] bloques, int cabezal, Direccion direccion, Operacion operacion) {}

    private final Semaphore mutex = new Semaphore(1, true);
    private final ConfiguracionDisco config;
    private final Bloque[] bloques;
    private int cabezal;
    private Direccion direccion = Direccion.ASCENDENTE;
    private Operacion operacion;
    private int objetivo = -1;

    public DiscoSimulado(ConfiguracionDisco config) {
        this.config = config;
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
            Bloque primero = bloques[inicio];
            if (objetivo >= 0 && bloques[objetivo].swap() == primero.swap()
                    && bloques[objetivo].ocupante().equals(primero.ocupante())) {
                throw new IllegalStateException("Transferencia en curso.");
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

    public void iniciarTransferencia(int inicio, Operacion tipo) {
        mutex.acquireUninterruptibly();
        try {
            validarInicio(inicio);
            if (tipo == null) throw new IllegalArgumentException("Indique una operación.");
            if (operacion != null) throw new IllegalStateException("Disco ocupado.");
            objetivo = inicio;
            operacion = tipo;
        } finally {
            mutex.release();
        }
    }

    @Override
    public void ejecutarCiclo(long cicloActual) {
        mutex.acquireUninterruptibly();
        try {
            if (objetivo < 0) return;
            int distancia = objetivo - cabezal;
            if (distancia != 0) {
                direccion = distancia > 0 ? Direccion.ASCENDENTE : Direccion.DESCENDENTE;
                cabezal += Integer.signum(distancia) * Math.min(Math.abs(distancia), config.velocidad());
            } else {
                objetivo = bloques[cabezal].siguiente();
                if (objetivo < 0) operacion = null;
            }
        } finally {
            mutex.release();
        }
    }

    public Vista vista() {
        mutex.acquireUninterruptibly();
        try {
            return new Vista(bloques.clone(), cabezal, direccion, operacion);
        } finally {
            mutex.release();
        }
    }
}
