package org.idco.rest;

import org.onlab.rest.AbstractWebApplication;

import java.util.Set;

/**
 * REST API Application
 */
public class WebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(NetworkManagement.class, RESTExceptionMapper.class, ObjectProvider.class);
    }
}
