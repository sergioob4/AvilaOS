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
    public record PuntoDesplazamiento(long ciclo, long desplazamiento, PoliticaDisco politica) {}
    public record SeriePolitica(PoliticaDisco politica, PuntoDesplazamiento[] puntos,
                                long desplazamientoFinal, double esperaPromedio, int atendidas) {}
    public record MetricasDisco(long desplazamientoTotal, double tiempoEsperaPromedio,
                                int totalSolicitudesAtendidas, int[] atendidasPorPolitica,
                                PuntoDesplazamiento[] serieActual, SeriePolitica[] comparativa) {}

    private final Semaphore mutex = new Semaphore(1, true);
    private ConfiguracionDisco config;
    private Bloque[] bloques;
    private int cabezal;
    private Direccion direccion = Direccion.ASCENDENTE;
    private final ListaEnlazada<SolicitudDisco> cola = new ListaEnlazada<>();
    private final ListaEnlazada<Long> ciclosLlegada = new ListaEnlazada<>();
    private final Consumer<String> registrarEvento;
    private PoliticaDisco politica;
    private SolicitudDisco activa;
    private boolean barridoAscendente = true;
    private long desplazamiento;
    private long cicloActual = 0;
    private long sumaEsperaCola = 0;
    private int solicitudesAtendidasTotal = 0;
    private final int[] atendidasPorPolitica = new int[PoliticaDisco.values().length];
    private final ListaEnlazada<PuntoDesplazamiento> historialDesplazamiento = new ListaEnlazada<>();
    private final SeriePolitica[] seriesComparativas = new SeriePolitica[PoliticaDisco.values().length];
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

    /**
     * Asigna bloques contiguos encadenados exactamente a partir de {@code startBlock}
     * (usado al cargar el estado inicial del sistema de archivos desde JSON).
     */
    public int asignarArchivoEn(String ruta, int startBlock, int cantidad) {
        if (cantidad < 1 || startBlock < 0 || startBlock > getBloquesDatosTotales() - cantidad) {
            throw new IllegalArgumentException("Rango de bloques inválido.");
        }
        int[] cadena = new int[cantidad];
        for (int i = 0; i < cantidad; i++) cadena[i] = startBlock + i;
        return asignarArchivoEn(ruta, cadena);
    }

    /** Restaura los enlaces de un archivo en el orden guardado. */
    public int asignarArchivoEn(String ruta, int[] cadena) {
        if (ruta == null || ruta.isBlank() || cadena == null || cadena.length == 0) {
            throw new IllegalArgumentException("Indique la ruta y la cadena de bloques.");
        }
        int[] direcciones = cadena.clone();
        mutex.acquireUninterruptibly();
        try {
            int limiteDatos = bloques.length - config.swap();
            for (Bloque b : bloques) {
                if (!b.swap() && ruta.equals(b.ocupante())) {
                    throw new IllegalStateException("Ya existen bloques asignados para " + ruta);
                }
            }
            boolean[] visitados = new boolean[limiteDatos];
            for (int bloque : direcciones) {
                if (bloque < 0 || bloque >= limiteDatos || visitados[bloque]) {
                    throw new IllegalArgumentException("Bloque fuera del área de datos o repetido: " + bloque);
                }
                if (bloques[bloque].ocupante() != null) {
                    throw new IllegalStateException("El bloque " + bloque + " ya está ocupado por " + bloques[bloque].ocupante());
                }
                visitados[bloque] = true;
            }
            for (int i = 0; i < direcciones.length; i++) {
                int bloque = direcciones[i];
                int siguiente = i + 1 < direcciones.length ? direcciones[i + 1] : -1;
                bloques[bloque] = new Bloque(bloque, false, ruta, siguiente);
            }
            return direcciones[0];
        } finally {
            mutex.release();
        }
    }

    /** Reconstruye por completo el disco con una nueva configuración (al cargar JSON 1). */
    public void reconfigurar(ConfiguracionDisco nuevaConfig) {
        reconfigurar(nuevaConfig, bloquesVacios(nuevaConfig));
    }

    /** Instala el mapa de un disco preparado para la importación. */
    public void restaurarDesde(DiscoSimulado preparado) {
        reconfigurar(preparado.getConfiguracion(), preparado.vista().bloques());
    }

    private static Bloque[] bloquesVacios(ConfiguracionDisco nuevaConfig) {
        if (nuevaConfig == null) throw new IllegalArgumentException("Configuración de disco nula.");
        Bloque[] nuevos = new Bloque[nuevaConfig.bloques()];
        for (int i = 0; i < nuevos.length; i++) {
            nuevos[i] = new Bloque(i, i >= nuevos.length - nuevaConfig.swap(), null, -1);
        }
        return nuevos;
    }

    private void reconfigurar(ConfiguracionDisco nuevaConfig, Bloque[] nuevos) {
        mutex.acquireUninterruptibly();
        try {
            archivarSerieActualSiTieneDatos();
            this.config = nuevaConfig;
            this.politica = nuevaConfig.politica();
            this.bloques = nuevos;
            this.cabezal = nuevaConfig.cabezal();
            this.direccion = Direccion.ASCENDENTE;
            this.barridoAscendente = true;
            this.cola.vaciar();
            this.ciclosLlegada.vaciar();
            this.activa = null;
            this.objetivo = -1;
            this.desplazamiento = 0;
            this.cicloActual = 0;
            this.sumaEsperaCola = 0;
            this.solicitudesAtendidasTotal = 0;
            this.historialDesplazamiento.vaciar();
        } finally {
            mutex.release();
        }
    }

    /**
     * Ajusta el tamaño del área de swap y la política inicial del disco (al cargar JSON 2)
     * preservando todos los bloques de datos/archivos ya cargados en 0..datos-1.
     */
    public void ajustarSwapYPolitica(int swapSizeBlocks, PoliticaDisco nuevaPolitica) {
        if (swapSizeBlocks < 0) throw new IllegalArgumentException("El tamaño de swap no puede ser negativo.");
        if (nuevaPolitica == null) throw new IllegalArgumentException("Política de disco nula.");
        mutex.acquireUninterruptibly();
        try {
            int datos = bloques.length - config.swap();
            int totalNuevo = datos + swapSizeBlocks;
            int cabezalInicial = Math.min(config.cabezal(), Math.max(0, totalNuevo - 1));
            int velocidad = Math.min(config.velocidad(), totalNuevo);
            ConfiguracionDisco nuevaConfig = new ConfiguracionDisco(totalNuevo, swapSizeBlocks, cabezalInicial, velocidad, nuevaPolitica);
            Bloque[] nuevos = new Bloque[totalNuevo];
            for (int i = 0; i < datos; i++) {
                nuevos[i] = bloques[i];
            }
            for (int i = datos; i < totalNuevo; i++) {
                nuevos[i] = new Bloque(i, true, null, -1);
            }
            archivarSerieActualSiTieneDatos();
            this.config = nuevaConfig;
            this.bloques = nuevos;
            this.politica = nuevaPolitica;
            this.cabezal = cabezalInicial;
            this.direccion = Direccion.ASCENDENTE;
            this.barridoAscendente = true;
            this.cola.vaciar();
            this.ciclosLlegada.vaciar();
            this.activa = null;
            this.objetivo = -1;
            this.desplazamiento = 0;
            this.cicloActual = 0;
            this.sumaEsperaCola = 0;
            this.solicitudesAtendidasTotal = 0;
            this.historialDesplazamiento.vaciar();
        } finally {
            mutex.release();
        }
    }

    public ConfiguracionDisco getConfiguracion() {
        mutex.acquireUninterruptibly();
        try {
            return config;
        } finally {
            mutex.release();
        }
    }

    public int getBloquesDatosTotales() {
        mutex.acquireUninterruptibly();
        try {
            return bloques.length - config.swap();
        } finally {
            mutex.release();
        }
    }

    /** Actualiza las etiquetas del mapa al renombrar un archivo o un directorio hermano. */
    public void renombrarRuta(String anterior, String nueva) {
        if (anterior.equals(nueva)) return;
        String prefijoAnterior = anterior + "/";
        String prefijoNuevo = nueva + "/";
        mutex.acquireUninterruptibly();
        try {
            for (Bloque bloque : bloques) {
                String ruta = bloque.ocupante();
                if (!bloque.swap() && ruta != null && (ruta.equals(nueva) || ruta.startsWith(prefijoNuevo))) {
                    throw new IllegalStateException("El destino ya tiene bloques asignados: " + nueva);
                }
            }
            for (Bloque bloque : bloques) {
                String ruta = bloque.ocupante();
                if (!bloque.swap() && ruta != null && (ruta.equals(anterior) || ruta.startsWith(prefijoAnterior))) {
                    String destino = nueva + ruta.substring(anterior.length());
                    bloques[bloque.numero()] = new Bloque(bloque.numero(), false, destino, bloque.siguiente());
                }
            }
            if (activa != null) activa = conOcupanteActual(activa, anterior, nueva);
            for (int i = 0; i < cola.obtenerTamano(); i++) {
                SolicitudDisco solicitud = cola.obtener(i);
                SolicitudDisco actualizada = conOcupanteActual(solicitud, anterior, nueva);
                if (actualizada != solicitud) cola.reemplazar(i, actualizada);
            }
        } finally {
            mutex.release();
        }
    }

    public int asignarSwap(String proceso, int unidadesRam) {
        return asignar(proceso, unidadesRam, true);
    }

    /** Cantidad de bloques libres actualmente en el área de intercambio (swap). */
    public int getBloquesSwapLibres() {
        mutex.acquireUninterruptibly();
        try {
            int libres = 0;
            for (Bloque bloque : bloques) {
                if (bloque.swap() && bloque.ocupante() == null) {
                    libres++;
                }
            }
            return libres;
        } finally {
            mutex.release();
        }
    }

    /** Capacidad total configurada del área de intercambio (swap) en bloques. */
    public int getBloquesSwapTotales() {
        return config.swap();
    }

    private SolicitudDisco conOcupanteActual(SolicitudDisco solicitud, String anterior, String nueva) {
        String ocupante;
        if (solicitud.metadatos()) {
            String ruta = solicitud.ocupante();
            if (!ruta.equals(anterior) && !ruta.startsWith(anterior + "/")) return solicitud;
            ocupante = nueva + ruta.substring(anterior.length());
        } else {
            ocupante = bloques[solicitud.inicio()].ocupante();
        }
        if (solicitud.ocupante().equals(ocupante)) return solicitud;
        return new SolicitudDisco(solicitud.id(), solicitud.inicio(), solicitud.operacion(), ocupante, solicitud.metadatos(), solicitud.accion());
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
            int anterior = -1;
            // Enlaza los bloques libres en orden ascendente (primero -> ... -> ultimo -> -1).
            for (int i = 0; i < bloques.length && cantidad > 0; i++) {
                Bloque bloque = bloques[i];
                if (bloque.swap() == swap && bloque.ocupante() == null) {
                    bloques[i] = new Bloque(i, swap, ocupante, -1);
                    if (primero < 0) {
                        primero = i;
                    } else {
                        Bloque prev = bloques[anterior];
                        bloques[anterior] = new Bloque(prev.numero(), prev.swap(), prev.ocupante(), i);
                    }
                    anterior = i;
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
        liberarCadenas(new int[]{inicio});
    }

    /** Valida el lote completo antes de liberar el primer bloque. */
    public void liberarCadenas(int[] inicios) {
        int[] cadenas = inicios.clone();
        mutex.acquireUninterruptibly();
        try {
            for (int inicio : cadenas) {
                validarInicio(inicio);
                if (activa != null && activa.usaCadena(inicio)) {
                    throw new IllegalStateException("Transferencia en curso.");
                }
                for (int i = 0; i < cola.obtenerTamano(); i++) {
                    if (cola.obtener(i).usaCadena(inicio)) throw new IllegalStateException("Transferencia pendiente.");
                }
            }
            for (int inicio : cadenas) {
                for (int i = inicio; i >= 0;) {
                    Bloque bloque = bloques[i];
                    bloques[i] = new Bloque(i, bloque.swap(), null, -1);
                    i = bloque.siguiente();
                }
            }
        } finally {
            mutex.release();
        }
    }

    /** El directorio carece de cadena: se simula un acceso de metadatos en la posición actual. */
    public SolicitudDisco escribirDirectorio(String ruta) {
        return escribirDirectorio(ruta, Operacion.ESCRITURA.toString());
    }

    public SolicitudDisco escribirDirectorio(String ruta, String accion) {
        mutex.acquireUninterruptibly();
        try {
            var solicitud = new SolicitudDisco(siguienteId++, cabezal, Operacion.ESCRITURA, ruta, true, accion);
            cola.agregar(solicitud);
            ciclosLlegada.agregar(cicloActual);
            return solicitud;
        } finally {
            mutex.release();
        }
    }

    public SolicitudDisco iniciarTransferencia(int inicio, Operacion tipo) {
        if (tipo == null) throw new IllegalArgumentException("Indique una operación.");
        return iniciarTransferencia(inicio, tipo, tipo.toString());
    }

    public SolicitudDisco iniciarTransferencia(int inicio, Operacion tipo, String accion) {
        mutex.acquireUninterruptibly();
        try {
            validarInicio(inicio);
            if (tipo == null) throw new IllegalArgumentException("Indique una operación.");
            SolicitudDisco solicitud = new SolicitudDisco(siguienteId++, inicio, tipo, bloques[inicio].ocupante(), false, accion);
            cola.agregar(solicitud);
            ciclosLlegada.agregar(cicloActual);
            return solicitud;
        } finally {
            mutex.release();
        }
    }

    /** Retira una transferencia cancelada sin alterar el recorrido ya realizado. */
    public void cancelarTransferencia(long id) {
        mutex.acquireUninterruptibly();
        try {
            if (activa != null && activa.id() == id) {
                activa = null;
                objetivo = -1;
                return;
            }
            for (int i = 0; i < cola.obtenerTamano(); i++) {
                if (cola.obtener(i).id() == id) {
                    cola.eliminarEn(i);
                    if (i < ciclosLlegada.obtenerTamano()) {
                        ciclosLlegada.eliminarEn(i);
                    }
                    return;
                }
            }
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
        avanzarTransferencia(cicloActual);
    }

    /** Devuelve la transferencia terminada en este pulso, si la hubo. */
    public SolicitudDisco avanzarTransferencia(long cicloActual) {
        String evento = null;
        SolicitudDisco terminada = null;
        mutex.acquireUninterruptibly();
        try {
            this.cicloActual = cicloActual;
            if (activa == null) {
                if (cola.estaVacia()) {
                    registrarPuntoDesplazamiento(cicloActual);
                    return null;
                }
                int limiteSuperior = limiteBarridoActual();
                IPoliticaDisco.Decision decision = politica.elegir(cola, cabezal, limiteSuperior, barridoAscendente);
                if (decision.indice() < 0) {
                    if (decision.salto()) cabezal = decision.destino();
                    else moverHacia(decision.destino());
                    registrarPuntoDesplazamiento(cicloActual);
                    return null;
                }
                activa = cola.eliminarEn(decision.indice());
                long llegada = decision.indice() < ciclosLlegada.obtenerTamano()
                        ? ciclosLlegada.eliminarEn(decision.indice()) : cicloActual;
                sumaEsperaCola += Math.max(0L, cicloActual - llegada);
                solicitudesAtendidasTotal++;
                atendidasPorPolitica[politica.ordinal()]++;
                objetivo = activa.inicio();
                // Mantener el sentido del barrido mientras se recorre la cadena del archivo.
                if (objetivo != cabezal) barridoAscendente = objetivo > cabezal;
                evento = "Ciclo " + cicloActual + ": disco atiende " + activa + " (" + politica + ").";
            }
            if (objetivo != cabezal) {
                moverHacia(objetivo);
            } else {
                objetivo = activa.metadatos() ? -1 : bloques[cabezal].siguiente();
                if (objetivo < 0) {
                    terminada = activa;
                    activa = null;
                }
            }
            registrarPuntoDesplazamiento(cicloActual);
        } finally {
            mutex.release();
        }
        if (evento != null) registrarEvento.accept(evento);
        return terminada;
    }

    private void registrarPuntoDesplazamiento(long ciclo) {
        if (historialDesplazamiento.obtenerTamano() >= 2000) {
            historialDesplazamiento.eliminarEn(0);
        }
        historialDesplazamiento.agregar(new PuntoDesplazamiento(ciclo, desplazamiento, politica));
    }

    private void archivarSerieActualSiTieneDatos() {
        int n = historialDesplazamiento.obtenerTamano();
        if (n == 0 || (desplazamiento == 0 && solicitudesAtendidasTotal == 0)) {
            return;
        }
        PuntoDesplazamiento[] puntos = new PuntoDesplazamiento[n];
        for (int i = 0; i < n; i++) {
            puntos[i] = historialDesplazamiento.obtener(i);
        }
        double esperaProm = solicitudesAtendidasTotal > 0
                ? (double) sumaEsperaCola / solicitudesAtendidasTotal : 0.0;
        seriesComparativas[politica.ordinal()] = new SeriePolitica(
                politica, puntos, desplazamiento, esperaProm, solicitudesAtendidasTotal);
    }

    /** Limpia las series comparativas archivadas para iniciar una nueva comparación entre políticas. */
    public void limpiarComparativa() {
        mutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < seriesComparativas.length; i++) {
                seriesComparativas[i] = null;
            }
        } finally {
            mutex.release();
        }
    }

    /** Devuelve una instantánea thread-safe de todas las métricas del disco y series para graficar. */
    public MetricasDisco getMetricas() {
        mutex.acquireUninterruptibly();
        try {
            double esperaProm = solicitudesAtendidasTotal > 0
                    ? (double) sumaEsperaCola / solicitudesAtendidasTotal : 0.0;
            int n = historialDesplazamiento.obtenerTamano();
            PuntoDesplazamiento[] serieActual = new PuntoDesplazamiento[n];
            for (int i = 0; i < n; i++) {
                serieActual[i] = historialDesplazamiento.obtener(i);
            }
            int cantComp = 0;
            for (SeriePolitica sp : seriesComparativas) {
                if (sp != null) cantComp++;
            }
            SeriePolitica[] comp = new SeriePolitica[cantComp];
            int idx = 0;
            for (SeriePolitica sp : seriesComparativas) {
                if (sp != null) comp[idx++] = sp;
            }
            return new MetricasDisco(desplazamiento, esperaProm, solicitudesAtendidasTotal,
                    atendidasPorPolitica.clone(), serieActual, comp);
        } finally {
            mutex.release();
        }
    }

    private int limiteBarridoActual() {
        int limiteDatos = Math.max(0, bloques.length - config.swap() - 1);
        for (int i = 0; i < cola.obtenerTamano(); i++) {
            if (cola.obtener(i).inicio() > limiteDatos) {
                return bloques.length - 1;
            }
        }
        return cabezal > limiteDatos ? bloques.length - 1 : limiteDatos;
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
