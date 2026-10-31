package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("isAlreadyProcessed - retorna true si el eventId existe en base de datos")
    void testIsAlreadyProcessedTrue() {
        when(processedEventRepository.existsByEventId("evt-123")).thenReturn(true);

        boolean exists = idempotencyService.isAlreadyProcessed("evt-123");

        assertThat(exists).isTrue();
        verify(processedEventRepository).existsByEventId("evt-123");
    }

    @Test
    @DisplayName("isAlreadyProcessed - retorna false para eventId nulo o inexistente")
    void testIsAlreadyProcessedFalse() {
        when(processedEventRepository.existsByEventId("evt-unknown")).thenReturn(false);

        assertThat(idempotencyService.isAlreadyProcessed("evt-unknown")).isFalse();
        assertThat(idempotencyService.isAlreadyProcessed(null)).isFalse();
        assertThat(idempotencyService.isAlreadyProcessed("")).isFalse();
    }

    @Test
    @DisplayName("markAsProcessed - guarda registro de idempotencia con canal y grupo")
    void testMarkAsProcessed() {
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(i -> i.getArguments()[0]);

        idempotencyService.markAsProcessed("evt-123", "KAFKA", "pedido.creado", "10", "corr-1", "analitica-group");

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}

