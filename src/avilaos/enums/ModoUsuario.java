package avilaos.enums;

/**
 * Modo de uso activo en la interfaz de ÁvilaOS.
 *
 * <ul>
 *   <li>{@link #ADMINISTRADOR}: puede crear, modificar y eliminar archivos y directorios,
 *       gestionar procesos, cambiar políticas y ver toda la información del disco.</li>
 *   <li>{@link #USUARIO}: solo lee archivos propios o públicos y crea procesos de E/S
 *       sobre sus propios archivos.</li>
 * </ul>
 */
public enum ModoUsuario {

    ADMINISTRADOR("Administrador", "Acceso total al sistema de archivos y a la configuración"),

    USUARIO("Usuario", "Acceso restringido a archivos propios o públicos");

    // -------------------------------------------------------------------------
    // Atributos
    // -------------------------------------------------------------------------

    private final String nombreLegible;
    private final String descripcion;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    ModoUsuario(String nombreLegible, String descripcion) {
        this.nombreLegible = nombreLegible;
        this.descripcion = descripcion;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getNombreLegible() {
        return nombreLegible;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean esAdministrador() {
        return this == ADMINISTRADOR;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
