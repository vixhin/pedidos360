package cl.duoc.pedidos360.usuario.repository;

import cl.duoc.pedidos360.usuario.entity.RabbitBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RabbitBindingRepository extends JpaRepository<RabbitBinding, Long> {
    Optional<RabbitBinding> findByQueue_IdAndExchange_IdAndRoutingKey(Long queueId, Long exchangeId, String routingKey);
    boolean existsByQueue_IdAndExchange_IdAndRoutingKey(Long queueId, Long exchangeId, String routingKey);
    List<RabbitBinding> findByActiveTrue();
    List<RabbitBinding> findByQueue_Id(Long queueId);
    List<RabbitBinding> findByExchange_Id(Long exchangeId);
}
