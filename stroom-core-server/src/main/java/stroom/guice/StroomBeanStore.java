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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StroomBeanStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanStore.class);

    private static final String PACKAGE = "stroom";
//    private static final String STROOM_CLASSES = "stroom.";

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


//        Set<StroomBean> set = null;//getAnnotatedBeanStore().getStroomBeanMap().get(annotation);
//        if (set == null) {
//            set = Collections.emptySet();
//        }
//        return set.stream().map(StroomBean::getBeanName).collect(Collectors.toSet());
//
////        final Set<String> results = new HashSet<>();
////        final String[] names = applicationContext.getBeanDefinitionNames();
////        for (final String name : names) {
////            try {
////                final Object bean = applicationContext.getBean(name);
////                final Class<?> targetClass = AopUtils.getTargetClass(bean);
////                if (targetClass.isAnnotationPresent(annotationType)) {
////                    results.add(name);
////                } else {
////                    for (final Method method : targetClass.getMethods()) {
////                        if (method.isAnnotationPresent(annotationType)) {
////                            results.add(name);
////                        }
////                    }
////                }
////            } catch (final Exception e) {
////                LOGGER.error(e.getMessage(), e);
////            }
////        }
//
//
////        final Set<String> results = new HashSet<>();
////        new FastClasspathScanner("stroom")
////                .matchClassesWithAnnotation(annotationType, c -> {
////                    final String[] names = applicationContext.getBeanNamesForType(c);
////                    if (names.length > 0) {
////                        results.addAll(Arrays.asList(names));
////                    } else {
////                        addBeansByInterface(results, c, annotationType);
////                    }
////                })
////                .scan();
////
////        new FastClasspathScanner("stroom")
////                .matchClassesWithMethodAnnotation(annotationType, c -> {
////                    final String[] names = applicationContext.getBeanNamesForType(c);
////                    if (names.length > 0) {
////                        results.addAll(Arrays.asList(names));
////                    } else {
////                        addBeansByInterface(results, c, annotationType);
////                    }
////                })
////                .scan();
//
//
////        final Set<String> beanNames = new HashSet<>();
////        beanNames.addAll(Arrays.asList(applicationContext.getBeanDefinitionNames()));
////
////        for (final String beanName : beanNames) {
////            if (applicationContext.findAnnotationOnBean(beanName, annotationType) != null) {
////                results.add(beanName);
////            }
////        }
////        return results;
//    }

//    private void addBeansByInterface(final Set<String> results, final Class<?> c, final Class<? extends Annotation> annotationType) {
//        final Class<?>[] interfaces = c.getInterfaces();
//
//        for (final Class<?> anInterface : interfaces) {
//            final String[] otherNames = applicationContext.getBeanNamesForType(anInterface);
//            for (final String otherName : otherNames) {
//                try {
//                    final Object bean = applicationContext.getBean(otherName);
//                    final Class<?> targetClass = AopUtils.getTargetClass(bean);
//                    if (targetClass.isAnnotationPresent(annotationType)) {
//                        results.add(otherName);
//                    } else {
//                        for (final Method method : targetClass.getMethods()) {
//                            if (method.isAnnotationPresent(annotationType)) {
//                                results.add(otherName);
//                            }
//                        }
//                    }
//                } catch (final Exception e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            }
//        }
//    }

