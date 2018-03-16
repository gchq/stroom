/*
 * Copyright 2016 Crown Copyright
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

package stroom.guice;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.spring.StroomBeanMethod;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StroomBeanStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanStore.class);

    private static final String PACKAGE = "stroom";

    private final Map<Class<? extends Annotation>, Set<Class<?>>> classMap = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, Set<StroomBeanMethod>> methodMap = new ConcurrentHashMap<>();

    private final Injector injector;

    @Inject
    public StroomBeanStore(final Injector injector) {
        this.injector = injector;
    }

    public Set<Class<?>> getAnnotatedStroomBeans(final Class<? extends Annotation> annotation) {
        return classMap.computeIfAbsent(annotation, a -> {
            final Set<Class<?>> classes = new HashSet<>();
            new FastClasspathScanner(PACKAGE)
                    .matchClassesWithAnnotation(annotation, classes::add)
                    .scan();
            return Collections.unmodifiableSet(classes);
        });
    }

    public Set<StroomBeanMethod> getAnnotatedStroomBeanMethods(final Class<? extends Annotation> annotation) {
        return methodMap.computeIfAbsent(annotation, a -> {
            final Set<StroomBeanMethod> set = new HashSet<>();
            new FastClasspathScanner(PACKAGE)
                    .matchClassesWithMethodAnnotation(a, (matchingClass, matchingMethodOrConstructor) -> {
                        for (final Method method : matchingClass.getMethods()) {
                            if (method.isAnnotationPresent(a)) {
                                set.add(new StroomBeanMethod(matchingClass, method));
                            }
                        }
                    })
                    .scan();
            return Collections.unmodifiableSet(set);
        });
    }

    public <T> Set<T> getBeansOfType(Class<T> type) {
        return getBindings(type);
    }

    private <T> Set<T> getBindings(Class<T> type) {
        final TypeLiteral<Set<T>> lit = setOf(type);
        final Key<Set<T>> key = Key.get(lit);
        return this.injector.getInstance(key);
    }

    @SuppressWarnings("unchecked")
    private static <T> TypeLiteral<Set<T>> setOf(Class<T> type) {
        return (TypeLiteral<Set<T>>) TypeLiteral.get(Types.setOf(type));
    }

    public <T> T getBean(final Class<T> type) {
        T o = null;
        try {
            o = injector.getInstance(type);
        } catch (final Throwable t) {
            LOGGER.error("Unable to get bean!", t);
        }

        if (o == null) {
            LOGGER.error("getBean() - {} returned null !!", type);
        }

        return o;
    }

    public Object getBean(final String name) {
        Object o = null;
        try {
            o = injector.getInstance(Key.get(Object.class, Names.named(name)));
        } catch (final Throwable t) {
            LOGGER.error("Unable to get bean!", t);
        }

        if (o == null) {
            LOGGER.error("getBean() - {} returned null !!", name);
        }

        return o;
    }

    public Object invoke(final StroomBeanMethod stroomBeanMethod, final Object... args) throws InvocationTargetException, IllegalAccessException {
        // Get the bean.
        final Object bean = getBean(stroomBeanMethod.getBeanClass());
        final Method beanMethod = stroomBeanMethod.getBeanMethod();

        if (bean != null) {
            beanMethod.setAccessible(true);
            beanMethod.invoke(bean, args);
        }

        return null;
    }

    public Object getBean(final StroomBeanMethod stroomBeanMethod) {
        if (stroomBeanMethod.getBeanClass() != null) {
            return getBean(stroomBeanMethod.getBeanClass());
        }
        return getBean(stroomBeanMethod.getBeanClass());
    }
}
