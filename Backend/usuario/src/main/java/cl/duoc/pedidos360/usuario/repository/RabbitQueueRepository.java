package cl.duoc.pedidos360.usuario.repository;

import cl.duoc.pedidos360.usuario.entity.RabbitQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RabbitQueueRepository extends JpaRepository<RabbitQueue, Long> {
    Optional<RabbitQueue> findByName(String name);
    boolean existsByName(String name);
    List<RabbitQueue> findByActiveTrue();
}
