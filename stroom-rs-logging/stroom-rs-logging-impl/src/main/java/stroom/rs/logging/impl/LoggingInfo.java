package stroom.rs.logging.impl;

import stroom.util.shared.StroomLoggingOperation;
import stroom.util.shared.StroomLoggingOperationType;

import javax.ws.rs.HttpMethod;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

class LoggingInfo {
    private final String httpMethod;
    private final String path;
    private final StroomLoggingOperationType operation;
    private final Method method;
    private final Class <?> resourceClass;

    public LoggingInfo(String httpMethod, String path, Method javaMethod, Class<?> resourceClass){
        this.httpMethod = httpMethod;
        this.path = path;
        this.method = javaMethod;
        this.resourceClass = resourceClass;
        operation = findOperation();
    }

    private Optional<StroomLoggingOperationType> getOperationType(final Class<?> restResourceClass) {
        final StroomLoggingOperation opAnnotation = restResourceClass.getAnnotation(StroomLoggingOperation.class);
        return Optional.ofNullable(opAnnotation)
                .or(() ->
                        // No operation annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResourceClass.getInterfaces())
                                .map(clazz -> clazz.getAnnotation(StroomLoggingOperation.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(op -> op.value());
    }

    private final StroomLoggingOperationType findOperation(){
        Optional<StroomLoggingOperationType> type = getOperationType(resourceClass);
        if (type.isPresent()){
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

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public StroomLoggingOperationType getOperationType() {
        return operation;
    }

    public String createCallKey (){
        return StroomServerLoggingFilterImpl.createCallKey(httpMethod, path);
    }

    public Optional<Class> getRequestParamClass (){
        if (method.getParameterCount() == 0){
            return Optional.empty();
        } else if (method.getParameterCount() == 1){
            return Optional.of(method.getParameters()[0].getType());
        }
        //Can't work with multiple parameters.
        return null;
    }
}
