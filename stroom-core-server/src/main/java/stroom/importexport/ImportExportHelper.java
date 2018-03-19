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
import stroom.entity.GenericEntityService;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityDependencyServiceException;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Res;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.importexport.shared.ImportState.State;
import stroom.query.api.v2.DocRef;
import stroom.util.date.DateUtil;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImportExportHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportHelper.class);

    private final Provider<GenericEntityService> genericEntityServiceProvider;
//    private final ClassTypeMap classTypeMap = new ClassTypeMap();
//    private volatile boolean entitiesInitialised = false;

    @Inject
    public ImportExportHelper(final Provider<GenericEntityService> genericEntityServiceProvider) {
        this.genericEntityServiceProvider = genericEntityServiceProvider;
    }

    @SuppressWarnings("unchecked")
    public <E extends DocumentEntity> void performImport(final E entity, final Map<String, String> dataMap,
                                                         final ImportState importState, final ImportMode importMode) {
        try {
//            init();

            final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), false);

            final Config config = new Config();
            config.read(new StringReader(dataMap.get("xml")));

//            final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

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
            // Start with properties that are not being set from an external
            // file as the internal properties may affect the file extension of
            // the external files.
            for (final Property property : propertyList) {
                final String propertyName = property.getName();

                // Import non externalised properties.
                if (!property.isExternalFile()) {
                    // Set the property if it is specified.
                    if (config.hasProperty(propertyName)) {
                        updateProperty(entity, property, config.get(propertyName), importState, importMode);
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
                    final String data = dataMap.get(dataKey);
                    if (data != null) {
                        final List<Object> newDataList = new ArrayList<>();
                        newDataList.add(data);
                        updateProperty(entity, property, newDataList, importState, importMode);

                    } else {
                        importState.addMessage(Severity.WARNING, String
                                .format("%s external property data not found %s", entity.getType(), property));

                    }
                }
            }
//
//            // Unmarshall the object from the external form.
//            try {
//                genericEntityMarshaller.unmarshal(entityType, entity);
//            } catch (final EntityDependencyServiceException dependency) {
//                if (importMode == ImportMode.CREATE_CONFIRMATION) {
//                    if (importState.getEntityAction() != EntityAction.ADD) {
//                        importState.setEntityAction(EntityAction.UPDATE);
//                    }
//                } else {
//                    throw dependency;
//                }
//            }

            // Did we update anything?
            if (importMode == ImportMode.CREATE_CONFIRMATION
                    && State.UPDATE.equals(importState.getState())) {
                boolean equal = importState.getUpdatedFieldList().size() == 0;

                final String newName = config.getString("name");

                if (newName != null && !newName.equals(entity.getName())) {
                    importState.addMessage(Severity.WARNING,
                            "The entity name will be changed on import from '" + entity.getName() + "' to '" + newName + "'");
                    equal = false;
                }

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

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            importState.addMessage(Severity.ERROR, e.getMessage());
        }
    }

    private void updateProperty(final Object object,
                                final Property property,
                                final List<Object> values,
                                final ImportState importState,
                                final ImportMode importMode) {
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
                        if (string != null && !string.isEmpty()) {
                            setStringProperty(entity, property, string, importState, importMode);
                        }
                    } else if (obj instanceof DocRef) {
                        final DocRef docRef = (DocRef) obj;
                        setDocRefProperty(entity, property, docRef, importState, importMode);
                    }
                } else {
                    // The new property value is null so set it to null.
                    if (importMode == ImportMode.CREATE_CONFIRMATION) {
                        final Object value = property.get(entity);
                        if (value != null) {
                            importState.getUpdatedFieldList().add(property.getName());
                        }
                    } else {
                        property.set(entity, null);
                    }
                }
            } else if (Set.class.isAssignableFrom(property.getType())) {
                final Set<BaseEntity> newSet = new HashSet<>();

                if (values != null && values.size() > 0) {
//                    final Class<? extends Entity> clazz = beanWrapper.getPropertyBaseEntityType(propertyName);
//                    final String entityType = classTypeMap.getEntityType(clazz);
//
//                    // Some entity types are not imported, e.g. Volumes so we
//                    // will sometimes get null entity type here if they are
//                    // unsupported.
//                    if (entityType != null) {
//                        for (final Object obj : values) {
//                            if (obj instanceof String) {
//                                final String string = (String) obj;
//                                if (string != null && !string.isEmpty()) {
//                                    final BaseEntity entity = resolveEntityByPath(beanWrapper, clazz, string);
//                                    newSet.add(entity);
//                                }
//                            } else if (obj instanceof DocRef) {
//                                final DocRef docRef = (DocRef) obj;
//                                final BaseEntity entity = resolveEntityByDocRef(beanWrapper, docRef);
//                                newSet.add(entity);
//                            }
//                        }
//                    }
                }
                @SuppressWarnings("unchecked") final Set<? extends DocumentEntity> oldSet = (Set<? extends DocumentEntity>) property.get(object);

                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    if (!newSet.equals(oldSet)) {
                        importState.getUpdatedFieldList().add(property.getName());
                    }
                } else {
                    property.set(object, newSet);
                }

            } else if (importMode == ImportMode.CREATE_CONFIRMATION) {
                if (values == null || values.size() == 0) {
                    if (property.get(object) != null) {
                        importState.getUpdatedFieldList().add(property.getName());
                    }
                } else {
                    // null is like "" from a Stroom XML POV
                    final Object oldValueO = property.get(object);
                    String oldValue = "";
                    if (oldValueO != null) {
                        oldValue = String.valueOf(oldValueO);
                    }
                    final Object newValue = values.get(0);

                    if (!newValue.equals(oldValue)) {
                        importState.getUpdatedFieldList().add(property.getName());
                    }
                }

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
                        property.set(object, Enum.valueOf((Class<Enum>)property.getType(), value));
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
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void setStringProperty(final Entity entity,
                                   final Property property,
                                   final String value,
                                   final ImportState importState,
                                   final ImportMode importMode) throws IllegalAccessException, InvocationTargetException {

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

            if (importMode == ImportMode.CREATE_CONFIRMATION) {
                if (!EqualsUtil.isEquals(res.getData(), value)) {
                    importState.getUpdatedFieldList().add(property.getName());
                }
            } else {
                res.setData(value);
                property.set(entity, res);
            }
        }
    }

    private void setDocRefProperty(final Entity entity,
                                   final Property property,
                                   final DocRef docRef,
                                   final ImportState importState,
                                   final ImportMode importMode) throws IllegalAccessException, InvocationTargetException {
        // This property is an entity so get the referenced
        // entity if we can.
        final BaseEntity value = resolveEntityByDocRef(docRef);
        if (importMode == ImportMode.CREATE_CONFIRMATION) {
            final Object existing = property.get(entity);
            if (!value.equals(existing)) {
                importState.getUpdatedFieldList().add(property.getName());
            }
        }
        property.set(entity, value);
    }

