package cl.duoc.pedidos360.usuario.dto;

/**
 * Datos que envía el frontend tras un login con Microsoft Entra ID para
 * sincronizar (crear o actualizar) la fila del usuario en usuario_db y
 * obtener su id numérico, que es la identidad que usan los demás
 * microservicios (carrito, pedidos, notificación).
 */
public class EntraSyncRequest {

    private String email;
    private String nombre;
    private String rol; // "ADMIN" | "VENDEDOR" | "CLIENTE" (opcional; default CLIENTE)

    public EntraSyncRequest() {}

    public EntraSyncRequest(String email, String nombre, String rol) {
        this.email = email;
        this.nombre = nombre;
        this.rol = rol;
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
}
