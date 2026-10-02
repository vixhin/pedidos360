package cl.duoc.pedidos360.chat.repository;

import cl.duoc.pedidos360.chat.entity.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    List<Mensaje> findByConversacionIdOrderByFechaEnvioAsc(Long conversacionId);

    List<Mensaje> findByConversacionIdAndEstado(Long conversacionId, String estado);
}
