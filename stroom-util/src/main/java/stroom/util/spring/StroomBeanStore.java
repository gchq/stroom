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

package stroom.util.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import stroom.util.logging.StroomLogger;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class StroomBeanStore implements InitializingBean, BeanFactoryAware, ApplicationContextAware {
    private final StroomLogger LOGGER = StroomLogger.getLogger(StroomBeanStore.class);

    private static final String STROOM_CLASSES = "stroom.";

    private boolean initialised = false;
    private final Map<Class<?>, List<StroomBeanMethod>> stroomBeanMethodMap = new HashMap<>();

    private ApplicationContext applicationContext;
    private BeanFactory beanFactory;

    public List<StroomBeanMethod> getStroomBeanMethod(final Class<?> annotation) {
        List<StroomBeanMethod> list = stroomBeanMethodMap.get(annotation);
        if (list == null) {
            list = new ArrayList<>();
        }
        return list;
    }

    public Set<String> getStroomBean(final Class<? extends Annotation> annotationType) {
        final Set<String> results = new HashSet<>();

        final Set<String> beanNames = new HashSet<>();
        beanNames.addAll(Arrays.asList(applicationContext.getBeanDefinitionNames()));

        for (final String beanName : beanNames) {
            if (applicationContext.findAnnotationOnBean(beanName, annotationType) != null) {
                results.add(beanName);
            }
        }
        return results;
    }

    public Set<String> getStroomBeanByType(final Class<?> type) {
        final Set<String> results = new HashSet<>();

        final Set<String> beanNames = new HashSet<>();
        beanNames.addAll(Arrays.asList(applicationContext.getBeanDefinitionNames()));

        for (final String beanName : beanNames) {
            if (applicationContext.isTypeMatch(beanName, type)) {
                results.add(beanName);
            }
        }
        return results;
    }

    public <A extends Annotation> A findAnnotationOnBean(final String beanName, final Class<A> annotationType) {
        return applicationContext.findAnnotationOnBean(beanName, annotationType);

    }

    public Object getBean(final String name) {
        Object o = null;
        try {
            o = beanFactory.getBean(name);
        } catch (final Throwable t) {
            LOGGER.error(t, t);
        }

        if (o == null) {
            LOGGER.error("getBean() - %s returned null !!", name);
        }

        return o;
    }

    public Object invoke(final StroomBeanMethod stroomBeanMethod, final Object... args)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Get the bean.
        final Object bean = getBean(stroomBeanMethod.getBeanName());
        final Method beanMethod = stroomBeanMethod.getBeanMethod();

        if (bean != null) {
            // Test to see if the bean is an instance of JdkDynamicProxy. If it
            // is then only the interface will be available on the proxy. We
            // ideally want to execute a method on the proxy as the proxy may be
            // providing transactional behaviour or security interception etc.
            // If we really can't get the method off the proxy then we will need
            // to get the proxy target and invoke the method on that directly.
            if (AopUtils.isJdkDynamicProxy(bean)) {
                Method method = null;

                // Try and get the method from the proxied interfaces.
                for (final Method m : bean.getClass().getMethods()) {
                    if (m.getName().equals(beanMethod.getName())
                            && Arrays.equals(m.getParameterTypes(), beanMethod.getParameterTypes())) {
                        method = m;
                        break;
                    }
                }

                if (method == null) {
                    // If we didn't manage to get the method from the proxied
                    // interfaces then invoke them method on the proxy target
                    // directly. This might result in errors as we will be
                    // bypassing transaction interception etc.
                    try {
                        final Object o = ((Advised) bean).getTargetSource().getTarget();
                        return beanMethod.invoke(o, args);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return method.invoke(bean, args);
                }

            } else {
                beanMethod.invoke(bean, args);
            }
        }

        return null;
    }

    public Object getBean(final StroomBeanMethod stroomBeanMethod) {
        return getBean(stroomBeanMethod.getBeanName());
    }

    public <T> T getBean(final Class<T> stroomBeanClass) {
        T bean = null;
        try {
            bean = beanFactory.getBean(stroomBeanClass);
        } catch (final Throwable t) {
            LOGGER.error(t, t);
        }

        if (bean == null) {
            LOGGER.error("getBean() - %s returned null !!", stroomBeanClass);
        }

        return bean;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    private synchronized void init() {
        if (initialised) {
            return;
        }

        final String[] allBeans = applicationContext.getBeanDefinitionNames();

        for (final String beanName : allBeans) {
            Class<?> beanClass = applicationContext.getType(beanName);
            if (beanClass != null) {
                // Here we need to get the real class if we have be given a
                // CGLIB Proxy.
                if (beanClass.getName().contains("CGLIB")) {
                    beanClass = beanClass.getSuperclass();
                }
                // Only bother with out own code
                if (!beanClass.getName().contains(STROOM_CLASSES)) {
                    continue;
                }

                if (beanClass.getName().contains("$")) {
                    LOGGER.error("init() - UNABLE TO RESOVE BEAN CLASS ?? MAYBE SPRING IS NOLONGER USING CGLIB .... %s",
                            beanClass.getName());
                }

                addAnnotatedMethods(beanClass, beanName);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            final List<String> beanMethodList = new ArrayList<>();
            for (final List<StroomBeanMethod> methods : stroomBeanMethodMap.values()) {
                for (final StroomBeanMethod method : methods) {
                    beanMethodList.add(method.toString());
                }
            }
            Collections.sort(beanMethodList);
            for (final String string : beanMethodList) {
                LOGGER.debug("init() - %s", string);
            }
        }
        initialised = true;
    }

    private void addAnnotatedMethods(final Class<?> clazz, final String beanName) {
        if (clazz != null) {
            final Method[] methodList = clazz.getMethods();
            for (final Method method : methodList) {
                final Annotation[] allAnnotation = method.getAnnotations();
                for (final Annotation annotation : allAnnotation) {
                    final Class<?> annotationType = annotation.annotationType();
                    if (annotationType.getName().contains(STROOM_CLASSES)) {
                        List<StroomBeanMethod> list = stroomBeanMethodMap.get(annotationType);
                        if (list == null) {
                            list = new ArrayList<>();
                            stroomBeanMethodMap.put(annotationType, list);
                        }

                        list.add(new StroomBeanMethod(beanName, method));
                    }
                }
            }

            // Recurse to add annotated methods from superclass.
            addAnnotatedMethods(clazz.getSuperclass(), beanName);
        }
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
