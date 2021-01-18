package stroom.rs.logging.impl;

import stroom.util.shared.EventLogged;
import stroom.util.shared.HasId;
import stroom.util.shared.HasUuid;
import stroom.util.shared.StroomLoggingOperationType;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

public class ContainerResourceInfo {
    private final ContainerRequestContext requestContext;
    private final ResourceInfo resourceInfo;
    private final StroomLoggingOperationType operationType;

    public ContainerResourceInfo(final ResourceInfo resourceInfo, final ContainerRequestContext requestContext) {
        this.resourceInfo = resourceInfo;
        this.requestContext = requestContext;
        this.operationType = findOperationType(getMethod(), getResourceClass(), requestContext.getMethod());
    }

    public ContainerRequestContext getRequestContext() {
        return requestContext;
    }

    public ResourceInfo getResourceInfo() {
        return resourceInfo;
    }

    public Class<?> getResourceClass() {
        return resourceInfo.getResourceClass();
    }

    public Method getMethod(){
        return resourceInfo.getResourceMethod();
    }

    public StroomLoggingOperationType getOperationType(){
        return operationType;
    }

    public String getTypeId(){
        //If method annotation provided use that on its own
        if ((getMethod().getAnnotation(EventLogged.class) != null) &&
                (!getMethod().getAnnotation(EventLogged.class).typeId().equals(EventLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(EventLogged.class).typeId();
        }
        String resourcePrefix = getResourceClass().getSimpleName();
        if ((getResourceClass().getAnnotation(EventLogged.class) != null) &&
                (!getResourceClass().getAnnotation(EventLogged.class).typeId().equals(EventLogged.ALLOCATE_AUTOMATICALLY))){
            resourcePrefix = getResourceClass().getAnnotation(EventLogged.class).typeId();
        }

        return resourcePrefix + "." + getMethod().getName();
    }

    public String getVerbFromAnnotations(){
        if ((getMethod().getAnnotation(EventLogged.class) != null) &&
                (!getMethod().getAnnotation(EventLogged.class).verb().equals(EventLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(EventLogged.class).verb();
        }
        return null;
    }

    private static Optional<StroomLoggingOperationType> getOperationTypeFromAnnotations(final Method method,
                                                                                        final Class<?> resourceClass){
        if (method.getAnnotation(EventLogged.class) != null){
            return Optional.of(method.getAnnotation(EventLogged.class).value());
        } else if (resourceClass.getAnnotation(EventLogged.class) != null){
            return Optional.of(resourceClass.getAnnotation(EventLogged.class).value());
        }
        return Optional.empty();
    }

    public Optional<StroomLoggingOperationType> getOperationTypeFromAnnotations(){
        return getOperationTypeFromAnnotations(getMethod(), getResourceClass());
    }

    public boolean shouldLog (boolean logByDefault){

        Optional<StroomLoggingOperationType> specifiedOperation = getOperationTypeFromAnnotations();
        if (specifiedOperation.isPresent()){
            return !specifiedOperation.get().equals(StroomLoggingOperationType.UNLOGGED);
        }

        return logByDefault;
    }

    private StroomLoggingOperationType findOperationType(final Method method,
                                                         final Class<?> resourceClass,
                                                         final String httpMethod) {
        Optional<StroomLoggingOperationType> type = getOperationTypeFromAnnotations(method, resourceClass);
        if (type.isPresent() && !StroomLoggingOperationType.ALLOCATE_AUTOMATICALLY.equals(type.get())){
            return type.get();
        } else if (HttpMethod.DELETE.equals(httpMethod)){
            return StroomLoggingOperationType.DELETE;
        } else if (method.getName().startsWith("get")){
            return StroomLoggingOperationType.VIEW;
        } else if (method.getName().startsWith("fetch")) {
            return StroomLoggingOperationType.VIEW;
        } else if (method.getName().startsWith("read")){
            return StroomLoggingOperationType.VIEW;
        } else if (method.getName().startsWith("create")){
            return StroomLoggingOperationType.CREATE;
        } else if (method.getName().startsWith("delete")){
            return StroomLoggingOperationType.DELETE;
        } else if (method.getName().startsWith("update")){
            return StroomLoggingOperationType.UPDATE;
        }  else if (method.getName().startsWith("save")){
            return StroomLoggingOperationType.UPDATE;
        } else if (method.getName().startsWith("find")){
            return StroomLoggingOperationType.SEARCH;
        } else if (method.getName().startsWith("search")){
            return StroomLoggingOperationType.SEARCH;
        }  else if (method.getName().startsWith("list")){
            return StroomLoggingOperationType.SEARCH;
        } else if (method.getName().startsWith("import")){
            return StroomLoggingOperationType.IMPORT;
        } else if (method.getName().startsWith("export")){
            return StroomLoggingOperationType.EXPORT;
        } else if (method.getName().startsWith("upload")){
            return StroomLoggingOperationType.IMPORT;
        } else if (method.getName().startsWith("download")){
            return StroomLoggingOperationType.EXPORT;
        } else if (method.getName().startsWith("set")){
            return StroomLoggingOperationType.UPDATE;
        } else if (method.getName().startsWith("copy")){
            return StroomLoggingOperationType.COPY;
        }
        return StroomLoggingOperationType.UNKNOWN;
    }

}