//    private BaseEntity resolveEntityByPath(final BaseEntityBeanWrapper beanWrapper, final Class<? extends Entity> clazz,
//                                           final String val) {
//        NamedEntity entity;
////        if (DocumentEntity.class.isAssignableFrom(clazz)) {
////            entity = entityPathResolver.getEntity(classTypeMap.getEntityType(clazz), beanWrapper.getBaseEntity(), val,
////                    null);
////        } else {
//        entity = genericEntityService.loadByName(classTypeMap.getEntityType(clazz), val);
////        }
//
//        if (entity == null) {
//            // If we couldn't find the referenced entity then throw an entity
//            // dependency exception. We might get the dependency added later in
//            // this import and be able to add this entity again later.
//            throw new EntityDependencyServiceException(classTypeMap.getEntityType(clazz), val);
//        }
//
//        return entity;
//    }

    private BaseEntity resolveEntityByDocRef(final DocRef docRef) {
        final GenericEntityService genericEntityService = genericEntityServiceProvider.get();
        NamedEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());

//        // Try by path if we couldn't find with uuid
//        if (entity == null) {
//            entity = entityPathResolver.getEntity(docRef.getType(), beanWrapper.getBaseEntity(), docRef.getPath(),
//                    null);
//        }

        if (entity == null) {
            // If we couldn't find the referenced entity then throw an entity
            // dependency exception. We might get the dependency added later in
            // this import and be able to add this entity again later.
            throw new EntityDependencyServiceException(docRef.getType(), docRef.getUuid());
        }

        return entity;
    }


//    private String toPath(final Folder folder, final String name) {
//        if (folder != null) {
//            if (name != null) {
//                return entityPathResolver.getEntityPath(Folder.ENTITY_TYPE, null, folder) + "/" + name;
//            } else {
//                return entityPathResolver.getEntityPath(Folder.ENTITY_TYPE, null, folder);
//            }
//        } else {
//            return "/" + name;
//        }
//    }
//
//    private String getDataKey(final String entityName, final String entityType,
//                             final String propertyName, final String fileExtension) {
//        String path = ""
//                + FileSystemUtil.encodeFileName(entityName)
//                + "."
//                + entityType
//                + "."
//                + propertyName
//                + "."
//                + fileExtension;
//        return path;
//    }


    public Map<String, String> performExport(final DocumentEntity entity,
                                             final boolean omitAuditFields, final List<Message> messageList) {
        final Map<String, String> dataMap = new HashMap<>();
        final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), omitAuditFields);

        try {
//            init();

            final Config config = new Config();
            final String name = entity.getName();
//            final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

            for (final Property property : propertyList) {
                final String propertyName = property.getName();
                final Object value = property.get(entity);

                // If the property is supposed to produce an external file then
                // do so.
                if (property.isExternalFile()) {
                    if (value != null) {
                        String data;
                        if (value instanceof Res) {
                            final Res res = (Res) value;
                            data = res.getData();
                        } else {
                            data = String.valueOf(value);
                        }

                        if (data != null) {
                            final String fileExtension = property.getExtensionProvider().getExtension(entity,
                                    propertyName);
                            if (fileExtension != null) {
                                dataMap.put(propertyName + "." + fileExtension, data);
                            }
                        }
                    }

                } else {
                    // Otherwise put the property in the exported XML
                    // representation of this entity.
                    if (value != null) {
                        if (value instanceof Collection) {
                            final List<Object> list = new ArrayList<>();

                            for (final Object valueItem : (Collection<?>) value) {
//                                if (valueItem instanceof DocumentEntity) {
//                                    final DocumentEntity documentEntity = (DocumentEntity) valueItem;
//                                    list.add(entityPathResolver.getEntityPath(documentEntity.getType(), entity,
//                                            documentEntity));
//                                } else {
                                list.add(String.valueOf(valueItem));
//                                }
                            }

                            config.add(propertyName, list);

                        } else if (value instanceof DocumentEntity) {
                            try {
                                DocumentEntity documentEntity = (DocumentEntity) value;
                                final GenericEntityService genericEntityService = genericEntityServiceProvider.get();
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
                                final GenericEntityService genericEntityService = genericEntityServiceProvider.get();
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

    private static class EntityClassComparator implements Comparator<Class<? extends DocumentEntity>> {
        @Override
        public int compare(final Class<? extends DocumentEntity> o1, final Class<? extends DocumentEntity> o2) {
            return o1.getSimpleName().compareTo(o2.getSimpleName());
        }
    }
}