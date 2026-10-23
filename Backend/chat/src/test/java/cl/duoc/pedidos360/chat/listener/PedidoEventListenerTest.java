package cl.duoc.pedidos360.chat.listener;

import cl.duoc.pedidos360.chat.service.ChatService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoEventListenerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private Channel channel;

    private PedidoEventListener listener;

    private static final long DELIVERY_TAG = 42L;

    @BeforeEach
    void setUp() {
        listener = new PedidoEventListener(chatService);
    }

    @Test
    @DisplayName("Should auto-close chat and send ACK when order status is ENTREGADO")
    void handlePedidoEstadoActualizado_Entregado_ClosesChatAndAcks() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", 100L);
        event.put("nuevoEstado", "ENTREGADO");

        listener.handlePedidoEstadoActualizado(event, channel, DELIVERY_TAG);

        verify(chatService).cerrarConversacionPorPedidoEntregado(100L);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Should handle Integer resourceId, auto-close chat and send ACK")
    void handlePedidoEstadoActualizado_IntegerResourceId_ClosesChatAndAcks() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", 200);
        event.put("nuevoEstado", "ENTREGADO");

        listener.handlePedidoEstadoActualizado(event, channel, DELIVERY_TAG);

        verify(chatService).cerrarConversacionPorPedidoEntregado(200L);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Should ACK without closing chat when order status is not ENTREGADO")
    void handlePedidoEstadoActualizado_OtherStatus_DoesNotCloseChatAndAcks() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", 100L);
        event.put("nuevoEstado", "EN_PREPARACION");

        listener.handlePedidoEstadoActualizado(event, channel, DELIVERY_TAG);

        verifyNoInteractions(chatService);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Should ACK without closing chat when resourceId is null")
    void handlePedidoEstadoActualizado_NullResourceId_DoesNotCloseChatAndAcks() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", null);
        event.put("nuevoEstado", "ENTREGADO");

        listener.handlePedidoEstadoActualizado(event, channel, DELIVERY_TAG);

        verifyNoInteractions(chatService);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Should send NACK (requeue=false, routed to DLQ) when chatService throws exception")
    void handlePedidoEstadoActualizado_ServiceException_SendsNack() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("resourceId", 100L);
        event.put("nuevoEstado", "ENTREGADO");

        doThrow(new RuntimeException("Database error")).when(chatService).cerrarConversacionPorPedidoEntregado(100L);

        listener.handlePedidoEstadoActualizado(event, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Should send NACK when event map is null causing exception")
    void handlePedidoEstadoActualizado_NullEvent_SendsNack() throws IOException {
        listener.handlePedidoEstadoActualizado(null, channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
