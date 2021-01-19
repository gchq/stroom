package stroom.rs.logging.impl;

import javax.ws.rs.container.ResourceInfo;
import java.lang.reflect.Method;

public class MockResourceInfo implements ResourceInfo {
    private Class<?> resourceClass;
    private Method method;

    public void setResourceClass(final Class<?> resourceClass) {
        this.resourceClass = resourceClass;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public Method getResourceMethod() {
        return method;
    }

    @Override
    public Class<?> getResourceClass() {
        return resourceClass;
    }
}
