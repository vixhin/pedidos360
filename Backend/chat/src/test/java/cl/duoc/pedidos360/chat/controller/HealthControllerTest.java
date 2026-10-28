package cl.duoc.pedidos360.chat.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private DataSource dataSource;
    private Connection connection;
    private HealthController healthController;

    @BeforeEach
    void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        connection = Mockito.mock(Connection.class);
        healthController = new HealthController(dataSource);
    }

    @Test
    void testHealthCheck_Up() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("chat-service", response.getBody().get("service"));
    }

    @Test
    void testHealthCheck_Down() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("DB connection refused"));

        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
    }

    @Test
    void testReadinessCheck_Ready() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = healthController.readinessCheck();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("READY", response.getBody().get("status"));
    }

    @Test
    void testReadinessCheck_NotReady() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Error"));

        ResponseEntity<Map<String, Object>> response = healthController.readinessCheck();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOT_READY", response.getBody().get("status"));
    }
}
