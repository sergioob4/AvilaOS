package avilaos.enums;

import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.SolicitudDisco;
import avilaos.interfaces.IPoliticaDisco;

public enum PoliticaDisco implements IPoliticaDisco {
    FIFO {
        public Decision elegir(ListaEnlazada<SolicitudDisco> cola, int cabeza, int ultimo, boolean sube) {
            return Decision.atender(0);
        }
    },
    SSTF {
        public Decision elegir(ListaEnlazada<SolicitudDisco> cola, int cabeza, int ultimo, boolean sube) {
            return Decision.atender(masCercana(cola, cabeza, 0));
        }
    },
    SCAN {
        public Decision elegir(ListaEnlazada<SolicitudDisco> cola, int cabeza, int ultimo, boolean sube) {
            return barrer(cola, cabeza, ultimo, sube, false);
        }
    },
    C_SCAN {
        public Decision elegir(ListaEnlazada<SolicitudDisco> cola, int cabeza, int ultimo, boolean sube) {
            return barrer(cola, cabeza, ultimo, sube, true);
        }
        @Override public String toString() { return "C-SCAN"; }
    };

    private static Decision barrer(ListaEnlazada<SolicitudDisco> cola, int cabeza, int ultimo,
                                   boolean sube, boolean circular) {
        int indice = masCercana(cola, cabeza, sube ? 1 : -1);
        if (indice >= 0) return Decision.atender(indice);
        int extremo = sube ? ultimo : 0;
        if (cabeza != extremo) return Decision.desplazar(extremo, false);
        if (circular) return Decision.desplazar(sube ? 0 : ultimo, true);
        return Decision.atender(masCercana(cola, cabeza, 0));
    }

    // sentido cero considera ambos lados. Una distancia igual conserva el orden de llegada.
    private static int masCercana(ListaEnlazada<SolicitudDisco> cola, int cabeza, int sentido) {
        int indice = -1;
        int distancia = Integer.MAX_VALUE;
        int total = cola.obtenerTamano();
        for (int i = 0; i < total; i++) {
            int delta = cola.obtener(i).inicio() - cabeza;
            if (delta * sentido >= 0 && Math.abs(delta) < distancia) {
                indice = i;
                distancia = Math.abs(delta);
            }
        }
        return indice;
    }
}
