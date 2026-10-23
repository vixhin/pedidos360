package cl.duoc.pedidos360.usuario.repository;

import cl.duoc.pedidos360.usuario.entity.RabbitExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RabbitExchangeRepository extends JpaRepository<RabbitExchange, Long> {
    Optional<RabbitExchange> findByName(String name);
    boolean existsByName(String name);
    List<RabbitExchange> findByActiveTrue();
}
