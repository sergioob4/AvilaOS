package avilaos.sistema;

import avilaos.hardware.DiscoSimulado;

/** Datos iniciales para inspeccionar el hardware desde la carga de prueba. */
public final class EscenarioDisco {
    private EscenarioDisco() {}

    public static boolean cargar(DiscoSimulado disco) {
        int datos = 0;
        int swap = 0;
        for (DiscoSimulado.Bloque bloque : disco.vista().bloques()) {
            if (bloque.ocupante() != null) return false;
            if (bloque.swap()) swap++;
            else datos++;
        }
        int cantidad = Math.min(4, datos);
        int inicio = disco.asignarArchivo("/ejemplo.txt", cantidad);
        disco.iniciarTransferencia(inicio, DiscoSimulado.Operacion.LECTURA);
        if (swap > 0) {
            int proceso = disco.asignarSwap("P-demo", Math.min(3, swap));
            disco.iniciarTransferencia(proceso, DiscoSimulado.Operacion.ESCRITURA);
        }
        if (datos > cantidad) {
            int notas = disco.asignarArchivo("/notas.txt", Math.min(3, datos - cantidad));
            disco.iniciarTransferencia(notas, DiscoSimulado.Operacion.LECTURA);
        }
        return true;
    }
}
