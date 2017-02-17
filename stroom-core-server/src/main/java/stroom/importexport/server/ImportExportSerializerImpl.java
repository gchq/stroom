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

package stroom.importexport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2Impl;
import stroom.entity.server.GenericEntityMarshaller;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.util.BaseEntityBeanWrapper;
import stroom.entity.server.util.BaseEntityUtil;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.server.util.XMLUtil;
import stroom.entity.shared.BaseEntity;
import stroom.query.api.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityActionConfirmation;
import stroom.entity.shared.EntityDependencyServiceException;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.Res;
import stroom.pipeline.shared.ExtensionProvider;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Component
public class ImportExportSerializerImpl implements ImportExportSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportSerializerImpl.class);

    private static final Attributes2Impl BLANK_ATTRIBUTES = new Attributes2Impl();
    private static final Set<String> RESOURCE_FETCH_SET = Collections.singleton("all");
    private static final String YES = "yes";
    private final FolderService folderService;
    private final EntityPathResolver entityPathResolver;
    private final GenericEntityService genericEntityService;
    private final GenericEntityMarshaller genericEntityMarshaller;
    private final ClassTypeMap classTypeMap = new ClassTypeMap();
    private volatile boolean entitiesInitialised = false;

    @Inject
    public ImportExportSerializerImpl(final FolderService folderService, final EntityPathResolver entityPathResolver,
                                      final GenericEntityService genericEntityService, final GenericEntityMarshaller genericEntityMarshaller) {
        this.folderService = folderService;
        this.entityPathResolver = entityPathResolver;
        this.genericEntityService = genericEntityService;
        this.genericEntityMarshaller = genericEntityMarshaller;
    }

    /**
     * Registers all the entities in the ClassTypeMap if they have not already
     * been registered.
     */
    private void init() {
        if (!entitiesInitialised) {
            synchronized (this) {
                if (!entitiesInitialised) {
                    registerEntities();
                    entitiesInitialised = true;
                }
            }
        }
    }

    /**
     * Use the spring registered instances of DocumentEntityServiceImpl to work
     * out which GroupedEntities to register into the ClassTypeMap The order we
     * register them in is important as some are dependent on others.
     */
    private void registerEntities() {
        // Stream type is a special case so explicitly register it first.
        classTypeMap.registerEntityReference(StreamType.class);

        // get all the spring grouped entity service beans.
        final Collection<DocumentEntityService<?>> services = genericEntityService.findAll();

        final ArrayList<Class<? extends DocumentEntity>> entityClasses = new ArrayList<>(services.size());
        for (final DocumentEntityService<?> service : services) {
            final Class<? extends DocumentEntity> clazz = service.getEntityClass();
            if (clazz == null) {
                throw new NullPointerException("No entity class provided");
            } else {
                entityClasses.add(clazz);
            }
        }

        // Sort the list of entity classes to ensure consistent behaviour.
        Collections.sort(entityClasses, new EntityClassComparator());
        // Make sure folders are first
        entityClasses.remove(Folder.class);
        entityClasses.add(0, Folder.class);
        // Make sure pipelines are last.
        entityClasses.remove(PipelineEntity.class);
        entityClasses.add(PipelineEntity.class);

        // Keep repeating the services loop to ensure all dependencies are
        // loaded.
        for (int i = 0; i < entityClasses.size(); i++) {
            // No dependencies and not already registered.
            entityClasses.stream().filter(entityClass -> classTypeMap.getEntityType(entityClass) == null)
                    .forEach(classTypeMap::registerEntity);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void read(final File dir, List<EntityActionConfirmation> entityActionConfirmationList,
                     final ImportMode importMode) {
        init();

        if (importMode == ImportMode.IGNORE_CONFIRMATION) {
            entityActionConfirmationList = new ArrayList<>();
        }

        // Key the actionConfirmation's by their key
        final Map<String, EntityActionConfirmation> map = new HashMap<>();
        for (final EntityActionConfirmation actionConfirmation : entityActionConfirmationList) {
            map.put(toUniquePath(actionConfirmation), actionConfirmation);
        }

        // Import
        for (final String entityType : classTypeMap.getEntityTypeList()) {
            performImport(dir, (Class<? extends DocumentEntity>) classTypeMap.getEntityClass(entityType), entityType,
                    map, importMode);
        }

        // Rebuild the list
        entityActionConfirmationList.clear();
        entityActionConfirmationList.addAll(map.values());
        Collections.sort(entityActionConfirmationList, (o1, o2) -> {
            final int r = ModelStringUtil.pathComparator().compare(o1.getPath(), o2.getPath());
            if (r == 0) {
                return classTypeMap.getEntityPriority(o1.getEntityType())
                        .compareTo(classTypeMap.getEntityPriority(o2.getEntityType()));
            } else {
                return r;
            }
        });
    }

    @Override
    public void write(final File dir, final FindFolderCriteria findFolderCriteria, final boolean omitAuditFields,
                      final boolean ignoreErrors, final List<String> messageList) {
        init();

        final EntityIdSet<Folder> entityIdSet = folderService
                .buildNestedFolderList(findFolderCriteria.getFolderIdSet());
        // Export
        for (final String entityType : classTypeMap.getEntityTypeList()) {
            performExport(dir, entityIdSet, entityType, omitAuditFields, ignoreErrors, messageList);
        }
    }

    /**
     * Import a config type
     */
    public <E extends DocumentEntity> void performImport(final File dir, final Class<E> entityClass,
                                                         final String entityType, final Map<String, EntityActionConfirmation> confirmMap,
                                                         final ImportMode importMode) {
        init();

        if (!dir.isDirectory()) {
            throw new EntityServiceException("Dir \"" + dir.getAbsolutePath() + "\" not found");
        }

        final List<Property> propertyList = getPropertyList(entityClass, true);
        performImport(dir, null, entityClass, entityType, propertyList, confirmMap, importMode);
    }

    private void performExport(final File dir, final EntityIdSet<Folder> entityIdSet, final String entityType,
                               final boolean omitAuditFields, final boolean ignoreErrors, final List<String> messageList) {
        @SuppressWarnings("unchecked")
        final Class<? extends DocumentEntity> entityClass = (Class<? extends DocumentEntity>) classTypeMap
                .getEntityClass(entityType);
        final List<Property> propertyList = getPropertyList(entityClass, omitAuditFields);
        performExport(dir, entityIdSet, null, entityClass, entityType, propertyList, ignoreErrors, messageList);
    }

    private void getMatchingFiles(final File dir, final String parentPath, final String matchEndName, final List<String> paths) {
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    getMatchingFiles(file, parentPath + file.getName() + "/", matchEndName, paths);
                } else {
                    final String name = file.getName();
                    if (name.endsWith(matchEndName)) {
                        paths.add(parentPath + file.getName());

                    }
                }
            }
        }
    }

    private <E extends DocumentEntity> void performImport(final File dir, final Folder rootGroup,
                                                          final Class<E> entityClass, final String entityType, final List<Property> propertyList,
                                                          final Map<String, EntityActionConfirmation> confirmMap, final ImportMode importMode) {
        // Look for matching files
        final String matchEndName = "." + entityType + ".xml";

        final List<String> paths = new ArrayList<>();
        getMatchingFiles(dir, "", matchEndName, paths);

        // Now try and import all of the matching paths until we fail to import
        // any more. We do this because some items we are importing may
        // reference other items that haven't been imported yet such as
        // pipelines that reference other pipelines.
        int lastSize = 0;
        final Map<String, EntityDependencyServiceException> exceptions = new HashMap<>();
        while (paths.size() > 0 && lastSize != paths.size()) {
            lastSize = paths.size();

            // For each attempt clear exceptions.
            exceptions.clear();

            // Try and import each entity.
            for (int i = paths.size() - 1; i >= 0; i--) {
                final String path = paths.get(i);
                final File file = new File(dir, path);

                String name = file.getName();
                name = FileSystemUtil.decodeFileName(name.substring(0, name.indexOf(matchEndName)));

                // Get parent folder.
                Folder parentFolder = rootGroup;
                final int index = path.lastIndexOf("/");
                if (index != -1) {
                    final String folderPath = path.substring(0, index);
                    final String[] folderNames = folderPath.split("/");
                    for (final String folderName : folderNames) {
                        Folder folder = folderService.loadByName(DocRefUtil.create(parentFolder), folderName);
                        // Stub it out.
                        if (folder == null) {
                            folder = new Folder();
                            folder.setFolder(parentFolder);
                            folder.setName(folderName);
                        }

                        parentFolder = folder;
                    }
                }

                try {
                    performImport(file, parentFolder, entityClass, entityType, propertyList, name, confirmMap,
                            importMode);
                    paths.remove(i);
                } catch (final EntityDependencyServiceException e) {
                    LOGGER.warn("performImport() - {}", e.getMessage());
                    exceptions.put(path, e);

                    // If it was a dependency not of the same type then this
                    // will never be resolved.
                    if (!e.getMissingEntityType().equals(entityType)) {
                        throw e;
                    }
                }
            }
        }

        // Output the error if we failed to import all entities.
        if (paths.size() > 0) {
            // Try and output all exceptions that have been recorded.
            if (exceptions.size() > 0) {
                final StringBuilder sb = new StringBuilder();
                for (final Entry<String, EntityDependencyServiceException> entry : exceptions.entrySet()) {
                    sb.append("Unable to import '");
                    sb.append(entry.getKey());
                    sb.append("' cause: ");
                    sb.append(entry.getValue().getMessage());
                    sb.append("\n");
                }
                sb.setLength(sb.length() - 1);

                throw new EntityDependencyServiceException(sb.toString());
            }

            throw new EntityDependencyServiceException(entityType, paths.toString());
        }
    }

    private <E extends DocumentEntity> void performExport(final File dir, final EntityIdSet<Folder> entityIdSet,
                                                          final Folder folder, final Class<E> entityClass, final String entityType, final List<Property> propertyList,
                                                          final boolean ignoreErrors, final List<String> messageList) {
        init();

        doPerformExport(dir, entityIdSet, folder, entityClass, entityType, propertyList, ignoreErrors, messageList);
    }

    private <E extends DocumentEntity> boolean doPerformExport(final File dir, final EntityIdSet<Folder> entityIdSet,
                                                               final Folder folder, final Class<E> entityClass, final String entityType, final List<Property> propertyList,
                                                               final boolean ignoreErrors, final List<String> messageList) {
        final Set<Folder> exportedFolders = new HashSet<>();
        boolean exportedSomething = false;

        // OK to export?
        if (entityClass.equals(Folder.class)) {
            // If it's the folder we query them and then check that they
            // are in the set to export.
            final FindFolderCriteria findFolderCriteria = new FindFolderCriteria();
            findFolderCriteria.getFolderIdSet().setDeep(false);
            if (folder == null) {
                findFolderCriteria.getFolderIdSet().setMatchNull(true);
            } else {
                findFolderCriteria.getFolderIdSet().add(folder);
            }

            final List<Folder> list = folderService.find(findFolderCriteria);

            for (final Folder entity : list) {
                if (entityIdSet.isMatch(entity.getId())) {
                    performExport(dir, folder, Folder.class, entityType, propertyList, entity, ignoreErrors,
                            messageList);
                    exportedFolders.add(entity);
                    exportedSomething = true;
                }
            }
        } else {
            // Otherwise just check the parent is in the group we are supposed
            // to export.
            if (folder != null && entityIdSet.isMatch(folder.getId())) {
                try {
                    final List<E> list = genericEntityService.findByFolder(entityType, DocRefUtil.create(folder), RESOURCE_FETCH_SET);

                    for (final E entity : list) {
                        performExport(dir, folder, entityClass, entityType, propertyList, entity, ignoreErrors,
                                messageList);
                        exportedSomething = true;
                    }
                } catch (final Exception ex) {
                    if (ignoreErrors) {
                        messageList.add(EntityServiceExceptionUtil.getDefaultMessage(ex, ex));
                    } else {
                        throw EntityServiceExceptionUtil.create(ex);
                    }
                }
            }
        }

        // Now step into sub folders
        final FindFolderCriteria findFolderCriteria = new FindFolderCriteria();
        findFolderCriteria.getFolderIdSet().setDeep(false);
        if (folder == null) {
            findFolderCriteria.getFolderIdSet().setMatchNull(true);
        } else {
            findFolderCriteria.getFolderIdSet().add(folder);
        }

        final List<Folder> childFolders = folderService.find(findFolderCriteria);
        for (final Folder childFolder : childFolders) {
            final File childDir = new File(dir, childFolder.getName());
            FileSystemUtil.mkdirs(dir, childDir);
            if (doPerformExport(childDir, entityIdSet, childFolder, entityClass, entityType, propertyList, ignoreErrors,
                    messageList)) {
                // Exported something from one of these sub folders.... have we
                // done the parent?
                if (entityClass.equals(Folder.class)) {
                    if (!exportedFolders.contains(childFolder)) {
                        // We must do this sub group
                        performExport(dir, folder, Folder.class, entityType, propertyList, childFolder, ignoreErrors,
                                messageList);
                        exportedFolders.add(childFolder);
                    }
                    exportedSomething = true;
                }
            }
        }
        return exportedSomething;

    }

    private <E extends DocumentEntity> void performExport(final File dir, final Folder folder,
                                                          final Class<E> entityClass, final String entityType, final List<Property> propertyList, final E entity,
                                                          final boolean ignoreErrors, final List<String> messageList) {
        // Only export this item if the user has permission to do so.
        final E toExport = genericEntityService.exportEntity(entity);
        if (toExport == null) {
            return;
        }

        init();

        LOGGER.info("performExport() - {} {}", entityType, entity);
        try {
            final String name = entity.getName();
            final FileOutputStream fileOutputStream = new FileOutputStream(getXMLFile(dir, entityType, name));

            final SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactory.newInstance();
            final TransformerHandler th = stf.newTransformerHandler();
            final Transformer transformer = th.getTransformer();

            fileOutputStream.write("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            XMLUtil.setCommonOutputProperties(transformer, true);
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, YES);

            th.setResult(new StreamResult(fileOutputStream));
            th.startDocument();

            final String xmlName = XMLUtil.toXMLName(entityType);

            writeStartElement(th, xmlName);

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
                                StreamUtil.stringToFile(data,
                                        getDataFile(dir, name, entityType, propertyName, fileExtension));
                            }
                        }
                    }

                } else {
                    // Otherwise put the property in the exported XML
                    // representation of this entity.
                    if (value != null) {
                        if (value instanceof Collection) {
                            for (final Object valueItem : (Collection<?>) value) {
                                if (valueItem instanceof DocumentEntity) {
                                    final DocumentEntity documentEntity = (DocumentEntity) valueItem;

                                    writeStartElement(th, propertyName);
                                    writeContent(th, entityPathResolver.getEntityPath(documentEntity.getType(), entity,
                                            documentEntity));
                                    writeEndElement(th, propertyName);
                                } else {
                                    writeStartElement(th, propertyName);
                                    writeContent(th, String.valueOf(valueItem));
                                    writeEndElement(th, propertyName);
                                }
                            }
                        } else if (value instanceof DocumentEntity) {
                            DocumentEntity documentEntity = (DocumentEntity) value;
                            documentEntity = genericEntityService.load(documentEntity, null);

                            writeStartElement(th, propertyName);

                            writeStartElement(th, "doc");
                            writeStartElement(th, "type");
                            writeContent(th, documentEntity.getType());
                            writeEndElement(th, "type");
                            writeStartElement(th, "uuid");
                            writeContent(th, documentEntity.getUuid());
                            writeEndElement(th, "uuid");
                            writeStartElement(th, "name");
                            writeContent(th, documentEntity.getName());
                            writeEndElement(th, "name");
                            writeStartElement(th, "path");
                            writeContent(th, entityPathResolver.getEntityPath(documentEntity.getType(), entity, documentEntity));
                            writeEndElement(th, "path");
                            writeEndElement(th, "doc");

                            writeEndElement(th, propertyName);
                        } else if (value instanceof NamedEntity) {
                            final NamedEntity namedEntity = genericEntityService.load((NamedEntity) value);

                            writeStartElement(th, propertyName);
                            writeContent(th, namedEntity.getName());
                            writeEndElement(th, propertyName);
                        } else if (value instanceof Date) {
                            writeStartElement(th, propertyName);
                            writeContent(th, DateUtil.createNormalDateTimeString(((Date) value).getTime()));
                            writeEndElement(th, propertyName);

                        } else {
                            writeStartElement(th, propertyName);
                            writeContent(th, String.valueOf(value));
                            writeEndElement(th, propertyName);
                        }
                    }
//                    else {
//                        // Null
//                        writeStartElement(th, propertyName);
//                        writeEndElement(th, propertyName);
//                    }
                }
            }

            writeEndElement(th, xmlName);
            th.endDocument();

            fileOutputStream.close();

        } catch (final Exception ex) {
            if (ignoreErrors) {
                messageList.add(EntityServiceExceptionUtil.getDefaultMessage(ex, ex));
            } else {
                throw EntityServiceExceptionUtil.create(ex);
            }
        }
    }

    private File getXMLFile(final File dir, final String entityType, final String name) {
        return new File(dir, FileSystemUtil.encodeFileName(name) + "." + entityType + ".xml");
    }

    private File getDataFile(final File dir, final String entityName, final String entityType,
                             final String propertyName, final String fileExtension) {
        String path = ""
                + FileSystemUtil.encodeFileName(entityName)
                + "."
                + entityType
                + "."
                + propertyName
                + "."
                + fileExtension;
        return new File(dir, path);
    }

    @SuppressWarnings("unchecked")
    private <E extends DocumentEntity> void performImport(final File file, final Folder folder,
                                                          final Class<E> entityClass, final String entityType, final List<Property> propertyList, final String name,
                                                          final Map<String, EntityActionConfirmation> confirmMap, final ImportMode importMode) {
        init();

        LOGGER.info("performImport() - {} {}", entityType, file);

        try {
            final String entityActionPath = toPath(folder, name);
            final String confirmMapPath = toUniquePath(entityActionPath, entityType);
            EntityActionConfirmation entityActionConfirmation = confirmMap.get(confirmMapPath);
            if (entityActionConfirmation == null) {
                entityActionConfirmation = new EntityActionConfirmation();
                entityActionConfirmation.setPath(entityActionPath);
                entityActionConfirmation.setEntityType(entityType);
                confirmMap.put(confirmMapPath, entityActionConfirmation);
            }

            E entity = entityPathResolver.getEntity(entityType, folder, name, RESOURCE_FETCH_SET);

            if (entity == null) {
                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    entityActionConfirmation.setEntityAction(EntityAction.ADD);
                    // We are adding an item ... no point checking anything else
                    return;
                }
                entity = BaseEntityUtil.newInstance(entityClass);
                entity.setFolder(folder);
            } else {
                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    entityActionConfirmation.setEntityAction(EntityAction.UPDATE);
                }
            }
            if (importMode == ImportMode.ACTION_CONFIRMATION) {
                if (!entityActionConfirmation.isAction()) {
                    return;
                }
            }

            if (entity.getFolder() != null && !entity.getFolder().isPersistent()) {
                throw new EntityDependencyServiceException(Folder.ENTITY_TYPE, toPath(entity.getFolder(), null));
            }

            final BaseEntityBeanWrapper beanWrapper = new BaseEntityBeanWrapper(entity);

            final FileInputStream fileInputStream = new FileInputStream(file);
            final HashMap<String, List<Object>> propertyValues = new HashMap<>();

            final SAXParser parser = XMLUtil.PARSER_FACTORY.newSAXParser();
            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setContentHandler(new ImportContentHandler() {
                @Override
                void handleAttribute(final String property, final Object value) {
                    List<Object> values = propertyValues.get(property);
                    if (values == null) {
                        values = new ArrayList<>();
                        propertyValues.put(property, values);
                    }
                    values.add(value);
                }
            });
            xmlReader.parse(new InputSource(new InputStreamReader(fileInputStream, StreamUtil.DEFAULT_CHARSET)));

            // Output warnings where the config lists invalid properties.
            for (final String property : propertyValues.keySet()) {
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
                    entityActionConfirmation.setWarning(true);
                    entityActionConfirmation.getMessageList()
                            .add(String.format("%s %s contains invalid property %s", entityType, name, property));
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
                    if (propertyValues.containsKey(propertyName)) {
                        updateProperty(beanWrapper, propertyName, propertyValues.get(propertyName),
                                entityActionConfirmation, importMode, file, name);
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
                    final File dataFile = getDataFile(file.getParentFile(), name, entityType, propertyName,
                            fileExtension);
                    if (dataFile.isFile()) {
                        final FileInputStream dataFileInputStream = new FileInputStream(dataFile);
                        final String newData = StreamUtil.streamToString(dataFileInputStream);

                        final List<Object> newDataList = new ArrayList<>();
                        newDataList.add(newData);
                        updateProperty(beanWrapper, propertyName, newDataList, entityActionConfirmation, importMode,
                                file, name);

                    } else {
                        entityActionConfirmation.setWarning(true);
                        entityActionConfirmation.getMessageList().add(String
                                .format("%s %s external property data file not found %s", entityType, name, property));

                    }
                }
            }

            // Unmarshall the object from the external form.
            try {
                genericEntityMarshaller.unmarshal(entityType, entity);
            } catch (final EntityDependencyServiceException dependency) {
                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    if (entityActionConfirmation.getEntityAction() != EntityAction.ADD) {
                        entityActionConfirmation.setEntityAction(EntityAction.UPDATE);
                    }
                } else {
                    throw dependency;
                }
            }

            // Did we update anything?
            if (importMode == ImportMode.CREATE_CONFIRMATION
                    && entityActionConfirmation.getEntityAction() == EntityAction.UPDATE) {
                if (entityActionConfirmation.getUpdatedFieldList().size() == 0) {
                    entityActionConfirmation.setEntityAction(EntityAction.EQUAL);
                } else {
                    try {
                        final Long originalUpdateTime = entity.getUpdateTime();
                        final List<Object> newDateString = propertyValues.get("updateTime");
                        if (newDateString != null && newDateString.size() == 1) {

                            Long newTime = null;
                            try {
                                newTime = DateUtil.parseNormalDateTimeString(newDateString.get(0).toString());
                            } catch (final Exception e) {
                                // Ignore.
                            }
                            try {
                                newTime = Long.valueOf(newDateString.get(0).toString());
                            } catch (final Exception e) {
                                // Ignore.
                            }

                            if (originalUpdateTime != null && newTime != null && originalUpdateTime > newTime) {
                                entityActionConfirmation.setWarning(true);
                                entityActionConfirmation.getMessageList().add(
                                        "The item you are attempting to import is older than the current version.");
                                entityActionConfirmation.getMessageList()
                                        .add("Current is "
                                                + DateUtil.createNormalDateTimeString(originalUpdateTime)
                                                + " (" + entity.getUpdateUser() + "), Import is "
                                                + DateUtil.createNormalDateTimeString(newTime));

                            }
                        }
                    } catch (final Exception ex) {
                        LOGGER.error("Unable to add date!", ex);
                    }
                }
            }

            if (importMode == ImportMode.IGNORE_CONFIRMATION
                    || (importMode == ImportMode.ACTION_CONFIRMATION && entityActionConfirmation.isAction())) {
                genericEntityService.importEntity(entity, DocRefUtil.create(folder));
            }

        } catch (final SAXException | IOException | ParserConfigurationException ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }

    private void updateProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                final List<Object> values, final EntityActionConfirmation entityActionConfirmation,
                                final ImportMode importMode, final File file, final String name) {
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
                            setStringProperty(beanWrapper, propertyName, string, entityActionConfirmation, importMode);
                        }
                    } else if (obj instanceof DocRef) {
                        final DocRef docRef = (DocRef) obj;
                        setDocRefProperty(beanWrapper, propertyName, docRef, entityActionConfirmation, importMode);
                    }
                } else {
                    // The new property value is null so set it to null.
                    if (importMode == ImportMode.CREATE_CONFIRMATION) {
                        if (beanWrapper.getPropertyValue(propertyName) != null) {
                            entityActionConfirmation.getUpdatedFieldList().add(propertyName);
                        }
                    } else {
                        beanWrapper.setPropertyValue(propertyName, null);
                    }
                }

            } else if (beanWrapper.isPropertyBaseEntitySet(propertyName)) {
                final Set<BaseEntity> newSet = new HashSet<>();

                if (values != null && values.size() > 0) {
                    final Class<? extends NamedEntity> clazz = beanWrapper.getPropertyBaseEntityType(propertyName);
                    final String entityType = classTypeMap.getEntityType(clazz);

                    // Some entity types are not imported, e.g. Volumes so we
                    // will sometimes get null entity type here if they are
                    // unsupported.
                    if (entityType != null) {
                        for (final Object obj : values) {
                            if (obj instanceof String) {
                                final String string = (String) obj;
                                if (StringUtils.hasText(string)) {
                                    final BaseEntity entity = resolveEntityByPath(beanWrapper, clazz, string);
                                    newSet.add(entity);
                                }
                            } else if (obj instanceof DocRef) {
                                final DocRef docRef = (DocRef) obj;
                                final BaseEntity entity = resolveEntityByDocRef(beanWrapper, docRef);
                                newSet.add(entity);
                            }
                        }
                    }
                }
                @SuppressWarnings("unchecked")
                final Set<? extends DocumentEntity> oldSet = (Set<? extends DocumentEntity>) beanWrapper
                        .getPropertyValue(propertyName);

                if (importMode == ImportMode.CREATE_CONFIRMATION) {
                    if (!newSet.equals(oldSet)) {
                        entityActionConfirmation.getUpdatedFieldList().add(propertyName);
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
                        entityActionConfirmation.getUpdatedFieldList().add(propertyName);
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
                        entityActionConfirmation.getUpdatedFieldList().add(propertyName);
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
                                   final String value, final EntityActionConfirmation entityActionConfirmation,
                                   final ImportMode importMode) {
        final Class<? extends NamedEntity> clazz = beanWrapper.getPropertyBaseEntityType(propertyName);

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
                    entityActionConfirmation.getUpdatedFieldList().add(propertyName);
                }
            } else {
                res.setData(value);
                beanWrapper.setPropertyValue(propertyName, res);
            }

        } else {
            // This property is an entity so get the referenced
            // entity if we can.
            final BaseEntity entity = resolveEntityByPath(beanWrapper, clazz, value);
            if (importMode == ImportMode.CREATE_CONFIRMATION) {
                if (!entity.equals(beanWrapper.getPropertyValue(propertyName))) {
                    entityActionConfirmation.getUpdatedFieldList().add(propertyName);
                }
            }
            beanWrapper.setPropertyValue(propertyName, entity);
        }
    }

    private void setDocRefProperty(final BaseEntityBeanWrapper beanWrapper, final String propertyName,
                                   final DocRef docRef, final EntityActionConfirmation entityActionConfirmation,
                                   final ImportMode importMode) {
        // This property is an entity so get the referenced
        // entity if we can.
        final BaseEntity entity = resolveEntityByDocRef(beanWrapper, docRef);
        if (importMode == ImportMode.CREATE_CONFIRMATION) {
            if (!entity.equals(beanWrapper.getPropertyValue(propertyName))) {
                entityActionConfirmation.getUpdatedFieldList().add(propertyName);
            }
        }
        beanWrapper.setPropertyValue(propertyName, entity);
    }

    private BaseEntity resolveEntityByPath(final BaseEntityBeanWrapper beanWrapper, final Class<? extends NamedEntity> clazz,
                                           final String val) {
        NamedEntity entity;
//        if (DocumentEntity.class.isAssignableFrom(clazz)) {
//            entity = entityPathResolver.getEntity(classTypeMap.getEntityType(clazz), beanWrapper.getBaseEntity(), val,
//                    null);
//        } else {
            entity = genericEntityService.loadByName(classTypeMap.getEntityType(clazz), val);
//        }

        if (entity == null) {
            // If we couldn't find the referenced entity then throw an entity
            // dependency exception. We might get the dependency added later in
            // this import and be able to add this entity again later.
            throw new EntityDependencyServiceException(classTypeMap.getEntityType(clazz), val);
        }

        return entity;
    }

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

    private void writeStartElement(final TransformerHandler th, final String name) throws SAXException {
        th.startElement("", name, name, BLANK_ATTRIBUTES);
    }

    private void writeEndElement(final TransformerHandler th, final String name) throws SAXException {
        th.endElement("", name, name);
    }

    private void writeContent(final TransformerHandler th, final String content) throws SAXException {
        th.characters(content.toCharArray(), 0, content.length());
    }

    /**
     * Given a class return all the property names.
     */
    private List<Property> getPropertyList(final Class<?> clazz, final boolean omitAuditFields) {
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

    private String toUniquePath(final EntityActionConfirmation entityActionConfirmation) {
        return entityActionConfirmation.getPath() + "." + entityActionConfirmation.getEntityType();
    }

    private String toUniquePath(final String path, final String entityType) {
        return path + "." + entityType;
    }

    private String toPath(final Folder folder, final String name) {
        if (folder != null) {
            if (name != null) {
                return entityPathResolver.getEntityPath(Folder.ENTITY_TYPE, null, folder) + "/" + name;
            } else {
                return entityPathResolver.getEntityPath(Folder.ENTITY_TYPE, null, folder);
            }
        } else {
            return "/" + name;
        }
    }

    private static class EntityClassComparator implements Comparator<Class<? extends DocumentEntity>> {
        @Override
        public int compare(final Class<? extends DocumentEntity> o1, final Class<? extends DocumentEntity> o2) {
            return o1.getSimpleName().compareTo(o2.getSimpleName());
        }
    }
}
