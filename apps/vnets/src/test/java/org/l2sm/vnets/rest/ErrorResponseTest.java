package org.l2sm.vnets.rest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ErrorResponseTest {

    @Test
    public void constructorStoresCodeAndDescription() {
        ErrorResponse response = new ErrorResponse("C1", "description");

        assertEquals("C1", response.getErrorCode());
        assertEquals("description", response.getErrorDescription());
    }

    @Test
    public void supportsNullValues() {
        ErrorResponse response = new ErrorResponse(null, null);

        assertEquals(null, response.getErrorCode());
        assertEquals(null, response.getErrorDescription());
    }
}
