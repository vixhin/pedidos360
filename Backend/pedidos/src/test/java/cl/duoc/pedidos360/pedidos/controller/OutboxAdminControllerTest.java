package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import cl.duoc.pedidos360.pedidos.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxAdminControllerTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private OutboxAdminController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        ReflectionTestUtils.setField(controller, "bffInternalKey", "secure-s2s-token-12345");
    }

    @Test
    @DisplayName("reprocessFailed - acceso exitoso con JWT rol ADMIN")
    void testReprocessSuccessWithAdminJwt() {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");
        when(outboxService.reprocessFailedEvents(50)).thenReturn(5);

        ResponseEntity<?> response = controller.reprocessFailedEvents(50, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("reprocessedCount")).isEqualTo(5);
        verify(outboxService).reprocessFailedEvents(50);
    }

    @Test
    @DisplayName("reprocessFailed - acceso exitoso con clave interna S2S configurada")
    void testReprocessSuccessWithInternalKey() {
        request.addHeader("X-Internal-Service-Key", "secure-s2s-token-12345");
        when(outboxService.reprocessFailedEvents(20)).thenReturn(2);

        ResponseEntity<?> response = controller.reprocessFailedEvents(20, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(outboxService).reprocessFailedEvents(20);
    }

    @Test
    @DisplayName("reprocessFailed - rechaza acceso con rol distinto a ADMIN (VENDEDOR/CLIENTE)")
    void testReprocessForbiddenNonAdminRole() {
        request.addHeader("Authorization", "Bearer vendor-token");
        when(jwtUtil.validateToken("vendor-token")).thenReturn(true);
        when(jwtUtil.extractRole("vendor-token")).thenReturn("VENDEDOR");

        ResponseEntity<?> response = controller.reprocessFailedEvents(50, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("reprocessFailed - rechaza autenticación si la clave interna está vacía o es nula")
    void testReprocessForbiddenEmptyInternalKey() {
        ReflectionTestUtils.setField(controller, "bffInternalKey", "");
        request.addHeader("X-Internal-Service-Key", "");

        ResponseEntity<?> response = controller.reprocessFailedEvents(50, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("reprocessFailed - rechaza clave interna incorrecta")
    void testReprocessForbiddenInvalidInternalKey() {
        request.addHeader("X-Internal-Service-Key", "wrong-key");

        ResponseEntity<?> response = controller.reprocessFailedEvents(50, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("reprocessFailed - rechaza límite inválido menor a 1")
    void testReprocessBadRequestLimitLessThanOne() {
        ResponseEntity<?> response = controller.reprocessFailedEvents(0, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("reprocessFailed - rechaza límite inválido mayor a 500")
    void testReprocessBadRequestLimitExceedsMax() {
        ResponseEntity<?> response = controller.reprocessFailedEvents(501, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(outboxService);
    }
}

