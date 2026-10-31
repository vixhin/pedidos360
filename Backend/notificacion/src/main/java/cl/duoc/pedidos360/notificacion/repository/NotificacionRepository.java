package cl.duoc.pedidos360.notificacion.repository;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioId(Long usuarioId);
    List<Notificacion> findByUsuarioIdOrderByFechaEnvioDesc(Long usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.id = :id")
    void marcarLeida(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId")
    void marcarTodasLeidas(@Param("usuarioId") Long usuarioId);
}
