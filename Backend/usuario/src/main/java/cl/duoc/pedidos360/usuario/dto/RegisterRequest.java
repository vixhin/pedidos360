package cl.duoc.pedidos360.usuario.dto;

import cl.duoc.pedidos360.usuario.enums.Rol;

public class RegisterRequest {
    private String nombre;
    private String email;
    private String password;
    private Rol rol;
    private String turnstileToken;

    public RegisterRequest() {}

    public RegisterRequest(String nombre, String email, String password, Rol rol) {
        this.nombre = nombre;
        this.email = email;
        this.password = password;
        this.rol = rol;
    }

    public RegisterRequest(String nombre, String email, String password, Rol rol, String turnstileToken) {
        this.nombre = nombre;
        this.email = email;
        this.password = password;
        this.rol = rol;
        this.turnstileToken = turnstileToken;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public String getTurnstileToken() {
        return turnstileToken;
    }

    public void setTurnstileToken(String turnstileToken) {
        this.turnstileToken = turnstileToken;
    }
}
