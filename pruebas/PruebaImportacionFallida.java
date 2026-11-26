import avilaos.gui.VentanaPrincipal;
import avilaos.sistema.ConfiguracionInicial;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.swing.SwingUtilities;

class PruebaImportacionFallida {
    @FunctionalInterface
    private interface Importacion {
        void ejecutar() throws Exception;
    }

    private static void exigir(boolean cumple, String mensaje) {
        if (!cumple) throw new AssertionError(mensaje);
    }

    private static void rechazar(Importacion importacion) throws Exception {
        try {
            importacion.ejecutar();
            throw new AssertionError("La importación debía fallar");
        } catch (IOException esperada) {
            // El llamador comprueba el estado tras el rechazo.
        }
    }

    private static void comprobarImportaciones(Path disco, Path procesos, Path reemplazo) throws Exception {
        var cluster = ConfiguracionInicial.porDefecto().crearCluster();
        var comp = cluster.getComputadores().obtener(0);
        var reloj = cluster.getRelojGlobal();
        var ventana = new VentanaPrincipal(cluster);
        try {
            String procesosValidos = Files.readString(procesos);
            ventana.cargarSistemaArchivosJsonDesdeArchivo(disco.toFile());
            ventana.cargarProcesosConfigJsonDesdeArchivo(procesos.toFile());

            synchronized (reloj) {
                for (int i = 0; i < 6; i++) reloj.avanzarUnCiclo();
                var vista = comp.getDisco().vista();
                exigir(vista.activa() != null, "Se esperaba una solicitud en curso");
                var activo = comp.getSo().getColaBloqueados().obtener(0);
                long ciclo = reloj.getCicloActual();
                long duracion = reloj.getDuracionCicloMs();
                int ram = comp.getMemoria().getMemoriaUsada();

                String json = Files.readString(disco);
                Files.writeString(reemplazo, json.replace("\"owner\": \"user2\"", "\"owner\": 2"));
                rechazar(() -> ventana.cargarSistemaArchivosJsonDesdeArchivo(reemplazo.toFile()));
                exigir(!reloj.estaEnPausa(), "El rechazo pausó el reloj");

                reloj.pausar();
                Files.writeString(reemplazo, procesosValidos.replace("\"deadline\": 500", "\"deadline\": 0"));
                rechazar(() -> ventana.cargarProcesosConfigJsonDesdeArchivo(reemplazo.toFile()));
                exigir(reloj.estaEnPausa(), "El rechazo reanudó el reloj");

                // El JSON de procesos seleccionado puede cambiar antes de la recarga conjunta.
                Files.writeString(procesos, "{");
                Files.writeString(reemplazo, """
                        {"diskConfig":{"totalBlocks":10,"initialHead":0},"fileSystem":[]}
                        """);
                rechazar(() -> ventana.cargarSistemaArchivosJsonDesdeArchivo(reemplazo.toFile()));

                var despues = comp.getDisco().vista();
                exigir(ventana.estaListaParaIniciar() && reloj.estaEnPausa()
                        && reloj.getCicloActual() == ciclo && reloj.getDuracionCicloMs() == duracion,
                        "Se alteró el reloj o el estado de carga");
                exigir(Arrays.equals(vista.bloques(), despues.bloques())
                        && Arrays.equals(vista.pendientes(), despues.pendientes())
                        && vista.activa().equals(despues.activa()) && vista.cabezal() == despues.cabezal(),
                        "Se alteró el disco o su cola");
                exigir(comp.getSo().getColaBloqueados().obtener(0) == activo
                        && comp.getMemoria().getMemoriaUsada() == ram
                        && comp.getSistemaArchivos().buscar("/sistema/boot.bin") != null,
                        "Se alteraron procesos, RAM o directorios");

                Files.writeString(procesos, procesosValidos);
                ventana.cargarSistemaArchivosJsonDesdeArchivo(disco.toFile());
                exigir(ventana.estaListaParaIniciar() && !reloj.estaEnPausa()
                        && reloj.getCicloActual() == 0, "La recarga válida posterior falló");
            }
        } finally {
            reloj.detener();
            ventana.dispose();
        }
    }

    public static void main(String[] args) throws Exception {
        Path disco = Path.of("ejemplos/sistema_archivos.json");
        Path procesos = Files.createTempFile("avilaos-procesos-", ".json");
        Path reemplazo = Files.createTempFile("avilaos-importacion-", ".json");
        try {
            Files.writeString(procesos, Files.readString(Path.of("ejemplos/procesos_config.json")));
            SwingUtilities.invokeAndWait(() -> {
                try {
                    comprobarImportaciones(disco, procesos, reemplazo);
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            });
        } finally {
            Files.deleteIfExists(procesos);
            Files.deleteIfExists(reemplazo);
        }
        System.out.println("OK: el rechazo conserva la simulación y permite reintentar la carga.");
    }
}
