package avilaos.interfaces;

import avilaos.hardware.SolicitudDisco;
import avilaos.estructuras.ListaEnlazada;

/** Decide la próxima atención o movimiento entre solicitudes, con la cola en orden de llegada. */
public interface IPoliticaDisco {
    record Decision(int indice, int destino, boolean salto) {
        public static Decision atender(int indice) { return new Decision(indice, -1, false); }
        public static Decision desplazar(int destino, boolean salto) { return new Decision(-1, destino, salto); }
    }
    Decision elegir(ListaEnlazada<SolicitudDisco> cola, int cabezal, int ultimo, boolean ascendente);
}
