package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    boolean existsByResourceIdAndEventType(String resourceId, String eventType);
}

