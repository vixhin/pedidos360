package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.entity.UserSession;
import cl.duoc.pedidos360.usuario.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class SessionServiceTest {

    private SessionService sessionService;
    private InMemoryUserSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository = new InMemoryUserSessionRepository();
        sessionService = new SessionService(sessionRepository, "PT8H");
    }

    @Test
    @DisplayName("crearSesion - genera sid, refresh token aleatorio y persiste hash SHA-256")
    void testCrearSesion() {
        SessionService.SessionCreationResult result = sessionService.crearSesion(10L, "Mozilla/5.0", "127.0.0.1");

        assertThat(result.getSid()).isNotBlank();
        assertThat(result.getRawRefreshToken()).isNotBlank();
        assertThat(result.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(7));

        Optional<UserSession> session = sessionRepository.findBySid(result.getSid());
        assertThat(session).isPresent();
        assertThat(session.get().getUsuarioId()).isEqualTo(10L);
        assertThat(session.get().getRefreshTokenHash()).isEqualTo(SessionService.sha256(result.getRawRefreshToken()));
    }

    @Test
    @DisplayName("rotarRefreshToken - rotación legítima emite nuevo refresh token y preserva sid")
    void testRotarRefreshTokenLegitimo() {
        SessionService.SessionCreationResult initial = sessionService.crearSesion(10L, "Mozilla/5.0", "127.0.0.1");

        Optional<SessionService.SessionCreationResult> rotated =
                sessionService.rotarRefreshToken(initial.getRawRefreshToken(), "Mozilla/5.0", "127.0.0.1");

        assertThat(rotated).isPresent();
        assertThat(rotated.get().getSid()).isEqualTo(initial.getSid());
        assertThat(rotated.get().getRawRefreshToken()).isNotEqualTo(initial.getRawRefreshToken());

        // El refresh token viejo ya no debe ser válido
        Optional<SessionService.SessionCreationResult> reuseAttempt =
                sessionService.rotarRefreshToken(initial.getRawRefreshToken(), "Mozilla/5.0", "127.0.0.1");
        assertThat(reuseAttempt).isEmpty();
    }

    @Test
    @DisplayName("revocarSesion - marca la sesión como revocada y esSesionValida retorna false")
    void testRevocarSesion() {
        SessionService.SessionCreationResult result = sessionService.crearSesion(10L, "Mozilla/5.0", "127.0.0.1");
        assertThat(sessionService.esSesionValida(result.getSid())).isTrue();

        sessionService.revocarSesion(result.getSid());
        assertThat(sessionService.esSesionValida(result.getSid())).isFalse();
    }

    // In-memory implementation of UserSessionRepository for fast, reliable unit tests
    private static class InMemoryUserSessionRepository implements UserSessionRepository {
        private final Map<Long, UserSession> store = new ConcurrentHashMap<>();
        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public Optional<UserSession> findBySid(String sid) {
            return store.values().stream().filter(s -> sid.equals(s.getSid())).findFirst();
        }

        @Override
        public int revokeAllByUsuarioId(Long usuarioId, LocalDateTime now) {
            int count = 0;
            for (UserSession s : store.values()) {
                if (usuarioId.equals(s.getUsuarioId()) && s.getRevokedAt() == null) {
                    s.setRevokedAt(now);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int deleteExpiredBefore(LocalDateTime cutoff) {
            int initial = store.size();
            store.entrySet().removeIf(e -> e.getValue().getExpiresAt().isBefore(cutoff));
            return initial - store.size();
        }

        @Override
        public <S extends UserSession> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(idGen.getAndIncrement());
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public Optional<UserSession> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<UserSession> findAll() { return new ArrayList<>(store.values()); }
        @Override public boolean existsById(Long id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long id) { store.remove(id); }
        @Override public void delete(UserSession entity) { if (entity != null && entity.getId() != null) store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends UserSession> entities) { entities.forEach(this::delete); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends UserSession> List<S> saveAll(Iterable<S> entities) { List<S> list = new ArrayList<>(); entities.forEach(e -> list.add(save(e))); return list; }
        @Override public List<UserSession> findAllById(Iterable<Long> ids) { List<UserSession> list = new ArrayList<>(); ids.forEach(id -> findById(id).ifPresent(list::add)); return list; }
        @Override public void flush() {}
        @Override public <S extends UserSession> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends UserSession> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<UserSession> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public UserSession getOne(Long id) { return store.get(id); }
        @Override public UserSession getById(Long id) { return store.get(id); }
        @Override public UserSession getReferenceById(Long id) { return store.get(id); }
        @Override public <S extends UserSession> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends UserSession> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends UserSession> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends UserSession> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends UserSession> long count(Example<S> example) { return 0; }
        @Override public <S extends UserSession> boolean exists(Example<S> example) { return false; }
        @Override public <S extends UserSession, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<UserSession> findAll(Sort sort) { return findAll(); }
        @Override public Page<UserSession> findAll(Pageable pageable) { return Page.empty(); }
    }
}

