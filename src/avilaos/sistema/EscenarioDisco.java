package avilaos.sistema;

import avilaos.hardware.DiscoSimulado;
import avilaos.hardware.ConfiguracionDisco;
import avilaos.enums.ModoUsuario;
import avilaos.enums.Permiso;
import avilaos.enums.PoliticaDisco;
import avilaos.sistemaarchivos.SistemaArchivos;

/** Datos iniciales para inspeccionar el hardware desde la carga de prueba. */
public final class EscenarioDisco {
    private EscenarioDisco() {}

    public record Acceso(int bloque, DiscoSimulado.Operacion operacion) {}

    public record Resultado(PoliticaDisco politica, int[] orden, long desplazamiento, double esperaPromedio) {}

    /** Compara las políticas con la misma cola inicial de accesos a un solo bloque. */
    public static Resultado[] comparar(int bloques, int cabezalInicial, Acceso[] accesos) {
        PoliticaDisco[] politicas = PoliticaDisco.values();
        Resultado[] resultados = new Resultado[politicas.length];
        for (int i = 0; i < politicas.length; i++) {
            PoliticaDisco politica = politicas[i];
            var disco = new DiscoSimulado(new ConfiguracionDisco(bloques, 0, cabezalInicial, 1, politica));
            boolean[] asignados = new boolean[bloques];
            for (Acceso acceso : accesos) {
                int bloque = acceso.bloque();
                if (!asignados[bloque]) {
                    disco.asignarArchivoEn("/bloque-" + bloque, bloque, 1);
                    asignados[bloque] = true;
                }
                disco.iniciarTransferencia(bloque, acceso.operacion());
            }

            int[] orden = new int[accesos.length];
            int atendidas = 0;
            for (long ciclo = 1; atendidas < orden.length; ciclo++) {
                var terminada = disco.avanzarTransferencia(ciclo);
                if (terminada != null) orden[atendidas++] = terminada.inicio();
            }
            var metricas = disco.getMetricas();
            resultados[i] = new Resultado(politica, orden, metricas.desplazamientoTotal(),
                    metricas.tiempoEsperaPromedio());
        }
        return resultados;
    }

    public static boolean cargar(Computador computador) {
        DiscoSimulado disco = computador.getDisco();
        SistemaArchivos archivos = computador.getSistemaArchivos();
        int datos = 0;
        int swap = 0;
        for (DiscoSimulado.Bloque bloque : disco.vista().bloques()) {
            if (bloque.ocupante() != null) return false;
            if (bloque.swap()) swap++;
            else datos++;
        }
        int cantidad = Math.min(4, datos);
        if (archivos.buscar("/ejemplo.txt") != null
                || (datos > cantidad && archivos.buscar("/notas.txt") != null)) return false;
        String admin = SistemaArchivos.ADMINISTRADOR;
        archivos.crearArchivo("/", "ejemplo.txt", admin, Permiso.PUBLICO, cantidad, null, admin, ModoUsuario.ADMINISTRADOR);
        archivos.solicitarLectura("/ejemplo.txt", admin, ModoUsuario.ADMINISTRADOR);
        if (swap > 0) {
            int proceso = disco.asignarSwap("P-demo", Math.min(3, swap));
            disco.iniciarTransferencia(proceso, DiscoSimulado.Operacion.ESCRITURA);
        }
        if (datos > cantidad) {
            archivos.crearArchivo("/", "notas.txt", admin, Permiso.PRIVADO, Math.min(3, datos - cantidad),
                    null, admin, ModoUsuario.ADMINISTRADOR);
            archivos.solicitarLectura("/notas.txt", admin, ModoUsuario.ADMINISTRADOR);
        }
        return true;
    }
}
