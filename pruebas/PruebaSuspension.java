import avilaos.persistencia.GestorPersistencia;
import avilaos.sistema.ConfiguracionInicial;
import avilaos.sistema.ClusterDistribuido;
import java.io.File;
import java.nio.file.Files;

/** Verifica el escenario de presión de RAM y la persistencia del cabezal físico. */
class PruebaSuspension {
    private static void exigir(boolean cumple, String mensaje) {
        if (!cumple) throw new AssertionError(mensaje);
    }

    public static void main(String[] args) throws Exception {
        var cluster = ConfiguracionInicial.porDefecto().crearCluster();
        var comp = cluster.buscarPorId("C1");
        var fs = new File("ejemplos/sistema_archivos.json");
        var procesos = new File("ejemplos/procesos_swap.json");
        GestorPersistencia.importarSistemaArchivosJson(fs, procesos, cluster);
        var disco = comp.getDisco();
        var so = comp.getSo();
        boolean guardadoEnSwap = false;
        var json = Files.createTempFile("avilaos-cabezal-", ".json");
        try {
            for (int i = 0; i < 3000 && so.getColaTerminados().obtenerTamano() < 2; i++) {
                cluster.getRelojGlobal().avanzarUnCiclo();
                int cabezal = disco.vista().cabezal();
                if (!guardadoEnSwap && cabezal >= disco.getBloquesDatosTotales()) {
                    GestorPersistencia.exportarSistemaArchivosJson(json.toFile(), comp);
                    var copia = ConfiguracionInicial.porDefecto().crearCluster().buscarPorId("C1");
                    GestorPersistencia.importarSistemaArchivosJson(json.toFile(), copia);
                    exigir(copia.getDisco().vista().cabezal() == cabezal, "Cambió el cabezal dentro de swap");
                    exigir(copia.getDisco().vista().bloques().length == disco.vista().bloques().length,
                            "Cambió la capacidad física");
                    guardadoEnSwap = true;
                }
            }
            exigir(guardadoEnSwap && so.getTotalSuspensiones() > 0, "Falta la suspensión");
            exigir(so.getTotalReactivaciones() == so.getTotalSuspensiones(), "Falta la reactivación");
            exigir(so.getProcesosCompletadosExitosos() == 2, "No terminaron ambos procesos");
            exigir(comp.getMemoria().getMemoriaUsada() == 0 && disco.getBloquesSwapLibres() == 100,
                    "Quedó memoria reservada");
            exigir(comp.getSistemaArchivos().buscar("/prueba-swap").getTamanoBloques() == 3,
                    "No se respetó fileSizeBlocks");
            String sinSwap = Files.readString(procesos.toPath()).replace("\"swapSizeBlocks\": 100", "\"swapSizeBlocks\": 0");
            Files.writeString(json, sinSwap);
            GestorPersistencia.importarSistemaArchivosJson(fs, json.toFile(), cluster);
            for (int i = 0; i < 3000 && so.getColaTerminados().obtenerTamano() < 2; i++) {
                cluster.getRelojGlobal().avanzarUnCiclo();
            }
            exigir(so.getTotalSuspensiones() == 0 && so.getProcesosCompletadosExitosos() == 2,
                    "Sin swap el nuevo proceso debe esperar por la RAM");
        } finally {
            Files.deleteIfExists(json);
        }
        try {
            new ClusterDistribuido(2, 16);
            throw new AssertionError("Se admitió un segundo computador");
        } catch (IllegalArgumentException esperado) { }
        System.out.println("OK: suspensión, reactivación, tamaño importado y cabezal en swap");
    }
}
