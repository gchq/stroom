/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.event.logging.rs.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Class that finds annotations on superclasses/interfaces of supplied instance.
 * <p>
 * Credit for initial implementation goes to the author of this answer on
 * Stack Overflow: https://stackoverflow.com/a/17281097
 */
public final class AnnotationUtil {

    private AnnotationUtil() {

    }

    public static <A extends Annotation> A getInheritedClassOrMethodAnnotation(
            final Class<A> annotationClass, final AnnotatedElement annotatedElement) {
        if (annotatedElement instanceof Class<?>) {
            return getInheritedClassAnnotation(annotationClass, (Class<?>) annotatedElement);
        }
        if (annotatedElement instanceof Method) {
            return getInheritedMethodAnnotation(annotationClass, (Method) annotatedElement);
        }
        if (annotatedElement == null) {
            return null;
        }
        throw new IllegalArgumentException("This method requires an instance of Method or Class, got "
                                           + annotatedElement.getClass().getName());
    }

    public static <A extends Annotation> A getInheritedClassAnnotation(
            final Class<A> annotationClass, final Class<?> annotatedClass) {
        //Check class itself
        A annotation = annotatedClass.getAnnotation(annotationClass);

        //Check super classes.
        if (annotation == null) {
            final Class<?> superclass = annotatedClass.getSuperclass();
            if (superclass != null) {
                annotation = getInheritedClassAnnotation(annotationClass, superclass);
                if (annotation != null) {
                    return annotation;
                }
            }

            //Check interfaces
            for (final Class<?> interfaze : annotatedClass.getInterfaces()) {
                annotation = getInheritedClassAnnotation(annotationClass, interfaze);

                if (annotation != null) {
                    return annotation;
                }
            }
        }

        return annotation;
    }

    public static <A extends Annotation> A getInheritedMethodAnnotation(
            final Class<A> annotationClass, final Method method) {
        A annotation = method.getAnnotation(annotationClass);
        if (annotation == null) {
            annotation = getOverriddenAnnotation(annotationClass, Optional.of(method), OptionalInt.empty());
        }

        return annotation;
    }

    public static <A extends Annotation> A getInheritedParameterAnnotation(
            final Class<A> annotationClass, final Method method, final Parameter annotatedParameter) {
        A annotation = annotatedParameter.getAnnotation(annotationClass);
        if (annotation == null) {
            int paramNumber;
            for (paramNumber = 0; paramNumber < method.getParameterCount(); paramNumber++) {
                if (method.getParameters()[paramNumber].equals(annotatedParameter)) {
                    break; //This is the correct parameter
                }
            }
            annotation = getOverriddenAnnotation(annotationClass, Optional.of(method), OptionalInt.of(paramNumber));
        }

        return annotation;
    }

    private static <A extends Annotation> A getOverriddenAnnotation(
            final Class<A> annotationClass, final Optional<Method> annotatedMethod,
            final OptionalInt annotatedParamNumber) {
        if (annotatedMethod.isEmpty()) {
            throw new IllegalArgumentException("Must be supplied with either an annotated class or method to search" +
                                               " but neither were provided.");
        }
        final Method method = annotatedMethod.get();
        final Class<?> methodClass = method.getDeclaringClass();
        final String name = method.getName();
        final Class<?>[] params = method.getParameterTypes();

        // Check super classes
        final Class<?> superclass = methodClass.getSuperclass();
        if (superclass != null) {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, superclass, name, params, annotatedParamNumber);
            if (annotation != null) {
                return annotation;
            }
        }

        // Check interfaces
        for (final Class<?> interfaze : methodClass.getInterfaces()) {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, interfaze, name, params, annotatedParamNumber);
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    private static <A extends Annotation> A getOverriddenAnnotationFrom(final Class<A> annotationClass,
                                                                        final Class<?> searchClass,
                                                                        final String name,
                                                                        final Class<?>[] params,
                                                                        final OptionalInt paramNumber) {
        try {
            final Method method = searchClass.getMethod(name, params);

            if (paramNumber.isPresent()) {
                final int param = paramNumber.getAsInt();
                if (param > method.getParameterCount()) {
                    throw new IllegalArgumentException("Parameter number " + param + " out of range for "
                                                       + searchClass.getName() + " method " + name);
                }
                final A annotation = method.getParameters()[param].getAnnotation(annotationClass);
                if (annotation != null) {
                    return annotation;
                }
            } else {
                final A annotation = method.getAnnotation(annotationClass);
                if (annotation != null) {
                    return annotation;
                }
            }
            return getOverriddenAnnotation(annotationClass, Optional.of(method), paramNumber);
        } catch (final NoSuchMethodException e) {
            return null;
        }
    }


}
