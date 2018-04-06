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

package stroom.importexport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.EncodingUtil;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.Res;
import stroom.entity.util.EntityServiceExceptionUtil;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LegacyXMLSerialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyXMLSerialiser.class);

    @SuppressWarnings("unchecked")
    public <E extends DocumentEntity> void performImport(final E entity, final Map<String, byte[]> dataMap) {
        try {
            final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), false);

            final Config config = new Config();
            config.read(new StringReader(EncodingUtil.asString(dataMap.get("xml"))));

            // Output warnings where the config lists invalid properties.
            for (final String property : config.getProperties()) {
                boolean found = false;
                for (final Property prop : propertyList) {
                    if (prop.getName().equals(property)) {
                        found = true;
                        break;
                    }
                }
                if (DocumentEntity.AUDIT_FIELDS.contains(property)) {
                    found = true;
                }
            }

            // Only try and set valid properties.
            // Start with properties that are not being set from an external
            // file as the internal properties may affect the file extension of
            // the external files.
            for (final Property property : propertyList) {
                final String propertyName = property.getName();

                // Import non externalised properties.
                if (!property.isExternalFile()) {
                    // Set the property if it is specified.
                    if (config.hasProperty(propertyName)) {
                        updateProperty(entity, property, config.get(propertyName));
                    }
                }
            }

            // Now set properties that are held in external files.
            for (final Property property : propertyList) {
                final String propertyName = property.getName();

                // Import the property from an external file if we are expected
                // to.
                if (property.isExternalFile()) {
                    final String fileExtension = property.getExtensionProvider().getExtension(entity, propertyName);
                    final String dataKey = propertyName + "." + fileExtension;
                    final String data = EncodingUtil.asString(dataMap.get(dataKey));
                    if (data != null) {
                        final List<Object> newDataList = new ArrayList<>();
                        newDataList.add(data);
                        updateProperty(entity, property, newDataList);
                    }
                }
            }
        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void updateProperty(final Object object,
                                final Property property,
                                final List<Object> values) {
        try {
            if (BaseEntity.class.isAssignableFrom(property.getType())) {
                Entity entity = (Entity) object;
                Object obj = null;
                if (values != null && values.size() > 0) {
                    obj = values.iterator().next();
                }

                if (obj != null) {
                    if (obj instanceof String) {
                        final String string = (String) obj;
                        if (!string.isEmpty()) {
                            setStringProperty(entity, property, string);
                        }
                    }
                } else {
                    property.set(entity, null);
                }
            } else if (Set.class.isAssignableFrom(property.getType())) {
                final Set<BaseEntity> newSet = new HashSet<>();
                property.set(object, newSet);

            } else {
                // Simple property
                if (values == null || values.size() == 0) {
                    property.set(object, null);
                } else {
                    final String value = values.get(0).toString();

                    if (String.class.equals(property.getType())) {
                        property.set(object, value);
                    } else if (Long.class.equals(property.getType())) {
                        property.set(object, Long.valueOf(value));
                    } else if (Integer.class.equals(property.getType())) {
                        property.set(object, Integer.valueOf(value));
                    } else if (Boolean.class.equals(property.getType())) {
                        property.set(object, Boolean.valueOf(value));
                    } else if (property.getType().isEnum()) {
                        property.set(object, Enum.valueOf((Class<Enum>) property.getType(), value));
                    } else if (property.getType().isPrimitive()) {
                        if (property.getType().getName().equals("boolean")) {
                            property.set(object, Boolean.valueOf(value));
                        } else if (property.getType().getName().equals("int")) {
                            property.set(object, Integer.valueOf(value));
                        } else if (property.getType().getName().equals("long")) {
                            property.set(object, Long.valueOf(value));
                        } else {
                            throw new EntityServiceException("Unexpected property type " + property.getType());
                        }
                    } else {
                        throw new EntityServiceException("Unexpected property type " + property.getType());
                    }
                }
            }
        } catch (final RuntimeException | IllegalAccessException | InvocationTargetException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    private void setStringProperty(final Entity entity,
                                   final Property property,
                                   final String value) throws IllegalAccessException, InvocationTargetException {

        // See if this property is a resource. If it is then create
        // a new resource or update an existing one.
        if (Res.class.equals(property.getType())) {
            Res res;
            final Object existing = property.get(entity);
            if (existing == null) {
                res = new Res();
            } else {
                res = (Res) existing;
            }

            res.setData(value);
            property.set(entity, res);
        }
    }
}