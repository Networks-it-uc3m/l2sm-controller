package org.l2sm.vnets.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.junit.Test;
import org.l2sm.vnets.api.IDCOServiceException;

import com.fasterxml.jackson.core.JsonParseException;

public class RESTExceptionMapperTest {

    private final RESTExceptionMapper mapper = new RESTExceptionMapper();

    @Test
    public void jsonParseExceptionReturnsBadRequest() {
        Response response = mapper.toResponse(new JsonParseException(null, "bad payload"));

        assertEquals(400, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("PARSING_ERROR", body.getErrorCode());
        assertEquals("Could not parse the received object", body.getErrorDescription());
    }

    @Test
    public void genericExceptionReturnsInternalServerError() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertEquals(500, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals("INTERNAL_ERROR", body.getErrorCode());
        assertTrue(body.getErrorDescription().contains("Internal error"));
    }

    @Test
    public void idcoServiceExceptionPathCurrentlyThrowsIllegalArgumentException() {
        try {
            mapper.toResponse(new IDCOServiceException("failure"));
        } catch (IllegalArgumentException e) {
            return;
        }

        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    public void providerIsInstantiable() {
        RESTExceptionMapper instance = new RESTExceptionMapper();
        assertTrue(instance instanceof RESTExceptionMapper);
    }
}
