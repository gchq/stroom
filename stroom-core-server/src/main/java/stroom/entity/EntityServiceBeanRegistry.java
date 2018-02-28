/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.document.DocumentStore;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityServiceBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityServiceBeanRegistry.class);
    private final Map<Class<?>, String> entityServiceClassMap = new HashMap<>();
    private final Map<String, String> entityServiceTypeMap = new HashMap<>();
    private final Map<List<Object>, Method> entityServiceMethodMap = new ConcurrentHashMap<>();

    private final StroomBeanStore beanStore;
    private volatile boolean init;

    private final Map<String, Object> externalDocRefServices = new HashMap<>();

    @Inject
    EntityServiceBeanRegistry(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    /**
     * Used to register services that are instantiations of a generic class. These are services that cannot
     * be found using Spring Bean reflection.
     *
     * @param type    The doc ref type this service will manage
     * @param service An instance of the service to use.
     */
    public void addExternal(final String type, final Object service) {
        this.externalDocRefServices.put(type, service);
    }

    public Object getEntityService(final Class<?> clazz) {
        final String beanName = getEntityServiceName(clazz, clazz);
        return beanStore.getBean(beanName);
    }

    public Object getEntityService(final String entityType) {
        if (externalDocRefServices.containsKey(entityType)) {
            return externalDocRefServices.get(entityType);
        } else {
            final String beanName = getEntityServiceName(entityType);
            return beanStore.getBean(beanName);
        }
    }

    public Object invoke(final String methodName, final Object... args) {
        Object retVal;

        try {
            if (args == null || args.length == 0) {
                throw new EntityServiceException("At least one argument is expected");
            }

            final Object obj = args[0];
            final Class<?> clazz = obj.getClass();
            final String beanName = getEntityServiceName(clazz, clazz);
            if (beanName == null) {
                throw new EntityServiceException("No bean name found for " + clazz.getSimpleName());
            }

            final Object entityService = beanStore.getBean(beanName);
            final Method method = getMethod(entityService.getClass(), methodName, buildArgTypes(args));
            if (method == null) {
                throw new EntityServiceException("No method '" + methodName + "' found on bean '" + beanName + "'");
            }

            retVal = method.invoke(entityService, args);

        } catch (final Exception e) {
            throw EntityServiceExceptionUtil.create(e);
        }

        return retVal;
    }

    private Class<?>[] buildArgTypes(final Object[] args) {
        final Class<?>[] argTypes = new Class<?>[args.length];
        int i = 0;
        for (final Object arg : args) {
            argTypes[i] = arg.getClass();
            i++;
        }
        return argTypes;
    }

    private Method getMethod(final Class<?> beanClazz, final String methodName, final Class<?>... argTypes) {
        final List<Object> signature = new ArrayList<>();
        signature.add(methodName);
        signature.addAll(Arrays.asList(argTypes));
        final Method method = getEntityServiceMethodMap().get(signature);
        if (method != null) {
            return method;
        }
        for (final Method testMethod : beanClazz.getMethods()) {
            if (testMethod.getName().equals(methodName) && testMethod.getParameterTypes().length == argTypes.length) {
                boolean allOk = true;
                for (int i = 0; i < argTypes.length; i++) {
                    if (!testMethod.getParameterTypes()[i].isAssignableFrom(argTypes[i])) {
                        allOk = false;
                    }
                }
                if (allOk) {
                    getEntityServiceMethodMap().put(signature, testMethod);
                    return testMethod;
                }

            }
        }
        return null;
    }

    private String getEntityServiceName(final Class<?> baseClass, final Class<?> entityClass) {
        if (entityClass == null) {
            throw new EntityServiceException("Unknown handler for " + baseClass.getName(), null, false);
        }
        final String name = getEntityServiceClassMap().get(entityClass);
        if (name != null) {
            return name;
        }
        return getEntityServiceName(baseClass, entityClass.getSuperclass());
    }

    private String getEntityServiceName(final String entityType) {
        final String name = getEntityServiceTypeMap().get(entityType);
        if (name == null) {
            throw new EntityServiceException("No service found for " + entityType, null, false);
        }
        return name;
    }

    private Class<?> tryParameterizedType(final Type clazz, final Class<?> paramClazz) {
        if (clazz instanceof ParameterizedType) {
            final ParameterizedType parameterizedType = (ParameterizedType) clazz;
            for (final Type type : parameterizedType.getActualTypeArguments()) {
                if (type instanceof Class<?> && paramClazz.isAssignableFrom((Class<?>) type)) {
                    return (Class<?>) type;
                }
            }
        }
        return null;

    }

    Class<?> findParameterizedType(final Class<?> clazz, final Class<?> paramClazz) {
        Class<?> rtnType = tryParameterizedType(clazz.getGenericSuperclass(), paramClazz);
        if (rtnType != null) {
            return rtnType;
        }
        final Type[] types = clazz.getGenericInterfaces();
        for (final Type type : types) {
            rtnType = tryParameterizedType(type, paramClazz);
            if (rtnType != null) {
                return rtnType;
            }
            if (type instanceof Class<?>) {
                rtnType = findParameterizedType((Class<?>) type, paramClazz);
                if (rtnType != null) {
                    return rtnType;
                }
            }

        }
        return null;
    }

    private Map<Class<?>, String> getEntityServiceClassMap() {
        init();
        return entityServiceClassMap;
    }

    private Map<String, String> getEntityServiceTypeMap() {
        init();
        return entityServiceTypeMap;
    }

    private Map<List<Object>, Method> getEntityServiceMethodMap() {
        init();
        return entityServiceMethodMap;
    }

    private void init() {
        if (init) {
            return;
        }

        synchronized (this) {
            if (init) {
                return;
            }

            beanStore.getBeansOfType(EntityService.class, false, false).forEach((name, bean) -> {
                try {
                    if (!name.toLowerCase().startsWith("cached")) {
                        final Class<?> entityType = findParameterizedType(bean.getClass(), Entity.class);
                        if (entityType != null && Entity.class.isAssignableFrom(entityType)) {
                            try {
                                final Entity entity = (Entity) entityType.newInstance();
                                final String existing = entityServiceTypeMap.put(entity.getType(), name);
                                if (existing != null) {
                                    LOGGER.error("Existing bean found for entity type '" + existing + "'");
                                }
                            } catch (final Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }

                            final String existing = entityServiceClassMap.put(entityType, name);
                            if (existing != null) {
                                LOGGER.error("Existing bean found for entity type class '" + existing + "'");
                            }
                        }

                        final Class<?> findType = findParameterizedType(bean.getClass(), BaseCriteria.class);
                        if (findType != null) {
                            final String existing = entityServiceClassMap.put(findType, name);
                            if (existing != null) {
                                LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });

            beanStore.getBeansOfType(FindService.class, false, false).forEach((name, bean) -> {
                try {
                    if (!name.toLowerCase().startsWith("cached")) {
                        final Class<?> findType = findParameterizedType(bean.getClass(), BaseCriteria.class);
                        if (findType != null) {
                            final String existing = entityServiceClassMap.put(findType, name);
                            if (existing != null) {
                                LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });

            beanStore.getBeansOfType(DocumentStore.class, false, false).forEach((name, bean) -> {
                try {
                    if (!name.toLowerCase().startsWith("cached")) {
                        final String existing = entityServiceTypeMap.put(bean.getDocType(), name);
                        if (existing != null) {
                            LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });


//            Arrays.stream(applicationContext.getBeanDefinitionNames()).forEach(beanName -> {
//
//                        final Object bean = applicationContext.getBean(beanName);
//
//                        if (bean instanceof EntityService<?>) {
//
//
//
//
//                        } else if (bean instanceof FindService<?, ?>) {
//                            final Class<?> findType = findParameterizedType(bean.getClass(), BaseCriteria.class);
//                            if (findType != null) {
//                                final String existing = entityServiceClassMap.put(findType, beanName);
//                                if (existing != null) {
//                                    LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
//                                }
//                            }
//                        } else if (bean instanceof DocumentStore) {
//                            final DocumentStore documentStore = (DocumentStore) bean;
//                            final String existing = entityServiceTypeMap.put(documentStore.getDocType(), beanName);
//                            if (existing != null) {
//                                LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
//                            }
//                        }
//                    }
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//            });
            init = true;
        }
    }
}
