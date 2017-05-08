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

package stroom.entity.server.util;

import org.hibernate.Hibernate;
import org.hibernate.proxy.AbstractLazyInitializer;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.HasEntity;
import stroom.entity.shared.HasPassword;
import stroom.util.shared.SharedObject;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Class to DE-PROXY a class and it's children and avoid cyclic relationships
 * (we have not yet implemented this).
 *
 * We rely here on some conventions in the domain objects.
 *
 * If we find any non simple POJO types we do the following: - PersistentSet
 * converts to a POJO version - HibernateProxy we convert to our own PROXY with
 * just the id set (as these is a lazy loaded class)
 */
public class BaseEntityDeProxyProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseEntityDeProxyProcessor.class);

    private final HashMap<BaseEntity, BaseEntity> objectsDone = new HashMap<>();

    private final boolean incoming;
    private static final String PASSWORD_MASK = "********************";

    public BaseEntityDeProxyProcessor(final boolean incoming) {
        this.incoming = incoming;
    }

    /**
     * Take a object and deproxy it.
     */
    public Object process(final Object source) {
        try {
            return doProcess(source);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isLazy(final Object target) {
        if (target != null) {
            return !Hibernate.isInitialized(target);
        }
        return false;
    }

    /**
     * Do the work of processing the object and it's children.
     */
    private Object doProcess(final Object source)
            throws IntrospectionException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Object target = source;

        // Walk unless we say otherwise
        boolean walk = true;

        // Convert all collections to standard collection types.
        if (source instanceof Collection || source instanceof Map) {
            target = replaceCollectionType(source);
        }

        // Are we dealing with a proxy?
        if (target instanceof HibernateProxy) {
            if (!(target instanceof BaseEntity)) {
                throw new RuntimeException("We are expecting all lazy objects to be BaseEntities");
            }
            // Return back our own proxy for the hibernate one that just returns
            // the id. We create a real base entity with just the ID set. The
            // client an then choose to load this up using the loadXYZ API's on
            // the service layer
            BaseEntity replacement = null;
            try {
                final HibernateProxy hibernateProxy = (HibernateProxy) target;
                final LazyInitializer lazyInitializer = hibernateProxy.getHibernateLazyInitializer();
                if (lazyInitializer != null) {
                    if (lazyInitializer instanceof AbstractLazyInitializer) {
                        final AbstractLazyInitializer abstractLazyInitializer = (AbstractLazyInitializer) lazyInitializer;
                        if (!abstractLazyInitializer.isUninitialized()) {
                            replacement = (BaseEntity) abstractLazyInitializer.getImplementation();
                        }
                    }

                    if (replacement == null) {
                        final String entityName = lazyInitializer.getEntityName();
                        final Class<?> clazz = Class.forName(entityName);
                        replacement = (BaseEntity) clazz.newInstance();
                        final long lazyId = ((BaseEntity) source).getId();
                        replacement.setStub(lazyId);

                        // No Point in walking the child objects.
                        walk = false;
                    }
                }
            } catch (final ClassNotFoundException e) {
                LOGGER.error("Unable to get proxy!", e);
            }

            target = replacement;
        }

        if (target instanceof HasEntity<?>) {
            final HasEntity<?> entityRow = (HasEntity<?>) target;

            if (entityRow.getEntity() != null) {
                doProcess(entityRow.getEntity());
            }
        } else {
            // Only walk children of base entities
            if (walk) {
                if (target instanceof BaseEntity) {
                    final BaseEntity child = (BaseEntity) target;
                    final BaseEntity objectDone = objectsDone.get(child);

                    // Here is check if we are on a node that we have already
                    // processed (but it was not a JPA PROXY thus a STUB)
                    if (objectDone != null && !objectDone.isStub()) {
                        // Already done this one and it was not a stub
                        target = objectsDone.get(child);

                    } else {
                        // If is was a stub replace with the real thing
                        objectsDone.put(child, child);

                        // One last check that we don't walk our dummy entities
                        // We know this as they will have the id set but not the
                        // version
                        if (!child.isStub()) {
                            // Now Walk the fields of the class.
                            walkChildren(child);
                        }
                    }
                } else if (target instanceof SharedObject) {
                    // Now Walk the fields of the class.... some kind of front
                    // end holder
                    walkChildren(target);
                }
            }
        }

        if (target instanceof HasPassword) {
            final HasPassword hasPassword = (HasPassword) target;
            if (hasPassword.isPassword()) {
                if (incoming) {
                    // Password not changed!
                    if (PASSWORD_MASK.equals(hasPassword.getValue())) {
                        throw new EntityServiceException("Password not set");
                    }
                } else {
                    // Outgoing
                    hasPassword.setValue(PASSWORD_MASK);
                }
            }
        }

        return target;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object replaceCollectionType(final Object source)
            throws IntrospectionException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Object target = null;

        // Are we dealing with a list?
        if (source instanceof List) {
            if (source instanceof SharedObject) {
                final List<Object> copy = new ArrayList((Collection) source);
                target = source;
                ((Collection) target).clear();
                convertCollection((Collection) copy, (Collection) target);
            } else if (!source.getClass().equals(ArrayList.class) && isLazy(source)) {
                target = new ArrayList(0);
            } else {
                target = new ArrayList(((Collection) source).size());
                convertCollection((Collection) source, (Collection) target);
            }

            // Are we dealing with a set?
        } else if (source instanceof Set) {
            if (source instanceof SharedObject) {
                final List<Object> copy = new ArrayList((Collection) source);
                target = source;
                ((Collection) target).clear();
                convertCollection((Collection) copy, (Collection) target);
            } else if (!source.getClass().equals(HashSet.class) && isLazy(source)) {
                target = new HashSet(0);
            } else {
                target = new HashSet();
                convertCollection((Collection) source, (Collection) target);
            }

            // Are we dealing with a map?
        } else if (source instanceof Map) {
            if (source instanceof SharedObject) {
                final Map<Object, Object> copy = new HashMap((Map) source);
                target = source;
                ((Map) target).clear();
                convertMap((Map) copy, (Map) target);
            } else if (!source.getClass().equals(HashMap.class) && isLazy(source)) {
                target = new HashMap(0);
            } else {
                target = new HashMap();
                convertMap((Map) source, (Map) target);
            }
        }

        return target;
    }

    private void convertCollection(final Collection<Object> source, final Collection<Object> target)
            throws IntrospectionException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (final Object value : source) {
            final Object newValue = doProcess(value);
            target.add(newValue);
        }
    }

    private void convertMap(final Map<Object, Object> source, final Map<Object, Object> target)
            throws IntrospectionException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (final Entry<Object, Object> entry : source.entrySet()) {
            final Object key = doProcess(entry.getKey());
            final Object value = doProcess(entry.getValue());
            target.put(key, value);
        }
    }

    /**
     * Loop around all our child properties looking for one to convert
     */
    private void walkChildren(final Object target)
            throws IntrospectionException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final PropertyDescriptor[] fields = Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors();
        for (final PropertyDescriptor field : fields) {
            // We can only do anything with readable properties
            if (field.getReadMethod() != null && field.getReadMethod().getParameterTypes().length == 0) {
                try {
                    // Only process writable properties
                    if (field.getWriteMethod() != null) {
                        final Object oldValue = field.getReadMethod().invoke(target);
                        final Object newValue = doProcess(oldValue);
                        // Only write if we changed the value
                        if (oldValue != newValue) {
                            field.getWriteMethod().invoke(target, newValue);
                        }
                    }
                } catch (final InvocationTargetException itex) {
                    LOGGER.error("Failed to process field " + field.getName(), itex);
                    throw itex;
                }
            }
        }
    }
}
