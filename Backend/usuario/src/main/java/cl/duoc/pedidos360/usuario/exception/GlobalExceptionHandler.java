package cl.duoc.pedidos360.usuario.exception;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("[USER-SERVICE][VALIDATION] Datos de usuario inválidos: {}", errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Datos de usuario inválidos: " + errorMsg));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException ex) {
        log.warn("[USER-SERVICE][SECURITY] Excepción de seguridad: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Credenciales inválidas") || msg.contains("Turnstile") || msg.contains("inactiva"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(msg));
        }
        if (msg != null && msg.contains("deshabilitado")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(msg != null ? msg : "Acceso no autorizado"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[USER-SERVICE][ACCESS-DENIED] Privilegios insuficientes: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Acceso prohibido: Privilegios insuficientes."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("[USER-SERVICE][CONFLICT] Conflicto de estado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[USER-SERVICE][BAD_REQUEST] Argumento inválido: {}", ex.getMessage());
        String msg = ex.getMessage();
        if (msg != null && msg.contains("no encontrado")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg));
        }
        if (msg != null && msg.contains("ya se encuentra registrado")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(msg));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg != null ? msg : "Solicitud inválida"));
    }

    @ExceptionHandler(ProtectedResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleProtectedResource(ProtectedResourceException ex) {
        log.warn("[USER-SERVICE][CONFLICT] Intento de operar sobre recurso protegido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("[USER-SERVICE][CONFLICT] Error de integridad o duplicado");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Error de integridad o duplicado en la base de datos"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("[USER-SERVICE][ERROR] Exception no tipada: {}", ex.getMessage());
        if (ex.getMessage() != null && ex.getMessage().contains("ya se encuentra registrado")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
        }
        if (ex.getMessage() != null && ex.getMessage().contains("no encontrado")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("[USER-SERVICE][FATAL] Error interno no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Ha ocurrido un error interno en el servidor."));
    }
}
