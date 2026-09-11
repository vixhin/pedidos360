package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.EntraSyncRequest;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalControllerTest {

    @Mock
    private UsuarioService usuarioService;

    private InternalController internalController;
    private final String validInternalKey = "pedidos360-internal-secret-key-2026";

    @BeforeEach
    void setUp() {
        internalController = new InternalController(usuarioService, validInternalKey);
    }

    @Test
    @DisplayName("12. /api/internal/entra-sync sin cabecera X-Internal-Service-Key -> 403 Forbidden")
    void testSyncInternalSinHeaderReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EntraSyncRequest dto = new EntraSyncRequest("user@pedidos360.cl", "User", "ADMIN");

        ResponseEntity<?> response = internalController.syncInternal(request, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("12. /api/internal/entra-sync con cabecera incorrecta -> 403 Forbidden")
    void testSyncInternalConHeaderInvalidoReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", "clave-invalida");
        EntraSyncRequest dto = new EntraSyncRequest("user@pedidos360.cl", "User", "ADMIN");

        ResponseEntity<?> response = internalController.syncInternal(request, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("12, 13. /api/internal/entra-sync con cabecera correcta -> 200 OK y persiste rol de confianza")
    void testSyncInternalExito() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", validInternalKey);
        EntraSyncRequest dto = new EntraSyncRequest("admin@pedidos360.cl", "Admin User", "ADMIN");

        UsuarioResponseDTO mockResponse = new UsuarioResponseDTO(10L, "Admin User", "admin@pedidos360.cl", Rol.ADMIN, null, null);
        when(usuarioService.sincronizarUsuarioExterno(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        ResponseEntity<?> response = internalController.syncInternal(request, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
