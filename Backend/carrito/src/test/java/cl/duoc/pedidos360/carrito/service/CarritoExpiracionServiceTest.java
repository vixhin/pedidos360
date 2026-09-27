package cl.duoc.pedidos360.carrito.service;

import cl.duoc.pedidos360.carrito.config.RabbitMQConfig;
import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.carrito.repository.CarritoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarritoExpiracionServiceTest {

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CarritoService carritoService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CarritoExpiracionService expiracionService;

    private CarritoItem itemExpirado;

    @BeforeEach
    void setUp() {
        itemExpirado = new CarritoItem(1L, 100L, 5L, 2, 5000.0);
        itemExpirado.setUltimaActividad(LocalDateTime.now().minusHours(50));
    }

    @Test
    @DisplayName("procesarCarritosExpirados - vacia carrito inactivo mas de 48h y publica evento")
    void testProcesarCarritosExpiradosExito() {
        when(carritoRepository.findByUltimaActividadBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(itemExpirado));
        when(carritoRepository.findByUsuarioId(100L))
                .thenReturn(List.of(itemExpirado));
        doNothing().when(carritoService).vaciarCarrito(100L);

        expiracionService.procesarCarritosExpirados();

        verify(carritoService, times(1)).vaciarCarrito(100L);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_CARRITO_EXPIRADO),
                any(CarritoExpiradoEvent.class)
        );
    }

    @Test
    @DisplayName("procesarCarritosExpirados - ignora si no hay carritos expirados")
    void testProcesarCarritosExpiradosSinItems() {
        when(carritoRepository.findByUltimaActividadBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        expiracionService.procesarCarritosExpirados();

        verifyNoInteractions(carritoService);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("procesarCarritosExpirados - no expira si hubo actividad posterior")
    void testProcesarCarritosExpiradosConActividadReciente() {
        CarritoItem itemReciente = new CarritoItem(2L, 100L, 6L, 1, 3000.0);
        itemReciente.setUltimaActividad(LocalDateTime.now().minusMinutes(10));

        when(carritoRepository.findByUltimaActividadBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(itemExpirado));
        when(carritoRepository.findByUsuarioId(100L))
                .thenReturn(List.of(itemExpirado, itemReciente));

        expiracionService.procesarCarritosExpirados();

        verifyNoInteractions(carritoService);
        verifyNoInteractions(rabbitTemplate);
    }
}