//    public Set<String> getStroomBeanByType(final Class<?> type) {
////        final Set<String> results = getAnnotatedBeanStore()
////                .getStroomBeanMap()
////                .values()
////                .stream()
////                .flatMap(Collection::stream)
////                .filter(bean -> type.isAssignableFrom(bean.getBeanClass()))
////                .map(StroomBean::getBeanName)
////                .collect(Collectors.toSet());
//
//
//        final Set<String> results = new HashSet<>();
//        final Set<String> beanNames = new HashSet<>();
//        beanNames.addAll(Arrays.asList(applicationContext.getBeanDefinitionNames()));
//
//        for (final String beanName : beanNames) {
//            if (applicationContext.isTypeMatch(beanName, type)) {
//                results.add(beanName);
//            }
//        }
//        return results;
//    }

    public <T> Set<T> getBeansOfType(Class<T> type) {
//        final Set<T> set = new HashSet<>();
//        final Binding<T> binding = injector.getBinding(type);
//        if (binding instanceof MultibinderBinding) {
//            final MultibinderBinding multibinderBinding = (MultibinderBinding) binding;
//            final List<Binding<T>> bindings = multibinderBinding.getElements();
//            bindings.forEach(b -> {
//                final T handler = b.getProvider().get();
//                set.add(handler);
//            });
//        } else {
//            final T handler = binding.getProvider().get();
//            set.add(handler);
//        }
//
//        return set;

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

//    public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
//            throws BeansException {
//        return null;//applicationContext.getBeansOfType(type, includeNonSingletons, allowEagerInit);
//    }

    public <A extends Annotation> A findAnnotationOnBean(final String beanName, final Class<A> annotationType) {
//        injector.

//        final Object bean = beanFactory.getBean(beanName);
//        final Class<?> targetClass = AopUtils.getTargetClass(bean);
//        if (targetClass.isAnnotationPresent(annotationType)) {
//            return targetClass.getAnnotation(annotationType);
//        } else {
//            for (final Method method : targetClass.getMethods()) {
//                if (method.isAnnotationPresent(annotationType)) {
//                    return method.getAnnotation(annotationType);
//                }
//            }
//        }
//
//
////        return applicationContext.findAnnotationOnBean(beanName, annotationType);
        throw new RuntimeException("Annotation " + annotationType + " not found on bean '" + beanName + "'");


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
//            // Test to see if the bean is an instance of JdkDynamicProxy. If it
//            // is then only the interface will be available on the proxy. We
//            // ideally want to execute a method on the proxy as the proxy may be
//            // providing transactional behaviour or security interception etc.
//            // If we really can't get the method off the proxy then we will need
//            // to get the proxy target and invoke the method on that directly.
//            if (AopUtils.isJdkDynamicProxy(bean)) {
//                Method method = null;
//
//                // Try and get the method from the proxied interfaces.
//                for (final Method m : bean.getClass().getMethods()) {
//                    if (m.getName().equals(beanMethod.getName())
//                            && Arrays.equals(m.getParameterTypes(), beanMethod.getParameterTypes())) {
//                        method = m;
//                        break;
//                    }
//                }
//
//                if (method == null) {
//                    // If we didn't manage to get the method from the proxied
//                    // interfaces then invoke them method on the proxy target
//                    // directly. This might result in errors as we will be
//                    // bypassing transaction interception etc.
//                    try {
//                        final Object o = ((Advised) bean).getTargetSource().getTarget();
//                        return beanMethod.invoke(o, args);
//                    } catch (final Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                } else {
//                    return method.invoke(bean, args);
//                }
//
//            } else {
                beanMethod.setAccessible(true);
                beanMethod.invoke(bean, args);
//            }
        }

        return null;
    }

    public Object getBean(final StroomBeanMethod stroomBeanMethod) {
        if (stroomBeanMethod.getBeanClass() != null) {
            return getBean(stroomBeanMethod.getBeanClass());
        }
        return getBean(stroomBeanMethod.getBeanClass());
    }

//    public <T> T getBean(final Class<T> stroomBeanClass) {
//        T bean = null;
//        try {
//            bean = injector.getInstance(stroomBeanClass);
////            bean = beanFactory.getBean(stroomBeanClass);
//        } catch (final Throwable t) {
//            LOGGER.error("Unable to get bean!", t);
//        }
//
//        if (bean == null) {
//            LOGGER.error("getBean() - {} returned null !!", stroomBeanClass);
//        }
//
//        return bean;
//    }

