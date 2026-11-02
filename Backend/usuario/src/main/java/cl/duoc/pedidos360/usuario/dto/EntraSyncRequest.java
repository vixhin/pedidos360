package cl.duoc.pedidos360.usuario.dto;

/**
 * Datos que envía el BFF tras un login con Microsoft Entra ID para
 * sincronizar (crear o actualizar) la fila del usuario en usuario_db y
 * obtener su id numérico local.
 */
public class EntraSyncRequest {

    private String email;
    private String nombre;
    private String rol; // "ADMIN" | "VENDEDOR" | "CLIENTE"
    private boolean rolFromAppRole; // true si proviene explícitamente del claim roles de Entra ID

    public EntraSyncRequest() {}

    public EntraSyncRequest(String email, String nombre, String rol) {
        this(email, nombre, rol, false);
    }

    public EntraSyncRequest(String email, String nombre, String rol, boolean rolFromAppRole) {
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
        this.rolFromAppRole = rolFromAppRole;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isRolFromAppRole() {
        return rolFromAppRole;
    }

    public void setRolFromAppRole(boolean rolFromAppRole) {
        this.rolFromAppRole = rolFromAppRole;
    }
}
