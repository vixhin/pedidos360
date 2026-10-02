package cl.duoc.pedidos360.chat.repository;

import cl.duoc.pedidos360.chat.entity.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    Optional<Conversacion> findByPedidoId(Long pedidoId);

    List<Conversacion> findByClienteIdAndDeletedForClientAtIsNull(Long clienteId);

    List<Conversacion> findByRepartidorIdAndDeletedForCourierAtIsNull(Long repartidorId);

    List<Conversacion> findByEstadoAndFechaCierreBefore(String estado, LocalDateTime fechaCierreLimit);
}
