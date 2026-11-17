package cl.duoc.pedidos360.notificacion.dto;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;

import java.time.LocalDateTime;

/**
 * Datos para crear una notificación. Excluye id y fechaEnvio: la fecha la asigna el servidor.
 */
public record NotificacionRequestDTO(Long usuarioId, String mensaje, String canal, Boolean leida) {

    public Notificacion toEntity() {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(usuarioId);
        notificacion.setMensaje(mensaje);
        notificacion.setCanal(canal);
        notificacion.setLeida(leida != null ? leida : Boolean.FALSE);
        notificacion.setFechaEnvio(LocalDateTime.now());
        return notificacion;
    }
}
