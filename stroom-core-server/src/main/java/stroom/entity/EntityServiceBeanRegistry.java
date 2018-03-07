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

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.document.DocumentStore;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class EntityServiceBeanRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityServiceBeanRegistry.class);
//    private final Map<Class<?>, Object> entityServiceClassMap = new HashMap<>();
//    private final Map<String, Object> entityServiceTypeMap = new HashMap<>();
    private final Map<List<Object>, Method> entityServiceMethodMap = new ConcurrentHashMap<>();
    private final Map<String, Object> externalDocRefServices = new HashMap<>();

    private final StroomBeanStore beanStore;

    @Inject
    EntityServiceBeanRegistry(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
    }

    //    @Inject
//    EntityServiceBeanRegistry(final Set<EntityService> entityServices,
//                              final Set<FindService> findServices,
//                              final Set<DocumentStore> documentStores) {
//        entityServices.forEach(entityService -> {
//            final Class<?> entityType = entityService.getEntityClass();
//            if (entityType != null && Entity.class.isAssignableFrom(entityType)) {
//                try {
//                    final Entity entity = (Entity) entityType.newInstance();
//                    final Object existing = entityServiceTypeMap.put(entity.getType(), entityService);
//                    if (existing != null) {
//                        LOGGER.error("Existing bean found for entity type '" + existing + "'");
//                    }
//                } catch (final Exception e) {
//                    LOGGER.error(e.getMessage(), e);
//                }
//
//                final Object existing = entityServiceClassMap.put(entityType, entityService);
//                if (existing != null) {
//                    LOGGER.error("Existing bean found for entity type class '" + existing + "'");
//                }
//            }
//
//            final Class<?> findType = findParameterizedType(entityService.getClass(), BaseCriteria.class);
//            if (findType != null) {
//                final Object existing = entityServiceClassMap.put(findType, entityService);
//                if (existing != null) {
//                    LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
//                }
//            }
//        });
//
//
//        findServices.forEach(findService -> {
//            final Class<?> findType = findParameterizedType(findService.getClass(), BaseCriteria.class);
//            if (findType != null) {
//                final Object existing = entityServiceClassMap.put(findType, findService);
//                if (existing != null) {
//                    LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
//                }
//            }
//        });
//
//        documentStores.forEach(documentStore -> {
//            final Object existing = entityServiceTypeMap.put(documentStore.getDocType(), documentStore);
//            if (existing != null) {
//                LOGGER.error("Existing bean found for entity find type class '" + existing + "'");
//            }
//        });
//    }

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
        return beanStore.getBean(clazz);

//        final Binding<?> binding = injector.getBinding(clazz);
//        if (binding != null) {
//            return binding.getProvider().get();
//        }
//
//        throw new EntityServiceException("No bean name found for " + clazz.getSimpleName());
    }

    public Object getEntityService(final String entityType) {
        return injector.getInstance(Key.get(String.class, Names.named(entityType)));
//        if (externalDocRefServices.containsKey(entityType)) {
//            return externalDocRefServices.get(entityType);
//        } else {
//            return entityServiceTypeMap.get(entityType);
//        }
    }

    public Object invoke(final String methodName, final Object... args) {
        Object retVal;

        try {
            if (args == null || args.length == 0) {
                throw new EntityServiceException("At least one argument is expected");
            }

            final Object obj = args[0];
            final Class<?> clazz = obj.getClass();
            final String beanName = getEntityType(clazz, clazz);
            if (beanName == null) {
                throw new EntityServiceException("No bean name found for " + clazz.getSimpleName());
            }

            final Object entityService = getEntityService(beanName);
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

    private String getEntityType(final Class<?> baseClass, final Class<?> entityClass) {
        if (entityClass == null) {
            throw new EntityServiceException("Unknown handler for " + baseClass.getName(), null, false);
        }
        final Object entityService = injector.getInstance(entityClass);
        if (entityService != null) {
            if (entityService instanceof EntityService) {
                return ((EntityService) entityService).getEntityType();
            }
            if (entityService instanceof DocumentStore) {
                return ((DocumentStore) entityService).getDocType();
            }
        }

        return null;
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
}
