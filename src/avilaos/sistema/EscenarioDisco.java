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
        if (datos > cantidad) disco.asignarArchivo("/notas.txt", Math.min(3, datos - cantidad));
        if (swap > 0) disco.asignarSwap("P-demo", Math.min(3, swap));
        disco.iniciarTransferencia(inicio, DiscoSimulado.Operacion.LECTURA);
        return true;
    }
}
