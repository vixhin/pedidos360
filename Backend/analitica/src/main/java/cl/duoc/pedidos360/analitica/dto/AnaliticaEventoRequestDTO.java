package cl.duoc.pedidos360.analitica.dto;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;

import java.time.LocalDateTime;

/**
 * Datos para registrar un evento de analítica. Excluye id; si no se envía fecha, se usa la actual.
 */
public record AnaliticaEventoRequestDTO(String tipoEvento, String descripcion, LocalDateTime fecha) {

    public AnaliticaEvento toEntity() {
        AnaliticaEvento evento = new AnaliticaEvento();
        evento.setTipoEvento(tipoEvento);
        evento.setDescripcion(descripcion);
        evento.setFecha(fecha != null ? fecha : LocalDateTime.now());
        return evento;
    }
}
