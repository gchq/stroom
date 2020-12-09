package stroom.rs.logging.impl;

import stroom.util.shared.ResourcePaths;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

final public class AnnotationUtil {
    private AnnotationUtil(){

    }

    public static Optional<String> getMethodPath(final Method method) {
        Path annotation = getInheritedAnnotation(Path.class, method);
        if (annotation != null){
            return Optional.of(annotation.value());
        } else {
            return Optional.empty();
        }
    }

    public static <A extends Annotation> A getInheritedAnnotation(
            Class<A> annotationClass, AnnotatedElement element)
    {
        A annotation = element.getAnnotation(annotationClass);
        if (annotation == null && element instanceof Method)
            annotation = getOverriddenAnnotation(annotationClass, (Method) element);
        return annotation;
    }

    public static <A extends Annotation> A getOverriddenAnnotation(
            Class<A> annotationClass, Method method)
    {
        final Class<?> methodClass = method.getDeclaringClass();
        final String name = method.getName();
        final Class<?>[] params = method.getParameterTypes();

        // prioritize all superclasses over all interfaces
        final Class<?> superclass = methodClass.getSuperclass();
        if (superclass != null)
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, superclass, name, params);
            if (annotation != null)
                return annotation;
        }

        // depth-first search over interface hierarchy
        for (final Class<?> intf : methodClass.getInterfaces())
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, intf, name, params);
            if (annotation != null)
                return annotation;
        }

        return null;
    }

    public static <A extends Annotation> A getOverriddenAnnotationFrom(
            Class<A> annotationClass, Class<?> searchClass, String name, Class<?>[] params)
    {
        try
        {
            final Method method = searchClass.getMethod(name, params);
            final A annotation = method.getAnnotation(annotationClass);
            if (annotation != null)
                return annotation;
            return getOverriddenAnnotation(annotationClass, method);
        }
        catch (final NoSuchMethodException e)
        {
            return null;
        }
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

    public static Optional<String> getHttpMethod(AnnotatedElement element) {
        Optional <? extends Annotation> annotation = Optional.ofNullable(getInheritedAnnotation(GET.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.GET);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(PUT.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PUT);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(POST.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.POST);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(DELETE.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.DELETE);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(PATCH.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PATCH);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(HEAD.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.HEAD);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(OPTIONS.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.OPTIONS);
        }

        return Optional.empty();
    }

    public static Optional<String> getHttpMethod(Method method, Class<?> resourceClass){
        Optional<String> httpMethod = getHttpMethod(method);
        if (httpMethod.isPresent()){
            return httpMethod;
        }
        return getHttpMethod(resourceClass);
    }

}