//    private AnnotatedBeanStore getAnnotatedBeanStore() {
//        if (annotatedBeanStore == null) {
//            synchronized (this) {
//                if (annotatedBeanStore == null) {
//                    annotatedBeanStore = new AnnotatedBeanStore(applicationContext);
//                }
//            }
//        }
//        return annotatedBeanStore;
//    }
//
//    private static class AnnotatedBeanStore {
//        private final Map<Class<?>, Set<StroomBeanMethod>> stroomBeanMethodMap = new HashMap<>();
//        private final Map<Class<?>, Set<StroomBean>> stroomBeanMap = new HashMap<>();
//
//        AnnotatedBeanStore(final ApplicationContext applicationContext) {
//            final String[] allBeans = applicationContext.getBeanDefinitionNames();
//
//            for (final String beanName : allBeans) {
//                Class<?> beanClass = applicationContext.getType(beanName);
//                if (beanClass != null) {
//                    // Here we need to get the real class if we have be given a
//                    // CGLIB Proxy.
//                    if (beanClass.getName().contains("CGLIB")) {
//                        beanClass = beanClass.getSuperclass();
//                    } else if (beanClass.getName().contains("$")) {
//                        Object bean = applicationContext.getBean(beanName);
//                        beanClass = AopUtils.getTargetClass(bean);
//                    }
//
//                    // Only bother with our own code
//                    if (!beanClass.getName().contains(STROOM_CLASSES)) {
//                        continue;
//                    }
//
//                    if (beanClass.getName().contains("$")) {
//                        LOGGER.error("init() - UNABLE TO RESOLVE BEAN CLASS ?? MAYBE SPRING IS NO LONGER USING CGLIB .... {}",
//                                beanClass.getName());
//                    }
//
////                if (beanClass.isInterface()) {
////                    LOGGER.error("init() - EXPECTED CLASS BUT RECEIVED INTERFACE .... {}",
////                            beanClass.getName());
////                }
//
//
//                    addClass(beanClass, beanName);
//                    addAnnotatedMethods(beanClass, beanName);
//
////                // Add from instance.
////                try {
////                    final Object bean = applicationContext.getBean(beanName);
////                    final Class<?> targetClass = AopUtils.getTargetClass(bean);
////                    addClass(targetClass, beanName);
////                    addAnnotatedMethods(targetClass, beanName);
////                } catch (final Exception e) {
////                    LOGGER.error(e.getMessage(), e);
////                }
//                }
//            }
//
//            if (LOGGER.isDebugEnabled()) {
//                final List<String> beanMethodList = new ArrayList<>();
//                for (final Set<StroomBeanMethod> methods : stroomBeanMethodMap.values()) {
//                    for (final StroomBeanMethod method : methods) {
//                        beanMethodList.add(method.toString());
//                    }
//                }
//                Collections.sort(beanMethodList);
//                for (final String string : beanMethodList) {
//                    LOGGER.debug("init() - {}", string);
//                }
//            }
//        }
//
//        private void addClass(final Class<?> clazz, final String beanName) {
//            if (clazz != null) {
//                final Annotation[] allAnnotation = clazz.getAnnotations();
//                for (final Annotation annotation : allAnnotation) {
//                    final Class<?> annotationType = annotation.annotationType();
//                    if (annotationType.getName().contains(STROOM_CLASSES)) {
//                        stroomBeanMap.computeIfAbsent(annotationType, k -> new HashSet<>()).add(new StroomBean(beanName, clazz));
//                    }
//                }
//
//                // Recurse to add annotated methods from superclass.
//                addClass(clazz.getSuperclass(), beanName);
//            }
//        }
//
//        private void addAnnotatedMethods(final Class<?> clazz, final String beanName) {
//            if (clazz != null) {
//                final Method[] methodList = clazz.getMethods();
//                for (final Method method : methodList) {
//                    final Annotation[] allAnnotation = method.getAnnotations();
//                    for (final Annotation annotation : allAnnotation) {
//                        final Class<?> annotationType = annotation.annotationType();
//                        if (annotationType.getName().contains(STROOM_CLASSES)) {
//                            stroomBeanMethodMap.computeIfAbsent(annotationType, k -> new HashSet<>()).add(new StroomBeanMethod(beanName, method));
//                        }
//                    }
//                }
//
//                // Recurse to add annotated methods from superclass.
//                addAnnotatedMethods(clazz.getSuperclass(), beanName);
//            }
//        }
//
//        Map<Class<?>, Set<StroomBean>> getStroomBeanMap() {
//            return stroomBeanMap;
//        }
//
//        Map<Class<?>, Set<StroomBeanMethod>> getStroomBeanMethodMap() {
//            return stroomBeanMethodMap;
//        }
//    }
}
