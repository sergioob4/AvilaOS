package avilaos.enums;

public enum OperacionArchivo {
    CREAR("Crear archivo"), LEER("Leer"), ACTUALIZAR("Renombrar"), ELIMINAR("Eliminar"),
    CREAR_DIRECTORIO("Crear directorio");

    private final String etiqueta;

    OperacionArchivo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
