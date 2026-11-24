package avilaos.proceso;

import avilaos.enums.ModoUsuario;
import avilaos.enums.OperacionArchivo;
import avilaos.enums.Permiso;
import avilaos.sistemaarchivos.NodoSistemaArchivos;

/** Datos de la operación y de la sesión que la solicitó, conservados durante la espera por CPU. */
public record PeticionArchivo(OperacionArchivo operacion, String ruta, String usuario, ModoUsuario modo,
                              int tamanoBloques, Permiso permiso, String nuevoNombre) {
    public PeticionArchivo {
        if (operacion == null || modo == null || usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("La petición requiere una operación, un usuario y un modo.");
        }
        usuario = usuario.trim();
        if (ruta == null || !ruta.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe ser absoluta.");
        }
        if (operacion == OperacionArchivo.CREAR) {
            if (tamanoBloques < 1 || permiso == null) {
                throw new IllegalArgumentException("Crear requiere un tamaño positivo y un permiso.");
            }
        } else if (tamanoBloques != 0 || permiso != null) {
            throw new IllegalArgumentException("Solo la creación admite tamaño y permiso.");
        }
        if (operacion == OperacionArchivo.ACTUALIZAR) {
            nuevoNombre = NodoSistemaArchivos.validarNombre(nuevoNombre);
        } else if (nuevoNombre != null) {
            throw new IllegalArgumentException("Solo la actualización admite un nombre nuevo.");
        }
    }
}
