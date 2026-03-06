package org.l2sm.vnets.rest;

import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class NBInterfaceWebApplicationTest {

    @Test
    public void getClassesRegistersExpectedResources() {
        NBInterfaceWebApplication app = new NBInterfaceWebApplication();

        Set<Class<?>> classes = app.getClasses();

        assertTrue(classes.contains(NetworkManagement.class));
        assertTrue(classes.contains(RESTExceptionMapper.class));
        assertTrue(classes.contains(ObjectProvider.class));
    }
}
