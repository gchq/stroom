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

package stroom.importexport.server;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.pipeline.shared.ExtensionProvider;

import javax.xml.bind.annotation.XmlTransient;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class BeanPropertyUtil {
    private BeanPropertyUtil() {
        // Utility class.
    }

    /**
     * Given a class return all the property names.
     */
    public static List<Property> getPropertyList(final Class<?> clazz, final boolean omitAuditFields) {
        final ArrayList<Property> list = new ArrayList<>();

        PropertyDescriptor[] props;
        try {
            props = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
        } catch (final IntrospectionException e) {
            throw new RuntimeException(e);
        }
        for (final PropertyDescriptor prop : props) {
            final Method readMethod = prop.getReadMethod();
            final Method writeMethod = prop.getWriteMethod();

            if (readMethod != null && writeMethod != null && readMethod.getParameterTypes().length == 0) {
                final String propertyName = prop.getName();
                final XmlTransient xmlTransient = readMethod.getAnnotation(XmlTransient.class);

                // Ignore transient fields.
                if (xmlTransient == null) {
                    if (!(omitAuditFields && DocumentEntity.AUDIT_FIELDS.contains(propertyName))) {
                        Property exportProperty;
                        final ExternalFile externalFile = prop.getReadMethod().getAnnotation(ExternalFile.class);
                        if (externalFile != null) {
                            final Class<?> extensionProvider = externalFile.extensionProvider();
                            if (ExtensionProvider.class.isAssignableFrom(extensionProvider)) {
                                ExtensionProvider instance;
                                try {
                                    instance = (ExtensionProvider) extensionProvider.newInstance();
                                } catch (final Exception e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                                exportProperty = new Property(propertyName, true, instance);
                            } else {
                                exportProperty = new Property(propertyName, true,
                                        new ExtensionProvider(externalFile.value()));
                            }
                        } else {
                            exportProperty = new Property(propertyName, false, null);
                        }

                        list.add(exportProperty);
                    }
                }
            }
        }

        Collections.sort(list, new Property.NameComparator());

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
}
