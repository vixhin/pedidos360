package cl.duoc.pedidos360.notificacion.service;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.repository.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    public List<Notificacion> obtenerTodas() {
        return notificacionRepository.findAll();
    }

    public List<Notificacion> obtenerPorUsuario(Long usuarioId) {
        return notificacionRepository.findByUsuarioIdOrderByFechaEnvioDesc(usuarioId);
    }

    @Transactional
    public Notificacion enviarNotificacion(Notificacion notificacion) {
        if (notificacion.getLeida() == null) {
            notificacion.setLeida(false);
        }
        return notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarLeida(Long id) {
        notificacionRepository.marcarLeida(id);
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        notificacionRepository.marcarTodasLeidas(usuarioId);
    }
}
