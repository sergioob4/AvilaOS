import avilaos.enums.ModoUsuario;
import avilaos.enums.Permiso;
import avilaos.persistencia.GestorPersistencia;
import avilaos.sistema.ConfiguracionInicial;
import avilaos.sistemaarchivos.Archivo;
import avilaos.sistemaarchivos.SistemaArchivos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class PruebaPersistenciaFragmentacion {
    private static void crear(SistemaArchivos fs, String nombre, int cantidad) {
        fs.crearArchivo("/", nombre, "admin", Permiso.PUBLICO, cantidad,
                "P1", "admin", ModoUsuario.ADMINISTRADOR);
    }

    private static void exigir(boolean cumple, String detalle) {
        if (!cumple) throw new AssertionError(detalle);
    }

    public static void main(String[] args) throws IOException {
        var origen = ConfiguracionInicial.porDefecto().crearCluster().getComputadores().obtener(0);
        var fs = origen.getSistemaArchivos();
        crear(fs, "a", 1);
        crear(fs, "b", 1);
        crear(fs, "c", 1);
        fs.eliminarArchivo("/a", ModoUsuario.ADMINISTRADOR);
        crear(fs, "fragmentado", 2);

        Path json = Files.createTempFile("avilaos-fragmentacion-", ".json");
        try {
            GestorPersistencia.exportarSistemaArchivosJson(json.toFile(), origen);
            String guardado = Files.readString(json);
            exigir(guardado.contains("\"blockChain\": [0, 3]"), "No se guardó la cadena fragmentada");

            var destino = ConfiguracionInicial.porDefecto().crearCluster().getComputadores().obtener(0);
            GestorPersistencia.importarSistemaArchivosJson(json.toFile(), destino);
            var archivo = (Archivo) destino.getSistemaArchivos().buscar("/fragmentado");
            exigir(archivo.getPrimerBloque() == 0 && archivo.getTamanoBloques() == 2, "Metadatos incorrectos");
            var bloques = destino.getDisco().vista().bloques();
            exigir(bloques[0].siguiente() == 3 && bloques[3].siguiente() == -1
                    && "/fragmentado".equals(bloques[3].ocupante())
                    && "/b".equals(bloques[1].ocupante()), "Cadena restaurada incorrectamente");

            for (String invalida : new String[]{"[0, 1]", "[0, 0]", "[0, -1]", "[3, 0]", "[0]", "[0, \"3\"]"}) {
                Files.writeString(json, guardado.replace("[0, 3]", invalida));
                try {
                    GestorPersistencia.importarSistemaArchivosJson(json.toFile(), destino);
                    throw new AssertionError("Se aceptó una cadena inválida: " + invalida);
                } catch (IOException esperada) {
                    exigir(destino.getDisco().vista().bloques()[0].siguiente() == 3
                            && destino.getSistemaArchivos().buscar("/fragmentado") != null,
                            "La cadena inválida alteró el sistema cargado");
                }
            }

            Files.writeString(json, guardado.replace("      \"blockChain\": [0, 3],\n", "")
                    .replace("\"startBlock\": 0", "\"startBlock\": 3"));
            GestorPersistencia.importarSistemaArchivosJson(json.toFile(), destino);
            bloques = destino.getDisco().vista().bloques();
            exigir(bloques[3].siguiente() == 4 && "/fragmentado".equals(bloques[4].ocupante()),
                    "El formato anterior debe reconstruir una cadena contigua");
        } finally {
            Files.deleteIfExists(json);
        }
        System.out.println("OK: persistencia fragmentada, rechazo de cadenas inválidas y formato anterior.");
    }
}
