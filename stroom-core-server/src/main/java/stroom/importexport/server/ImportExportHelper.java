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
import org.springframework.util.StringUtils;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.util.BaseEntityBeanWrapper;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityDependencyServiceException;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Res;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.date.DateUtil;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ImportExportHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportHelper.class);

    private final GenericEntityService genericEntityService;
    private final ClassTypeMap classTypeMap = new ClassTypeMap();
    private volatile boolean entitiesInitialised = false;

    @Inject
    public ImportExportHelper(final GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

    /**
     * Registers all the entities in the ClassTypeMap if they have not already
     * been registered.
     */
    private void init() {
//        if (!entitiesInitialised) {
//            synchronized (this) {
//                if (!entitiesInitialised) {
//                    registerEntities();
//                    entitiesInitialised = true;
//                }
//            }
//        }
    }

//    /**
//     * Use the spring registered instances of DocumentEntityServiceImpl to work
//     * out which GroupedEntities to register into the ClassTypeMap The order we
//     * register them in is important as some are dependent on others.
//     */
//    private void registerEntities() {
//        // Stream type is a special case so explicitly register it first.
//        classTypeMap.registerEntityReference(StreamType.class);
//
//        // get all the spring grouped entity service beans.
//        final Collection<DocumentEntityService<?>> services = genericEntityService.findAll();
//
//        final ArrayList<Class<? extends DocumentEntity>> entityClasses = new ArrayList<>(services.size());
//        for (final DocumentEntityService<?> service : services) {
//            final Class<? extends DocumentEntity> clazz = service.getEntityClass();
//            if (clazz == null) {
//                throw new NullPointerException("No entity class provided");
//            } else {
//                entityClasses.add(clazz);
//            }
//        }
//
//        // Sort the list of entity classes to ensure consistent behaviour.
//        Collections.sort(entityClasses, new EntityClassComparator());
//        // Make sure folders are first
//        entityClasses.remove(Folder.class);
//        entityClasses.add(0, Folder.class);
//        // Make sure pipelines are last.
//        entityClasses.remove(PipelineEntity.class);
//        entityClasses.add(PipelineEntity.class);
//
//        // Keep repeating the services loop to ensure all dependencies are
//        // loaded.
//        for (int i = 0; i < entityClasses.size(); i++) {
//            // No dependencies and not already registered.
//            entityClasses.stream().filter(entityClass -> classTypeMap.getEntityType(entityClass) == null)
//                    .forEach(classTypeMap::registerEntity);
//        }
//    }

    @SuppressWarnings("unchecked")
    public <E extends DocumentEntity> void performImport(final E entity, final Map<String, String> dataMap,
                                                         final ImportState importState, final ImportMode importMode) {
        try {
            init();

            final List<Property> propertyList = BeanPropertyUtil.getPropertyList(entity.getClass(), false);

            final Config config = new Config();
            config.read(new StringReader(dataMap.get("xml")));

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
            // Start with properties that are not being set from an external
            // file as the internal properties may affect the file extension of
            // the external files.
            for (final Property property : propertyList) {
                final String propertyName = property.getName();

                // Import non externalised properties.
                if (!property.isExternalFile()) {
                    // Set the property if it is specified.
                    if (config.hasProperty(propertyName)) {
                        updateProperty(beanWrapper, propertyName, config.get(propertyName), importState, importMode);
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
                        updateProperty(beanWrapper, propertyName, newDataList, importState, importMode);

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

    private void updateProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                final List<Object> values, final ImportState importState,
                                final ImportMode importMode) {
        try {
            if (beanWrapper.isPropertyBaseEntity(propertyName)) {
                Object obj = null;
                if (values != null && values.size() > 0) {
                    obj = values.iterator().next();
                }

                if (obj != null) {
                    if (obj instanceof String) {
                        final String string = (String) obj;
                        if (StringUtils.hasText(string)) {
                            setStringProperty(beanWrapper, propertyName, string, importState, importMode);
                        }
                    } else if (obj instanceof DocRef) {
                        final DocRef docRef = (DocRef) obj;
                        setDocRefProperty(beanWrapper, propertyName, docRef, importState, importMode);
                    }
                } else {
                    // The new property value is null so set it to null.
                    if (importMode == ImportMode.CREATE_CONFIRMATION) {
                        if (beanWrapper.getPropertyValue(propertyName) != null) {
                            importState.getUpdatedFieldList().add(propertyName);
                        }
                    } else {
                        beanWrapper.setPropertyValue(propertyName, null);
                    }
                }
            } else if (beanWrapper.isPropertyBaseEntitySet(propertyName)) {
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
//                                if (StringUtils.hasText(string)) {
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
                @SuppressWarnings("unchecked") final Set<? extends DocumentEntity> oldSet = (Set<? extends DocumentEntity>) beanWrapper
                        .getPropertyValue(propertyName);

                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    if (!newSet.equals(oldSet)) {
                        importState.getUpdatedFieldList().add(propertyName);
                    }
                } else {
                    beanWrapper.clearPropertySet(propertyName);
                    for (final BaseEntity o : newSet) {
                        beanWrapper.addToPropertySet(propertyName, o);
                    }
                }

            } else if (importMode == ImportMode.CREATE_CONFIRMATION) {
                if (values == null || values.size() == 0) {
                    if (beanWrapper.getPropertyValue(propertyName) != null) {
                        importState.getUpdatedFieldList().add(propertyName);
                    }
                } else {
                    // null is like "" from a Stroom XML POV
                    final Object oldValueO = beanWrapper.getPropertyValue(propertyName);
                    String oldValue = "";
                    if (oldValueO != null) {
                        oldValue = String.valueOf(oldValueO);
                    }
                    final Object newValue = values.get(0);

                    if (!newValue.equals(oldValue)) {
                        importState.getUpdatedFieldList().add(propertyName);
                    }
                }

            } else {
                if (values == null || values.size() == 0) {
                    // Simple property
                    beanWrapper.setPropertyValue(propertyName, null);
                } else {
                    // Simple property
                    beanWrapper.setPropertyValue(propertyName, values.get(0));
                }
            }
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void setStringProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                   final String value, final ImportState importState,
                                   final ImportMode importMode) {
        final Class<? extends Entity> clazz = beanWrapper.getPropertyBaseEntityType(propertyName);

        // See if this property is a resource. If it is then create
        // a new resource or update an existing one.
        if (Res.class.equals(clazz)) {
            Res res;
            final Object existing = beanWrapper.getPropertyValue(propertyName);
            if (existing == null) {
                res = new Res();
            } else {
                res = (Res) existing;
            }

            if (importMode == ImportMode.CREATE_CONFIRMATION) {
                if (!EqualsUtil.isEquals(res.getData(), value)) {
                    importState.getUpdatedFieldList().add(propertyName);
                }
            } else {
                res.setData(value);
                beanWrapper.setPropertyValue(propertyName, res);
            }

        }
//        else {
//            // This property is an entity so get the referenced
//            // entity if we can.
//            final BaseEntity entity = resolveEntityByPath(beanWrapper, clazz, value);
//            if (importMode == ImportMode.CREATE_CONFIRMATION) {
//                if (!entity.equals(beanWrapper.getPropertyValue(propertyName))) {
//                    importState.getUpdatedFieldList().add(propertyName);
//                }
//            }
//            beanWrapper.setPropertyValue(propertyName, entity);
//        }
    }

    private void setDocRefProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                   final DocRef docRef, final ImportState importState,
                                   final ImportMode importMode) {
        // This property is an entity so get the referenced
        // entity if we can.
        final BaseEntity entity = resolveEntityByDocRef(beanWrapper, docRef);
        if (importMode == ImportMode.CREATE_CONFIRMATION) {
            if (!entity.equals(beanWrapper.getPropertyValue(propertyName))) {
                importState.getUpdatedFieldList().add(propertyName);
            }
        }
        beanWrapper.setPropertyValue(propertyName, entity);
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

    private BaseEntity resolveEntityByDocRef(final BaseEntityBeanWrapper beanWrapper, final DocRef docRef) {
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
            init();

            final Config config = new Config();
            final String name = entity.getName();
            final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

            for (final Property property : propertyList) {
                final String propertyName = property.getName();
                final Object value = beanWrapper.getPropertyValue(propertyName);

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

    private static class EntityClassComparator implements Comparator<Class<? extends DocumentEntity>> {
        @Override
        public int compare(final Class<? extends DocumentEntity> o1, final Class<? extends DocumentEntity> o2) {
            return o1.getSimpleName().compareTo(o2.getSimpleName());
        }
    }
}