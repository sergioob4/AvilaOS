import avilaos.enums.PoliticaDisco;
import avilaos.hardware.ConfiguracionDisco;
import avilaos.hardware.DiscoSimulado;
import avilaos.hardware.SolicitudDisco;
import avilaos.persistencia.GestorPersistencia;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static avilaos.hardware.DiscoSimulado.Operacion.LECTURA;

/** Ejecutar: java -cp dist/AvilaOS.jar pruebas/PruebaPlanificacionDisco.java */
class PruebaPlanificacionDisco {
    static DiscoSimulado preparar(PoliticaDisco politica, int... destinos) {
        var disco = new DiscoSimulado(new ConfiguracionDisco(200, 0, 50, 10, politica));
        for (int i = 0; i < 200; i++) disco.asignarArchivo("/b" + i, 1);
        for (int destino : destinos) disco.iniciarTransferencia(destino, LECTURA);
        return disco;
    }

    static void exigir(boolean cumple, String motivo) {
        if (!cumple) throw new AssertionError(motivo);
    }

    static void verificar(PoliticaDisco politica, int[] destinos, int[] esperado, long recorrido) {
        var disco = preparar(politica, destinos);
        int atendidas = 0;
        for (int ciclo = 1; ciclo < 1000; ciclo++) {
            var antes = disco.vista();
            disco.ejecutarCiclo(ciclo);
            var despues = disco.vista();
            if (antes.pendientes().length > despues.pendientes().length) {
                SolicitudDisco elegida = despues.activa();
                int bloque = elegida == null ? despues.cabezal() : elegida.inicio();
                exigir(bloque == esperado[atendidas++], "Orden incorrecto en " + politica);
            }
            if (despues.activa() == null && despues.pendientes().length == 0) break;
        }
        exigir(atendidas == destinos.length, "Cola incompleta");
        exigir(disco.vista().desplazamiento() == recorrido, "Recorrido incorrecto en " + politica);
    }

    static void verificarEjemplosJson() throws IOException {
        var resultados = GestorPersistencia.compararPlanificacionJson(
                new File("ejemplos/sistema_archivos.json"), new File("ejemplos/procesos_config.json"));
        exigir(resultados.length == 4, "Faltan políticas en la comparación");
        for (var resultado : resultados) {
            int[] esperado = switch (resultado.politica()) {
                case FIFO -> new int[]{95,180,34,119,11,123,62,64};
                case SSTF -> new int[]{62,64,34,11,95,119,123,180};
                case SCAN -> new int[]{62,64,95,119,123,180,34,11};
                case C_SCAN -> new int[]{62,64,95,119,123,180,11,34};
            };
            long recorrido = switch (resultado.politica()) {
                case FIFO -> 644;
                case SSTF -> 236;
                case SCAN -> 337;
                case C_SCAN -> 183;
            };
            exigir(Arrays.equals(esperado, resultado.orden()), "Orden incorrecto con JSON en " + resultado.politica());
            exigir(resultado.desplazamiento() == recorrido, "Recorrido incorrecto con JSON en " + resultado.politica());
            exigir(resultado.esperaPromedio() > 0, "Falta la espera media en " + resultado.politica());
        }
    }

    public static void main(String[] args) throws IOException {
        int[] anexo = {82,17,43,140,24,16,190};
        verificar(PoliticaDisco.FIFO, anexo, anexo, 518);
        verificar(PoliticaDisco.SSTF, anexo, new int[]{43,24,17,16,82,140,190}, 208);
        verificar(PoliticaDisco.SCAN, anexo, new int[]{82,140,190,43,24,17,16}, 332);
        verificar(PoliticaDisco.C_SCAN, anexo, new int[]{82,140,190,16,17,24,43}, 192);
        int[] casos = {95,180,34,119,11,123,62,64};
        verificar(PoliticaDisco.FIFO, casos, casos, 644);
        verificar(PoliticaDisco.SSTF, casos, new int[]{62,64,34,11,95,119,123,180}, 236);
        verificar(PoliticaDisco.SCAN, casos, new int[]{62,64,95,119,123,180,34,11}, 337);
        verificar(PoliticaDisco.C_SCAN, casos, new int[]{62,64,95,119,123,180,11,34}, 183);

        var disco = preparar(PoliticaDisco.FIFO, 82, 10, 85);
        disco.ejecutarCiclo(1);
        var activa = disco.vista().activa();
        disco.cambiarPolitica(PoliticaDisco.SSTF);
        disco.iniciarTransferencia(84, LECTURA);
        try {
            disco.liberar(84);
            throw new AssertionError("Se liberó una cadena pendiente");
        } catch (IllegalStateException esperada) { }
        for (int ciclo = 2; ciclo <= 4; ciclo++) {
            disco.ejecutarCiclo(ciclo);
            exigir(activa.equals(disco.vista().activa()), "Se interrumpió la transferencia activa");
        }
        disco.ejecutarCiclo(5);
        disco.ejecutarCiclo(6);
        exigir(disco.vista().activa().inicio() == 84, "No se aplicó SSTF al siguiente despacho");
        verificarEjemplosJson();
        System.out.println("OK: cuatro políticas, casos del enunciado desde los JSON y cambio de política en curso.");
    }
}
