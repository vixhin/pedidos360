package cl.duoc.pedidos360.usuario.repository;

import cl.duoc.pedidos360.usuario.entity.RabbitProcessedEvent;
import cl.duoc.pedidos360.usuario.entity.RabbitProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RabbitProcessedEventRepository extends JpaRepository<RabbitProcessedEvent, RabbitProcessedEventId> {
    boolean existsById_EventIdAndId_Consumer(String eventId, String consumer);
    Optional<RabbitProcessedEvent> findById_EventIdAndId_Consumer(String eventId, String consumer);
}
