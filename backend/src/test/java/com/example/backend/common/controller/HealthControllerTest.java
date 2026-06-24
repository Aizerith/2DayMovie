package com.example.backend.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HealthControllerTest {

    @Test
    void returnsOkWhenDatabaseIsAvailable() throws Exception {
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        var response = new HealthController(dataSource).health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void returnsServiceUnavailableWhenDatabaseIsDown() throws Exception {
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new IllegalStateException("Database unavailable"));

        var response = new HealthController(dataSource).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
