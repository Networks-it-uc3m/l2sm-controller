package org.l2sm.vnets.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IDCOServiceExceptionTest {

    @Test
    public void getMessageReturnsConstructorMessage() {
        IDCOServiceException exception = new IDCOServiceException("boom");

        assertEquals("boom", exception.getMessage());
    }

    @Test
    public void toStringUsesExpectedPrefix() {
        IDCOServiceException exception = new IDCOServiceException("boom");

        assertEquals("ERROR: boom", exception.toString());
    }

    @Test
    public void supportsNullMessage() {
        IDCOServiceException exception = new IDCOServiceException(null);

        assertEquals(null, exception.getMessage());
        assertEquals("ERROR: null", exception.toString());
    }
}
