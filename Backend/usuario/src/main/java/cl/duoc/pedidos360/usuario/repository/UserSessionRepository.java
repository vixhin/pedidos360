package cl.duoc.pedidos360.usuario.repository;

import cl.duoc.pedidos360.usuario.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySid(String sid);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.usuarioId = :usuarioId AND s.revokedAt IS NULL")
    int revokeAllByUsuarioId(@Param("usuarioId") Long usuarioId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :cutoff OR (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}

