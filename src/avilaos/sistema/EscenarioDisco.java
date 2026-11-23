package avilaos.sistema;

import avilaos.hardware.DiscoSimulado;
import avilaos.enums.ModoUsuario;
import avilaos.enums.Permiso;
import avilaos.sistemaarchivos.SistemaArchivos;

/** Datos iniciales para inspeccionar el hardware desde la carga de prueba. */
public final class EscenarioDisco {
    private EscenarioDisco() {}

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
        var ejemplo = archivos.crearArchivo("/", "ejemplo.txt", SistemaArchivos.ADMINISTRADOR,
                Permiso.PUBLICO, cantidad, null, ModoUsuario.ADMINISTRADOR);
        disco.iniciarTransferencia(ejemplo.getPrimerBloque(), DiscoSimulado.Operacion.LECTURA);
        if (swap > 0) {
            int proceso = disco.asignarSwap("P-demo", Math.min(3, swap));
            disco.iniciarTransferencia(proceso, DiscoSimulado.Operacion.ESCRITURA);
        }
        if (datos > cantidad) {
            var notas = archivos.crearArchivo("/", "notas.txt", SistemaArchivos.ADMINISTRADOR,
                    Permiso.PRIVADO, Math.min(3, datos - cantidad), null, ModoUsuario.ADMINISTRADOR);
            disco.iniciarTransferencia(notas.getPrimerBloque(), DiscoSimulado.Operacion.LECTURA);
        }
        return true;
    }
}
