package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByUsuarioId(Long usuarioId);
    List<Pedido> findByRepartidorIdIsNullOrRepartidorId(Long repartidorId);
    Optional<Pedido> findByIdempotencyKey(String idempotencyKey);
}
