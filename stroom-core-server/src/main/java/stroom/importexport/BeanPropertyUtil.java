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
 */

package stroom.importexport;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.pipeline.shared.ExtensionProvider;

import javax.xml.bind.annotation.XmlTransient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BeanPropertyUtil {
    private static final Map<Key, List<Property>> propertyCache = new ConcurrentHashMap<>();

    private BeanPropertyUtil() {
        // Utility class.
    }

    /**
     * Given a class return all the property names.
     */
    public static List<Property> getPropertyList(final Class<?> clazz, final boolean omitAuditFields) {
        return propertyCache.computeIfAbsent(new Key(clazz, omitAuditFields), BeanPropertyUtil::create);
    }

    private static List<Property> create(final Key key) {
        final Map<String, List<Method>> methodMap = new HashMap<>();

        for (final Method method : key.clazz.getMethods()) {
            if (isGetter(method) || isSetter(method)) {
                final String propertyName = getPropertyName(method);

                final XmlTransient xmlTransient = method.getAnnotation(XmlTransient.class);

                // Ignore transient fields.
                if (xmlTransient == null) {
                    if (!(key.omitAuditFields && DocumentEntity.AUDIT_FIELDS.contains(propertyName))) {
                        methodMap.computeIfAbsent(propertyName, name -> new ArrayList<>()).add(method);
                    }
                }
            }
        }

        final List<Property> list = new ArrayList<>();
        methodMap.forEach((propertyName, methods) -> {
            if (methods.size() >= 2) {
                // Find the get method.
                Method getMethod = null;
                for (final Method method : methods) {
                    if (isGetter(method)) {
                        getMethod = method;
                        break;
                    }
                }

                if (getMethod != null) {
                    // Find the set method.
                    Method setMethod = null;
                    for (final Method method : methods) {
                        if (isSetter(method) && method.getParameterTypes()[0].equals(getMethod.getReturnType())) {
                            setMethod = method;
                            break;
                        }
                    }

                    if (setMethod != null) {
                        Property exportProperty;
                        final ExternalFile externalFile = getMethod.getAnnotation(ExternalFile.class);
                        if (externalFile != null) {
                            final Class<?> extensionProvider = externalFile.extensionProvider();
                            if (ExtensionProvider.class.isAssignableFrom(extensionProvider)) {
                                ExtensionProvider instance;
                                try {
                                    instance = (ExtensionProvider) extensionProvider.getConstructor().newInstance();
                                } catch (final NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                                exportProperty = new Property(propertyName, true, instance, getMethod, setMethod);
                            } else {
                                exportProperty = new Property(propertyName, true, new ExtensionProvider(externalFile.value()), getMethod, setMethod);
                            }
                        } else {
                            exportProperty = new Property(propertyName, false, null, getMethod, setMethod);
                        }

                        list.add(exportProperty);
                    }
                }
            }
        });
        list.sort(Comparator.comparing(Property::getName));

        final Iterator<Property> itr = list.iterator();

        while (itr.hasNext()) {
            final Property next = itr.next();
            final String name = next.getName();
            // Handle private properties Which could be pXxxx or pxxxxx
            if (name.startsWith("p") || name.startsWith("P")) {
                final String other1 = name.substring(1);
                final String other2 = other1.substring(0, 1).toLowerCase() + other1.substring(1);

                boolean found = false;
                for (final Property prop : list) {
                    if (prop.getName().equals(other1) || prop.getName().equals(other2)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    itr.remove();
                }
            }
            if (name.equals("folder")) {
                itr.remove();
            }
        }

        return list;
    }

    private static boolean isGetter(final Method method) {
        final String name = method.getName();
        return ((name.length() > 3 && name.startsWith("get")) ||
                (name.length() > 2 && name.startsWith("is"))) &&
                        method.getParameterTypes().length == 0;
    }

    private static boolean isSetter(final Method method) {
        final String name = method.getName();
        return name.length() > 3 && name.startsWith("set") && method.getParameterTypes().length == 1;
    }

    private static String getPropertyName(final Method method) {
        final String name = method.getName();
        if (name.startsWith("get") || name.startsWith("set")) {
            final String propertyName = name.substring(3);
            return propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
        }
        if (name.startsWith("is")) {
            final String propertyName = name.substring(2);
            return propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
        }
        return null;
    }

    private static class Key {
        private final Class<?> clazz;
        private final boolean omitAuditFields;

        Key(final Class<?> clazz, final boolean omitAuditFields) {
            this.clazz = clazz;
            this.omitAuditFields = omitAuditFields;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Key key = (Key) o;
            return omitAuditFields == key.omitAuditFields &&
                    Objects.equals(clazz, key.clazz);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, omitAuditFields);
        }
    }
}
