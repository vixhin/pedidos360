package cl.duoc.pedidos360.productos.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "promociones")
public class Promocion {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 50)
    private String tag;

    @Column(nullable = false, length = 50)
    private String color;

    @Column(nullable = false)
    private Boolean activa = true;

    public Promocion() {}

    public Promocion(String id, String titulo, String tag, String color, Boolean activa) {
        this.id = id;
        this.titulo = titulo;
        this.tag = tag;
        this.color = color;
        this.activa = activa != null ? activa : true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getActiva() {
        return activa;
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }
}

