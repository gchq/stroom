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
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class EntityServiceBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityServiceBeanRegistry.class);
    private final Map<List<Object>, Method> entityServiceMethodMap = new ConcurrentHashMap<>();
    private final Map<String, Provider<Object>> entityServiceByType;
    private final Map<Class<?>, Provider<FindService>> findServiceMap = new HashMap<>();

    @Inject
    EntityServiceBeanRegistry(final Map<String, Provider<Object>> entityServiceByType,
                              final Collection<Provider<FindService>> findServiceProviders) {
        this.entityServiceByType = entityServiceByType;

        findServiceProviders.forEach(findServiceProvider -> {
            final FindService findService = findServiceProvider.get();
            for (final Method method : findService.getClass().getMethods()) {
                if (method.getName().equals("find")) {
                    if (method.getParameterTypes().length == 1) {
                        findServiceMap.put(method.getParameterTypes()[0], findServiceProvider);
                    }
                }
            }
        });
    }

    public Object getEntityServiceByType(final String type) {
        final Provider<Object> serviceProvider = entityServiceByType.get(type);
        if (serviceProvider == null) {
            LOGGER.error("No Service provider found for '" + type + "'");
            return null;
        }

        return serviceProvider.get();
    }

    public FindService getEntityServiceByCriteria(final Class<?> criteriaClazz) {
        final Provider<FindService> serviceProvider = findServiceMap.get(criteriaClazz);
        if (serviceProvider == null) {
            LOGGER.error("No Service provider found for '" + criteriaClazz + "'");
            return null;
        }

        return serviceProvider.get();
    }

    public Object invoke(final Object entityService, final String methodName, final Object... args) {
        Object retVal;

        try {
            if (args == null || args.length == 0) {
                throw new EntityServiceException("At least one argument is expected");
            }
            final Method method = getMethod(entityService.getClass(), methodName, buildArgTypes(args));
            if (method == null) {
                throw new EntityServiceException("No method '" + methodName + "' found on bean '" + entityService + "'");
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
}
