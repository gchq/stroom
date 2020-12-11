package stroom.rs.logging.impl;

import stroom.util.shared.StroomLoggingOperation;
import stroom.util.shared.StroomLoggingOperationType;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class LoggingInfo {
    private final String httpMethod;
    private final String path;
    private final StroomLoggingOperationType operation;
    private final Method method;
    private final Class <?> resourceClass;
    private final Optional<Class<?>> parameterType;

    public LoggingInfo(String httpMethod, String path, Method javaMethod, Class<?> resourceClass){
        this.httpMethod = httpMethod;
        this.path = path;
        this.method = javaMethod;
        this.resourceClass = resourceClass;
        operation = findOperation();
        parameterType = findParameterType(path, javaMethod);
    }

    private static Optional<StroomLoggingOperationType> getOperationType(final Class<?> restResourceClass) {
        final StroomLoggingOperation opAnnotation = restResourceClass.getAnnotation(StroomLoggingOperation.class);
        return Optional.ofNullable(opAnnotation)
                .or(() ->
                        // No operation annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResourceClass.getInterfaces())
                                .map(clazz -> clazz.getAnnotation(StroomLoggingOperation.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(StroomLoggingOperation::value);
    }

    private static Optional<Class<?>> findParameterType (String path, Method method){
        if (method.getParameterCount() == 0){
            return Optional.empty();
        }

        List<Parameter> paramsWithoutPathAnnotation = Arrays.stream(method.getParameters()).
                filter(p -> AnnotationUtil.getInheritedParameterAnnotation(Path.class, method, p) == null).
                collect(Collectors.toList());

        if (paramsWithoutPathAnnotation.size() > 1) {
            LOGGER.warn("Unable to provide logging for api call "  + path + " because implementation " + method.getName()
                    + " has multiple parameters");
        }

        if (paramsWithoutPathAnnotation.size() == 1){
            return Optional.of(paramsWithoutPathAnnotation.get(0).getType());
        }

        return Optional.empty();
    }

    private StroomLoggingOperationType findOperation(){
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

    public Optional<Class<?>> getRequestParamClass (){
      return parameterType;
    }
}
