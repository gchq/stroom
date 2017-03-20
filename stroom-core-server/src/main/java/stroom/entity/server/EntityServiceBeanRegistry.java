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

package stroom.entity.server;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindService;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomBeanStore;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EntityServiceBeanRegistry implements BeanPostProcessor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(EntityServiceBeanRegistry.class);
    private final Map<Class<?>, String> entityServiceClassMap = new HashMap<>();
    private final Map<String, String> entityServiceTypeMap = new HashMap<>();
    private final Map<List<Object>, Method> entityServiceMethodMap = new ConcurrentHashMap<>();
    @Resource
    private StroomBeanStore beanStore;

    public Object getEntityService(final Class<?> clazz) {
        final String beanName = getEntityServiceName(clazz, clazz);
        final Object entityService = beanStore.getBean(beanName);
        return entityService;
    }

    public Object getEntityService(final String entityType) {
        final String beanName = getEntityServiceName(entityType);
        final Object entityService = beanStore.getBean(beanName);
        return entityService;
    }

    /**
     * @return All instances that are a child of superClass
     */
    public Collection<Object> getAllServicesByParent(final Class<?> superClass) {
        final Collection<Object> services = new HashSet<>();

        // Loop through all the cached entities.
        for (final Entry<Class<?>, String> entry : entityServiceClassMap.entrySet()) {
            final Class<?> entity = entry.getKey();
            if (Entity.class.isAssignableFrom(entity)) {
                final Object bean = beanStore.getBean(entry.getValue());
                final Class<?> targetClass = AopUtils.getTargetClass(bean);
                if (superClass.isAssignableFrom(targetClass)) {
                    services.add(bean);
                }
            }
        }
        return services;
    }

    public Object invoke(final String methodName, final Object... args) {
        Object retVal = null;

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
            final Method method = getMethod(beanName, entityService.getClass(), methodName, buildArgTypes(args));
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

    protected Method getMethod(final String beanName, final Class<?> beanClazz, final String methodName,
                               final Class<?>... argTypes) {
        final List<Object> signature = new ArrayList<>();
        signature.add(methodName);
        for (final Class<?> argType : argTypes) {
            signature.add(argType);
        }
        final Method method = entityServiceMethodMap.get(signature);
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
                    entityServiceMethodMap.put(signature, testMethod);
                    return testMethod;
                }

            }
        }
        return null;
    }

    protected String getEntityServiceName(final Class<?> baseClass, final Class<?> entityClass) {
        if (entityClass == null) {
            throw new EntityServiceException("Unknown handler for " + baseClass.getName(), null, false);
        }
        final String name = entityServiceClassMap.get(entityClass);
        if (name != null) {
            return name;
        }
        return getEntityServiceName(baseClass, entityClass.getSuperclass());
    }

    protected String getEntityServiceName(final String entityType) {
        final String name = entityServiceTypeMap.get(entityType);
        if (name == null) {
            throw new EntityServiceException("No service found for " + entityType, null, false);
        }
        return name;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        return bean;
    }

    public Class<?> findSubInterface(final Class<?> clazz, final Class<?> clazzInterface) {
        final Class<?>[] interfaceList = clazz.getInterfaces();

        for (final Class<?> ainterface : interfaceList) {
            if (ainterface.equals(clazzInterface)) {
                return clazz;
            }
            final Class<?> find = findSubInterface(ainterface, clazzInterface);
            if (find != null) {
                return find;
            }
        }
        return null;
    }

    public Class<?> tryParameterizedType(final Type clazz, final Class<?> paramClazz) {
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

    public Class<?> findParameterizedType(final Class<?> clazz, final Class<?> paramClazz) {
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

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        if (!beanName.toLowerCase().startsWith("cached")) {
            if (bean instanceof EntityService<?>) {
                final Class<?> entityType = findParameterizedType(bean.getClass(), Entity.class);
                if (entityType != null && Entity.class.isAssignableFrom(entityType)) {
                    try {
                        final Entity entity = (Entity) entityType.newInstance();
                        final String existing = entityServiceTypeMap.put(entity.getType(), beanName);
                        if (existing != null) {
                            LOGGER.error("Existing bean found for entity type '" + existing + "'");
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }

                    final String existing = entityServiceClassMap.put(entityType, beanName);
                    if (existing != null) {
                        LOGGER.error("Existing bean found for entity type class '" + existing + "'");
                    }
                }

                final Class<?> findType = findParameterizedType(bean.getClass(), BaseCriteria.class);
                if (findType != null) {
                    final String existing = entityServiceClassMap.put(findType, beanName);
                    if (existing != null) {
                        LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
                    }
                }

            } else if (bean instanceof FindService<?, ?>) {
                final Class<?> findType = findParameterizedType(bean.getClass(), BaseCriteria.class);
                if (findType != null) {
                    final String existing = entityServiceClassMap.put(findType, beanName);
                    if (existing != null) {
                        LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
                    }
                }
            }
        }

        return bean;
    }
}
