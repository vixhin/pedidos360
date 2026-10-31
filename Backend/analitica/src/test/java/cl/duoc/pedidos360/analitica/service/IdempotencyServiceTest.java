package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.repository.AnaliticaRepository;
import cl.duoc.pedidos360.analitica.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private AnaliticaRepository analiticaRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(processedEventRepository, analiticaRepository);
    }

    @Test
    @DisplayName("processEventTransactional - procesa evento nuevo, registra métrica y retorna true")
    void testProcessEventTransactionalSuccess() {
        ProcessedEvent event = new ProcessedEvent("evt-123", "KAFKA", "pedido.creado", "10", "corr-1", "analitica-group", Instant.now());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "PEDIDO_CREADO", "Pedido #10 creado");

        when(processedEventRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        boolean processed = idempotencyService.processEventTransactional(event, metrica);

        assertThat(processed).isTrue();
        verify(analiticaRepository).save(metrica);
    }

    @Test
    @DisplayName("processEventTransactional - detecta duplicado vía UNIQUE, omite métrica y retorna false")
    void testProcessEventTransactionalDuplicate() {
        ProcessedEvent event = new ProcessedEvent("evt-dup", "KAFKA", "pedido.creado", "10", "corr-1", "analitica-group", Instant.now());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "PEDIDO_CREADO", "Pedido #10 creado");

        when(processedEventRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        boolean processed = idempotencyService.processEventTransactional(event, metrica);

        assertThat(processed).isFalse();
        verify(analiticaRepository, never()).save(any());
    }

    @Test
    @DisplayName("processEventTransactional - propaga excepciones de base de datos sin silenciarlas")
    void testProcessEventTransactionalDatabaseError() {
        ProcessedEvent event = new ProcessedEvent("evt-err", "KAFKA", "pedido.creado", "10", "corr-1", "analitica-group", Instant.now());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "PEDIDO_CREADO", "Pedido #10 creado");

        when(processedEventRepository.insertIfNotExists(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("Database lock acquisition timeout"));

        assertThrows(QueryTimeoutException.class, () ->
                idempotencyService.processEventTransactional(event, metrica));

        verify(analiticaRepository, never()).save(any());
    }

    @Test
    @DisplayName("isAlreadyProcessed - verifica existencia en base de datos")
    void testIsAlreadyProcessed() {
        when(processedEventRepository.existsByEventId("evt-123")).thenReturn(true);
        when(processedEventRepository.existsByEventId("evt-unknown")).thenReturn(false);

        assertThat(idempotencyService.isAlreadyProcessed("evt-123")).isTrue();
        assertThat(idempotencyService.isAlreadyProcessed("evt-unknown")).isFalse();
        assertThat(idempotencyService.isAlreadyProcessed(null)).isFalse();
    }
}
