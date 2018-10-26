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

package stroom.importexport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.util.BaseEntityBeanWrapper;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.EntityDependencyServiceException;
import stroom.entity.shared.NamedEntity;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.query.api.v2.DocRef;
import stroom.util.date.DateUtil;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ImportExportHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportHelper.class);

    private final GenericEntityService genericEntityService;

    @Inject
    public ImportExportHelper(final GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

    @SuppressWarnings("unchecked")
    public <E extends DocumentEntity> void performImport(final E entity,
                                                         final Map<String, String> dataMap,
                                                         final ImportState importState,
                                                         final ImportMode importMode) {
        try {
            final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), false);

            final String xml = dataMap.get("xml");
            if (xml != null) {
                final Config config = new Config();
                config.read(new StringReader(xml));

                final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

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

                    if (!found) {
                        importState.addMessage(Severity.WARNING, String.format("%s contains invalid property %s", entity.getType(), property));
                    }
                }

                // Only try and set valid properties.
                // Start with properties that are not being set from an external file as the internal properties may affect the file extension of the external files.
                for (final Property property : propertyList) {
                    final String propertyName = property.getName();

                    // Import non externalised properties.
                    if (!property.isExternalFile()) {
                        // Set the property if it is specified.
                        if (config.hasProperty(propertyName)) {
                            updateProperty(beanWrapper, propertyName, config.get(propertyName), importState);
                        }
                    }
                }

                // Now set properties that are held in external files.
                for (final Property property : propertyList) {
                    final String propertyName = property.getName();

                    // Import the property from an external file if we are expected to.
                    if (property.isExternalFile()) {
                        final String fileExtension = property.getExtensionProvider().getExtension(entity, propertyName);
                        final String dataKey = propertyName + "." + fileExtension;
                        final String data = dataMap.get(dataKey);
                        if (data != null) {
                            final List<Object> newDataList = new ArrayList<>();
                            newDataList.add(data);
                            updateProperty(beanWrapper, propertyName, newDataList, importState);

                        } else {
                            importState.addMessage(Severity.WARNING, String
                                    .format("%s external property data not found %s", entity.getType(), property));

                        }
                    }
                }

                // Did we update anything?
                if (importMode == ImportMode.CREATE_CONFIRMATION
                        && State.UPDATE.equals(importState.getState())) {
                    boolean equal = importState.getUpdatedFieldList().size() == 0;

                    if (equal) {
                        importState.setState(State.EQUAL);
                    } else {
                        final Long originalUpdateTime = entity.getUpdateTime();
                        final String newDateString = config.getString("updateTime");
                        if (newDateString != null) {
                            Long newTime = null;
                            try {
                                newTime = DateUtil.parseNormalDateTimeString(newDateString);
                            } catch (final Exception e) {
                                // Ignore.
                            }
                            try {
                                newTime = Long.valueOf(newDateString);
                            } catch (final Exception e) {
                                // Ignore.
                            }

                            if (originalUpdateTime != null && newTime != null && originalUpdateTime > newTime) {
                                importState.addMessage(Severity.WARNING,
                                        "The item you are attempting to import is older than the current version.\nCurrent is "
                                                + DateUtil.createNormalDateTimeString(originalUpdateTime)
                                                + " (" + entity.getUpdateUser() + "), Import is "
                                                + DateUtil.createNormalDateTimeString(newTime));

                            }
                        }
                    }
                }
            }

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            importState.addMessage(Severity.ERROR, e.getMessage());
        }
    }

    private void updateProperty(final BaseEntityBeanWrapper beanWrapper,
                                final String propertyName,
                                final List<Object> values,
                                final ImportState importState) {
        try {
            final Object existingValue = beanWrapper.getPropertyValue(propertyName);

            if (beanWrapper.isPropertyBaseEntity(propertyName)) {
                Object obj = null;
                if (values != null && values.size() > 0) {
                    obj = values.iterator().next();
                }

                if (obj != null) {
                    if (obj instanceof DocRef) {
                        final DocRef docRef = (DocRef) obj;
                        setDocRefProperty(beanWrapper, propertyName, docRef, importState);
                    }
                } else {
                    // The new property value is null so set it to null.
                    if (existingValue != null) {
                        importState.getUpdatedFieldList().add(propertyName);
                    }
                    beanWrapper.setPropertyValue(propertyName, null);
                }
            } else if (beanWrapper.isPropertyBaseEntitySet(propertyName)) {
                beanWrapper.clearPropertySet(propertyName);

            } else {
                Object newValue = null;
                if (values != null && values.size() > 0) {
                    newValue = values.get(0);
                }

                if (!Objects.equals(existingValue, newValue)) {
                    // Don't rename existing items as they might have been renamed by the user.
                    if (existingValue == null || !propertyName.equals("name")) {
                        importState.getUpdatedFieldList().add(propertyName);

                        if (values == null || values.size() == 0) {
                            // Simple property
                            beanWrapper.setPropertyValue(propertyName, null);
                        } else {
                            // Simple property
                            beanWrapper.setPropertyValue(propertyName, values.get(0));
                        }
                    }
                }
            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void setDocRefProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                   final DocRef docRef, final ImportState importState) {
        // This property is an entity so get the referenced entity if we can.
        final BaseEntity entity = resolveEntityByDocRef(docRef);
        if (!entity.equals(beanWrapper.getPropertyValue(propertyName))) {
            importState.getUpdatedFieldList().add(propertyName);
        }
        beanWrapper.setPropertyValue(propertyName, entity);
    }

    private BaseEntity resolveEntityByDocRef(final DocRef docRef) {
        NamedEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());

        if (entity == null) {
            // If we couldn't find the referenced entity then throw an entity dependency exception. We might get the dependency added later in this import and be able to add this entity again later.
            throw new EntityDependencyServiceException(docRef.getType(), docRef.getUuid());
        }

        return entity;
    }

    public Map<String, String> performExport(final DocumentEntity entity,
                                             final boolean omitAuditFields, final List<Message> messageList) {
        final Map<String, String> dataMap = new HashMap<>();
        final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), omitAuditFields);

        try {
            final Config config = new Config();
            final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

            for (final Property property : propertyList) {
                final String propertyName = property.getName();
                final Object value = beanWrapper.getPropertyValue(propertyName);

                // If the property is supposed to produce an external file then do so.
                if (property.isExternalFile()) {
                    if (value != null) {
                        final String data = String.valueOf(value);
                        if (data != null) {
                            final String fileExtension = property.getExtensionProvider().getExtension(entity,
                                    propertyName);
                            if (fileExtension != null) {
                                dataMap.put(propertyName + "." + fileExtension, data);
                            }
                        }
                    }

                } else {
                    // Otherwise put the property in the exported XML representation of this entity.
                    if (value != null) {
                        if (value instanceof Collection) {
                            final List<Object> list = new ArrayList<>();

                            for (final Object valueItem : (Collection<?>) value) {
                                list.add(String.valueOf(valueItem));
                            }

                            config.add(propertyName, list);

                        } else if (value instanceof DocumentEntity) {
                            try {
                                DocumentEntity documentEntity = (DocumentEntity) value;
                                documentEntity = genericEntityService.load(documentEntity, null);
                                final DocRef docRef = DocRefUtil.create(documentEntity);
                                if (docRef != null) {
                                    config.add(propertyName, docRef);
                                }
                            } catch (final Exception e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                        } else if (value instanceof NamedEntity) {
                            try {
                                final NamedEntity namedEntity = genericEntityService.load((NamedEntity) value);
                                if (namedEntity != null) {
                                    config.add(propertyName, namedEntity.getName());
                                }
                            } catch (final Exception e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                        } else if (value instanceof Date) {
                            config.add(propertyName, DateUtil.createNormalDateTimeString(((Date) value).getTime()));

                        } else {
                            config.add(propertyName, String.valueOf(value));
                        }
                    }
                }
            }

            final StringWriter writer = new StringWriter();
            config.write(writer, entity.getType());
            dataMap.put("xml", writer.toString());

        } catch (final Exception e) {
            messageList.add(new Message(Severity.ERROR, EntityServiceExceptionUtil.getDefaultMessage(e, e)));
        }

        return dataMap;
    }
}