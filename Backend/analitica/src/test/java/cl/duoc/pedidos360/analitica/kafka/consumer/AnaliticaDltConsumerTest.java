package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnaliticaDltConsumerTest {

    @Mock
    private AnaliticaService analiticaService;

    @Mock
    private Acknowledgment acknowledgment;

    private AnaliticaDltConsumer dltConsumer;

    @BeforeEach
    void setUp() {
        dltConsumer = new AnaliticaDltConsumer(analiticaService);
    }

    @Test
    @DisplayName("consumeDlt - procesa mensaje de error DLT, registra métrica de error y envía ACK")
    void testConsumeDlt() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "pedidos360.pedidos.dlt.v1", 0, 99L, "pedido-failed-id", "{\"error\": \"fatal\"}"
        );

        dltConsumer.consumeDlt(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService).registrarEvento(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("ERROR_DLT");
        assertThat(captor.getValue().getDescripcion()).contains("pedidos360.pedidos.dlt.v1").contains("pedido-failed-id");

        verify(acknowledgment).acknowledge();
    }
}

