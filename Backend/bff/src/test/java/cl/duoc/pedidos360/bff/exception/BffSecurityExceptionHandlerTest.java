package cl.duoc.pedidos360.bff.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class BffSecurityExceptionHandlerTest {

    private final BffSecurityExceptionHandler handler = new BffSecurityExceptionHandler();

    @Test
    @DisplayName("commence - genera respuesta JSON 401 Unauthorized")
    void testCommence401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/bff/productos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("Token faltante"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(response.getContentAsString()).contains("Token inválido, expirado o ausente");
    }

    @Test
    @DisplayName("handle - genera respuesta JSON 403 Forbidden")
    void testHandle403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/bff/admin");
        request.addHeader("Authorization", "Bearer sample-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Acceso denegado"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        assertThat(response.getContentAsString()).contains("No posee permisos suficientes");
    }
}
