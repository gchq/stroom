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
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

final public class AnnotationUtil {
    private AnnotationUtil(){

    }

    public static <A extends Annotation> A getInheritedClassOrMethodAnnotation(
            Class<A> annotationClass, AnnotatedElement annotatedElement){
            if (annotatedElement instanceof Class<?>){
                return getInheritedClassAnnotation(annotationClass, (Class<?>) annotatedElement);
            }
            if (annotatedElement instanceof Method){
                return getInheritedMethodAnnotation(annotationClass, (Method) annotatedElement);
            }
            if (annotatedElement == null){
                return null;
            }
            throw new IllegalArgumentException("This method requires an instance of Method or Class, got "
             + annotatedElement.getClass().getName());
    }

    public static <A extends Annotation> A getInheritedClassAnnotation(
            Class<A> annotationClass, Class<?> annotatedClass)
    {
        A annotation = annotatedClass.getAnnotation(annotationClass);
        if (annotation == null){
            final Class<?> superclass = annotatedClass.getSuperclass();
            if (superclass != null)
            {
                annotation = getInheritedClassAnnotation(annotationClass, superclass);
                if (annotation != null)
                    return annotation;
            }

            // depth-first search over interface hierarchy
            for (final Class<?> intf : annotatedClass.getInterfaces())
            {
                annotation = getInheritedClassAnnotation(annotationClass, intf);

                if (annotation != null)
                    return annotation;
            }
        }

        return annotation;
    }
    public static <A extends Annotation> A getInheritedMethodAnnotation(
            Class<A> annotationClass, Method method)
    {
        A annotation = method.getAnnotation(annotationClass);
        if (annotation == null){
            annotation = getOverriddenAnnotation(annotationClass, Optional.of(method), OptionalInt.empty());
        }

        return annotation;
    }
    public static <A extends Annotation> A getInheritedParameterAnnotation(
            Class<A> annotationClass, Method method, Parameter annotatedParameter)
    {
        A annotation = annotatedParameter.getAnnotation(annotationClass);
        if (annotation == null){
            int paramNumber;
            for (paramNumber = 0; paramNumber < method.getParameterCount(); paramNumber++){
                if (method.getParameters()[paramNumber].equals(annotatedParameter)){
                    break; //This is the correct parameter
                }
            }
            annotation = getOverriddenAnnotation(annotationClass, Optional.of(method), OptionalInt.of(paramNumber));
        }

        return annotation;
    }

    private static <A extends Annotation> A getOverriddenAnnotation(
            Class<A> annotationClass, Optional<Method> annotatedMethod,
                OptionalInt annotatedParamNumber)
    {
        if (annotatedMethod.isEmpty()) {
            throw new IllegalArgumentException("Must be supplied with either an annotated class or method to search" +
                    " but neither were provided.");
        }
        final Method method = annotatedMethod.get();
        final Class<?> methodClass = method.getDeclaringClass();
        final String name = method.getName();
        final Class<?>[] params = method.getParameterTypes();

        // prioritize all superclasses over all interfaces
        final Class<?> superclass = methodClass.getSuperclass();
        if (superclass != null)
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, superclass, name, params, annotatedParamNumber);
            if (annotation != null)
                return annotation;
        }

        // depth-first search over interface hierarchy
        for (final Class<?> intf : methodClass.getInterfaces())
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, intf, name, params, annotatedParamNumber);
            if (annotation != null)
                return annotation;
        }

        return null;
    }

    private static <A extends Annotation> A getOverriddenAnnotationFrom(
            Class<A> annotationClass, Class<?> searchClass, String name, Class<?>[] params, OptionalInt paramNumber)
    {
        try
        {
            final Method method = searchClass.getMethod(name, params);

            if (paramNumber.isPresent()) {
                int param = paramNumber.getAsInt();
                if (param > method.getParameterCount()) {
                    throw new IllegalArgumentException("Paramter number " + param + " out of range for "
                    + searchClass.getName() + " method " + name);
                }
                final A annotation = method.getParameters()[param].getAnnotation(annotationClass);
                if (annotation != null)
                    return annotation;
            } else {
                final A annotation = method.getAnnotation(annotationClass);
                if (annotation != null)
                    return annotation;
            }
            return getOverriddenAnnotation(annotationClass, Optional.of(method), paramNumber);
        }
        catch (final NoSuchMethodException e)
        {
            return null;
        }
    }



}
