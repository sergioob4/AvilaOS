import avilaos.enums.*;
import avilaos.proceso.PeticionArchivo;
import avilaos.sistema.ConfiguracionInicial;
import avilaos.sistemaarchivos.Directorio;

/** Ejecutar con java -cp dist/AvilaOS.jar pruebas/PruebaDirectorioES.java */
class PruebaDirectorioES {
    static PeticionArchivo crear(String ruta, ModoUsuario modo) {
        return new PeticionArchivo(OperacionArchivo.CREAR_DIRECTORIO, ruta, "ana",
                modo, 0, Permiso.PRIVADO, null);
    }

    static void exigir(boolean cumple, String detalle) {
        if (!cumple) throw new AssertionError(detalle);
    }

    public static void main(String[] args) {
        var cluster = ConfiguracionInicial.porDefecto().crearCluster();
        var comp = cluster.getComputadores().obtener(0);
        var fs = comp.getSistemaArchivos();
        var so = comp.getSo();
        var reloj = cluster.getRelojGlobal();
        var admin = ModoUsuario.ADMINISTRADOR;
        var peticion = crear("/informes", admin);
        var proceso = so.crearProcesoArchivo(peticion, 0);
        boolean pasoPorDisco = false;
        for (int i = 0; i < 30 && !proceso.estaTerminado(); i++) {
            reloj.avanzarUnCiclo();
            if (proceso.getMotivoBloqueo() == MotivoBloqueo.DISCO) {
                exigir(fs.buscar("/informes") == null, "El árbol se modificó antes de completar la E/S");
                for (var solicitud : comp.getDisco().vista().pendientes()) {
                    if (solicitud.id() == proceso.getPcb().getIdSolicitudDisco() && solicitud.metadatos()) {
                        exigir(solicitud.toString().contains("Crear directorio /informes"),
                                "La cola debe mostrar la operación solicitada y su ruta");
                        pasoPorDisco = true;
                    }
                }
            }
        }
        exigir(pasoPorDisco && proceso.estaTerminado(), "Falta el recorrido CPU/disco");
        var nodo = fs.buscar("/informes");
        exigir(nodo instanceof Directorio && nodo.getPermiso() == Permiso.PRIVADO
                && nodo.getDueno().equals("ana") && nodo.getTamanoBloques() == 0, "Metadatos incorrectos");

        var duplicada = so.crearProcesoArchivo(peticion, reloj.getCicloActual());
        for (int i = 0; i < 30 && !duplicada.estaTerminado(); i++) reloj.avanzarUnCiclo();
        exigir(duplicada.getPcb().getErrorArchivo() != null, "Se aceptó un duplicado");
        var cancelada = fs.solicitarOperacion(crear("/cancelada", admin), "P");
        fs.cancelarOperacion(cancelada.id());
        fs.completarOperacion(cancelada.id());
        exigir(fs.buscar("/cancelada") == null, "La cancelación publicó el directorio");
        var usuario = crear("/informes/privado", ModoUsuario.USUARIO);
        try {
            fs.validarPermiso(usuario);
            throw new AssertionError("Usuario autorizado en la interfaz");
        } catch (SecurityException esperado) { }
        try {
            fs.solicitarOperacion(usuario, "P");
            throw new AssertionError("Usuario autorizado en el disco");
        } catch (SecurityException esperado) { }
        System.out.println("OK: proceso, cola, confirmación, duplicado, cancelación y permisos");
    }
}
