package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnaliticaRepository extends JpaRepository<AnaliticaEvento, Long> {
    List<AnaliticaEvento> findByTipoEvento(String tipoEvento);
}
