package stroom.rs.logging.impl;

import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.inject.Provider;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static stroom.rs.logging.impl.AnnotationUtil.getInheritedClassOrMethodAnnotation;
import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class ResourcePathMap {
    private static final String ALL_VARIABLE_RS_PATHS = "*{}*";
    private final Map<String, List<LoggingInfo>> loggingInfoMap;
    private final Map<Pattern, LoggingInfo> variablePathsMap;

    ResourcePathMap (final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap){
        loggingInfoMap =  providerMap.keySet().stream().flatMap(ResourcePathMap::findCallsForResource).
                collect(Collectors.groupingBy(ResourcePathMap::createCallKey));

        variablePathsMap = loggingInfoMap.get(ALL_VARIABLE_RS_PATHS).stream().
                collect(Collectors.toMap(ResourcePathMap::createPathPattern, l -> l));
    }

    public LoggingInfo lookup (String method, String path){
        if (path.contains("?")){
            path = path.substring(0, path.indexOf('?'));
        }
        String key = createCallKey(method, path);
        List<LoggingInfo> possibleMatches = loggingInfoMap.get(key);

        if (possibleMatches != null && possibleMatches.size() == 1){
            return possibleMatches.get(0);
        }


        return searchForVariableMatch(key);

    }

    private LoggingInfo searchForVariableMatch(String key){
        List<LoggingInfo> matches = variablePathsMap.keySet().stream().flatMap(
                pattern -> {
                    Matcher matcher = pattern.matcher(key);
                    if (matcher.matches()){
                        return Stream.of(variablePathsMap.get(pattern));
                    }
                    return Stream.empty();
                }
        ).collect(Collectors.toList());

        if (matches.size() > 1){
            LOGGER.warn("Multiple (" + matches.size() + ") resources found by rs logger at HTTPMethod:path " + key );
        }
        if (matches.size() > 0){
            return matches.get(0);
        }

        return null;
    }

    public LoggingInfo lookup (ContainerRequestContext context){
        return lookup (context.getMethod(),
                ResourcePaths.buildAuthenticatedApiPath(context.getUriInfo().getPath()));
    }

    private static Pattern createPathPattern(LoggingInfo loggingInfo){
        String regex = createCallKey(loggingInfo.getHttpMethod(),
                loggingInfo.getPath().replaceAll("\\{[^/]+}","[\\\\S]+"));
        return Pattern.compile(regex);
    }


    private static Optional<String> getJavaRSPath(final Method method) {
        Path annotation = AnnotationUtil.getInheritedMethodAnnotation(Path.class, method);
        if (annotation != null){
            return Optional.of(annotation.value());
        } else {
            return Optional.empty();
        }
    }

    private static Stream<LoggingInfo> findCallsForResource (RestResourcesBinder.ResourceType resourceType){
        Class<?> resourceClass = resourceType.getResourceClass();

        Optional<String> resourcePath = getResourcePath(resourceClass);
        if (resourcePath.isEmpty()){
            LOGGER.warn("Unable to determine HTTP method for class " + resourceClass.getName());
            return Stream.empty();
        }

        return Arrays.stream(resourceClass.getMethods()).sequential().flatMap(m -> {
            Optional<String> methodPath = getJavaRSPath (m);
            String method;
            if (methodPath.isEmpty()) {
                method = ""; //This is the default method for the resource
            } else {
                method = methodPath.get();
            }

            Optional<String> httpMethod = findHttpMethodOnMethodOrClass(m);
            if (httpMethod.isEmpty()) {
                //Not a remote resource call
                return Stream.empty();
            }

            //Normalise slashes in path
            if (!method.startsWith("/")){
                method = "/" + method;
            }
            if (method.endsWith("/")){
                method = method.substring(0, method.length() - 1);
            }

            return Stream.of(new LoggingInfo(
                    httpMethod.get(),
                    resourcePath.get() +
                            method,
                    m, resourceClass));
        });
    }

    public static Optional<String> getResourcePath(final Class<?> restResourceClass) {
        final Path pathAnnotation = restResourceClass.getAnnotation(Path.class);
        return Optional.ofNullable(pathAnnotation)
                .or(() ->
                        // No Path annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResourceClass.getInterfaces())
                                .map(clazz -> clazz.getAnnotation(Path.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(path ->
                        ResourcePaths.buildAuthenticatedApiPath(path.value()));
    }

    private static Optional<String> getHttpMethod(AnnotatedElement method) {
        Optional <? extends Annotation> annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(GET.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.GET);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(PUT.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PUT);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(POST.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.POST);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(DELETE.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.DELETE);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(PATCH.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PATCH);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(HEAD.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.HEAD);
        }
        annotation = Optional.ofNullable(getInheritedClassOrMethodAnnotation(OPTIONS.class, method));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.OPTIONS);
        }

        return Optional.empty();
    }

    private static Optional<String> findHttpMethodOnMethodOrClass(Method method){
        //First check method
        Optional<String> httpMethod = getHttpMethod(method);

        if (httpMethod.isEmpty()){
            httpMethod = getHttpMethod(method.getDeclaringClass());
        }

        return httpMethod;
    }


    private static String createCallKey (LoggingInfo loggingInfo){
        return createCallKey(loggingInfo.getHttpMethod(), loggingInfo.getPath());
    }

    private static String createCallKey (String method, String originalPath){
        if (originalPath.endsWith("/")){
            originalPath = originalPath.substring(0, originalPath.length() - 1);
        }
        if (originalPath.contains("{")) {
            return ALL_VARIABLE_RS_PATHS;
        }

        return method + ":" + originalPath;
    }
}
