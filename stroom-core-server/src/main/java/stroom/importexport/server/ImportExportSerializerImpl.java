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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.Entity;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.FindService;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.HasFolder;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.entity.shared.ImportState.State;
import stroom.entity.shared.PermissionException;
import stroom.folder.server.FolderServiceImpl;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.Message;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ImportExportSerializerImpl implements ImportExportSerializer {
    private static final DocRef SYSTEM_FOLDER = new DocRef(Folder.ENTITY_TYPE, "0", "System");

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportSerializerImpl.class);

    private static final Set<String> RESOURCE_FETCH_SET = Collections.singleton("all");
    private final FolderService folderService;
    private final GenericEntityService genericEntityService;
    private final ClassTypeMap classTypeMap = new ClassTypeMap();
    private volatile boolean entitiesInitialised = false;
    private final SecurityContext securityContext;

    @Inject
    public ImportExportSerializerImpl(final FolderService folderService,
                                      final GenericEntityService genericEntityService,
                                      final SecurityContext securityContext) {
        this.folderService = folderService;
        this.genericEntityService = genericEntityService;
        this.securityContext = securityContext;
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

    /**
     * IMPORT
     */
    @SuppressWarnings("unchecked")
    @Override
    public void read(final Path dir, List<ImportState> importStateList,
                     final ImportMode importMode) {
        init();

        if (importMode == ImportMode.IGNORE_CONFIRMATION) {
            importStateList = new ArrayList<>();
        }

        // Key the actionConfirmation's by their key
        final Map<DocRef, ImportState> map = new HashMap<>();
        for (final ImportState importState : importStateList) {
            map.put(importState.getDocRef(), importState);
        }

        // Import
        for (final String entityType : classTypeMap.getEntityTypeList()) {
            performImport(dir, entityType, map, importMode);
        }

        // Rebuild the list
        importStateList.clear();
        importStateList.addAll(map.values());
        Collections.sort(importStateList, (o1, o2) -> {
            final int r = ModelStringUtil.pathComparator().compare(o1.getSourcePath(), o2.getSourcePath());
            if (r == 0) {
                return classTypeMap.getEntityPriority(o1.getDocRef().getType())
                        .compareTo(classTypeMap.getEntityPriority(o2.getDocRef().getType()));
            } else {
                return r;
            }
        });
    }

    /**
     * Import a config type
     */
    public <E extends DocumentEntity> void performImport(final Path dir,
                                                         final String entityType, final Map<DocRef, ImportState> confirmMap,
                                                         final ImportMode importMode) {
        init();

        if (!Files.isDirectory(dir)) {
            throw new EntityServiceException("Dir \"" + dir.toAbsolutePath().toString() + "\" not found");
        }

        try {
            // Look for matching files
            final String match = "." + entityType + ".";
            List<Path> paths;
            try (final Stream<Path> stream = Files.walk(dir)) {
                final Set<Path> set = stream
                        .filter(p -> p.getFileName().toString().contains(match))
                        .map(p -> {
                            String name = p.getFileName().toString();
                            name = name.substring(0, name.indexOf(match) + match.length() - 1);
                            return p.getParent().resolve(name);
                        })
                        .collect(Collectors.toSet());
                try (Stream<Path> s = set.stream()) {
                    paths = s
                            .sorted(Comparator.comparingInt(Path::getNameCount))
                            .collect(Collectors.toList());
                }
            }

            paths.forEach(path -> {
                ImportState importState = null;

                try {
                    final Path relativePath = dir.relativize(path);
                    final String sourcePath = relativePath.toString().substring(0, relativePath.toString().lastIndexOf("."));

                    LOGGER.info("Importing " + entityType + " '" + sourcePath + "'");

                    // Find all of the files associated with this document config.
                    final String matchingConfig = relativePath.getFileName().toString();

                    // Create a map of all of the data required to import this document.
                    final Map<String, String> dataMap = new HashMap<>();
                    try (final Stream<Path> stream = Files.list(path.getParent())) {
                        final List<Path> parts = stream
                                .filter(p -> p.getFileName().toString().startsWith(matchingConfig))
                                .collect(Collectors.toList());

                        parts.forEach(part -> {
                            try {
                                final String key = dir.relativize(part).toString();
                                final String content = new String(Files.readAllBytes(part), Charset.forName("UTF-8"));
                                dataMap.put(key, content);
                            } catch (final Throwable e) {
                                LOGGER.error(e.getMessage(), e);
                                LOGGER.error("DATA SIZE = " + dataMap.size());
                            }
                        });
                    }

                    // Find out if this item exists.
                    // TODO : In v6 the UUID will be part of the file name so that we don't have to read the config to get it.
                    final Config config = new Config();
                    config.read(new StringReader(dataMap.get(relativePath + ".xml")));
                    final String uuid = config.getString("uuid");
                    if (uuid == null) {
                        throw new RuntimeException("Unable to get UUID for " + entityType + " '" + sourcePath + "'");
                    }

                    // Create a doc ref.
                    final DocRef docRef = new DocRef(entityType, uuid);
                    // Create or get the import state.
                    importState = confirmMap.computeIfAbsent(docRef, k -> new ImportState(docRef, sourcePath));
                    // See if there is already a folder for this item, i.e. it exists and is in a folder.
                    DocRef folderRef = getExistingFolder(docRef);

                    if (folderRef == null) {
                        // This is a new item so check that the user has permission to import it.

                        // Find the nearest parent folder and see if we are allowed to import/create items in the parent folder.
                        int pathIndex = 0;
                        DocRef nearestFolder = SYSTEM_FOLDER;
                        if (relativePath.getNameCount() == 1) {
                            folderRef = nearestFolder;
                        } else {
                            Folder parentFolder = null;
                            for (pathIndex = 0; pathIndex < relativePath.getNameCount() - 1; pathIndex++) {
                                final String folderName = relativePath.getName(pathIndex).toString();
                                Folder folder = folderService.loadByName(DocRefUtil.create(parentFolder), folderName);
                                if (folder != null) {
                                    nearestFolder = DocRefUtil.create(folder);
                                    folderRef = nearestFolder;
                                } else {
                                    folderRef = null;
                                    break;
                                }

                                parentFolder = folder;
                            }
                        }

                        // Only allow administrators to import documents with no folder.
                        // TODO : In v6 the root folder will be a real folder.
                        if (SYSTEM_FOLDER.equals(nearestFolder)) {
                            if (!securityContext.isAdmin()) {
                                throw new PermissionException("Only administrators can create root level entries");
                            }
                        }

                        // If the nearest folder is not the same as the folder reference then we have only got part way to the folder structure we need.
                        // If this is the case then we will need to create folders in the nearest folder.
                        if (nearestFolder != folderRef) {
                            if (!securityContext.hasDocumentPermission(nearestFolder.getType(), nearestFolder.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(Folder.ENTITY_TYPE))) {
                                throw new PermissionException("You do not have permission to create a folder in '" + nearestFolder);
                            }

                            if (!securityContext.hasDocumentPermission(nearestFolder.getType(), nearestFolder.getUuid(), DocumentPermissionNames.IMPORT)) {
                                throw new PermissionException("You do not have permission to import folders into '" + nearestFolder);
                            }

                            // Add the required folders for this new item.
                            if (importMode == ImportMode.IGNORE_CONFIRMATION
                                    || (importMode == ImportMode.ACTION_CONFIRMATION && importState.isAction())) {
                                if (pathIndex > 0) {
                                    pathIndex = pathIndex - 1;
                                }
                                for (; pathIndex < relativePath.getNameCount() - 1; pathIndex++) {
                                    final String folderName = relativePath.getName(pathIndex).toString();
                                    Folder folder = folderService.create(nearestFolder, folderName);
                                    nearestFolder = DocRefUtil.create(folder);
                                    folderRef = nearestFolder;
                                }
                            }


                        } else {
                            if (!securityContext.hasDocumentPermission(folderRef.getType(), folderRef.getUuid(), DocumentPermissionNames.getDocumentCreatePermission(entityType))) {
                                throw new PermissionException("You do not have permission to create '" + docRef + "' in '" + folderRef);
                            }

                            if (!securityContext.hasDocumentPermission(folderRef.getType(), folderRef.getUuid(), DocumentPermissionNames.IMPORT)) {
                                throw new PermissionException("You do not have permission to import '" + docRef + "' into '" + folderRef);
                            }
                        }

//                        if (importMode == ImportMode.CREATE_CONFIRMATION) {
//                            importState.setState(State.NEW);
//                        }
                    } else {
                        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.UPDATE)) {
                            throw new PermissionException("You do not have permission to update '" + docRef + "'");
                        }

                        if (!securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.IMPORT)) {
                            throw new PermissionException("You do not have permission to import '" + docRef + "'");
                        }

//                        if (importMode == ImportMode.CREATE_CONFIRMATION) {
//                            importState.setState(State.UPDATE);
//                        }
                    }
//
//                    // If we have got here then see if the user wants to import this item (this is usually after they have had a chance to confirm changes)
//                    if ((ImportMode.ACTION_CONFIRMATION.equals(importMode) && importState.isAction()) || ImportMode.IGNORE_CONFIRMATION.equals(importMode)) {

                    // Get the path for the destination...

                    if (folderRef == null) {
                        // If we haven't got a folder then this is a new item and we haven't yet created a folder for it.
                        // This might be because we didn't have permission to do so.
                        importState.setState(State.NEW);
                        importState.setDestPath(sourcePath);

                    } else {
                        // Import the item via the appropriate document service.
                        final DocumentEntityService documentEntityService = getService(docRef);
                        if (documentEntityService == null) {
                            throw new RuntimeException("Unable to find service to import " + docRef);
                        }

                        // TODO : In v6 we won't pass down the folder.
                        Folder folder = null;
                        if (!SYSTEM_FOLDER.equals(folderRef)) {
                            folder = folderService.loadByUuid(folderRef.getUuid());
                            if (folder == null) {
                                throw new RuntimeException("Unable to find parent folder: " + folderRef);
                            }
                        }
                        final DocRef imported = documentEntityService.importDocument(folder, dataMap, importState, importMode);

                        // TODO : In v6.0 add folders afterwards on successful import as they won't be controlled by doc service.


                        if (imported != null) {
                            importState.setDestPath(getFolderPath(imported));
                        }
                    }


                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    if (importState != null) {
                        importState.addMessage(Severity.ERROR, e.getMessage());
                    }
                }
            });

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private DocumentEntityService getService(final DocRef docRef) {
        final EntityService<?> entityService = genericEntityService.getEntityService(docRef.getType());
        if (entityService != null && entityService instanceof DocumentEntityService) {
            final DocumentEntityService documentEntityService = (DocumentEntityService) entityService;
            return documentEntityService;
        }
        return null;
    }

    private DocRef getExistingFolder(final DocRef docRef) {
        // TODO : In v6 replace this method with calls to local explorer service to get folder.
        final DocumentEntityService documentEntityService = getService(docRef);
        if (documentEntityService == null) {
            throw new RuntimeException("Unable to find service to import " + docRef);
        }

        final Entity entity = documentEntityService.loadByUuid(docRef.getUuid());
        if (entity != null && entity instanceof HasFolder) {
            final Folder folder = ((HasFolder) entity).getFolder();
            if (folder == null) {
                // Return the root folder.
                return SYSTEM_FOLDER;
            }
            return DocRefUtil.create(folderService.load(folder));
        }

        return null;
    }

    private String getFolderPath(final DocRef docRef) {
        final StringBuilder path = new StringBuilder();

        // TODO : In v6 replace this method with calls to local explorer service to get folder.
        final DocumentEntityService documentEntityService = getService(docRef);
        if (documentEntityService == null) {
            throw new RuntimeException("Unable to find service to import " + docRef);
        }

        final Entity entity = documentEntityService.loadByUuid(docRef.getUuid());
        if (entity != null && entity instanceof HasFolder) {
            if (docRef.getName() != null) {
                path.append(docRef.getName());
            } else if (entity instanceof DocumentEntity) {
                final DocumentEntity documentEntity = (DocumentEntity) entity;
                if (documentEntity.getName() != null) {
                    path.append(documentEntity.getName());
                }
            }

            Folder folder = ((HasFolder) entity).getFolder();
            while (folder != null) {
                folder = folderService.load(folder);
                if (folder != null) {
                    path.insert(0, "/");
                    path.insert(0, folder.getName());
                    folder = folder.getFolder();
                }
            }
        }

        return path.toString();
    }

    /**
     * EXPORT
     */
    @Override
    public void write(final Path dir, final DocRefs docRefs, final boolean omitAuditFields,
                      final List<Message> messageList) {
        init();

        // Create a set of all entities that we are going to try and export.
        final DocRefs expandedDocRefs = new DocRefs();
        if (docRefs != null) {
            for (final DocRef docRef : docRefs) {
                expandDocRefSet(docRef, expandedDocRefs);
            }
        } else {
            // If the supplied set of doc refs is null then get all entities.
            addAll(expandedDocRefs);
        }

        for (final DocRef docRef : expandedDocRefs) {
            try {
                performExport(dir, docRef, omitAuditFields, messageList);
            } catch (final Exception e) {
                messageList.add(new Message(Severity.ERROR, "Error created while exporting (" + docRef.toString() + ") : " + e.getMessage()));
            }
        }
    }

    private void expandDocRefSet(final DocRef docRef, final DocRefs set) {
        if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
            set.add(docRef);
        }

        if (Folder.ENTITY_TYPE.equals(docRef.getType())) {
            for (final String entityType : classTypeMap.getEntityTypeList()) {
                try {
                    List<DocumentEntity> entities;
                    if (SYSTEM_FOLDER.equals(docRef) || docRef == null) {
                        entities = genericEntityService.findByFolder(entityType, null, RESOURCE_FETCH_SET);
                    } else {
                        entities = genericEntityService.findByFolder(entityType, docRef, RESOURCE_FETCH_SET);
                    }

                    if (entities != null) {
                        for (final DocumentEntity documentEntity : entities) {
                            expandDocRefSet(DocRefUtil.create(documentEntity), set);
                        }
                    }
                } catch (final Exception e) {
                    // We might get a permission exception which is expected for some users.
                    LOGGER.debug(e.getMessage(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addAll(final DocRefs set) {
        // If the supplied set of doc refs is null then get all entities.
        for (final String entityType : classTypeMap.getEntityTypeList()) {
            final EntityService<?> entityService = genericEntityService.getEntityService(entityType);
            if (entityService instanceof FindService) {
                final FindService documentEntityService = (FindService) entityService;
                final List<Object> entities = documentEntityService.find(documentEntityService.createCriteria());
                if (entities != null) {
                    for (final Object object : entities) {
                        if (object instanceof DocumentEntity) {
                            final DocumentEntity documentEntity = (DocumentEntity) object;
                            final DocRef docRef = DocRefUtil.create(documentEntity);
                            if (securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), DocumentPermissionNames.EXPORT)) {
                                set.add(docRef);
                            }
                        }
                    }
                }
            }
        }
    }

    private void performExport(final Path dir, final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) throws IOException {
        LOGGER.info("Exporting: " + docRef);

        // TODO : In v6 get the folder structure from the explorer tree service and not the entity service.
        final Entity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid(), RESOURCE_FETCH_SET);
        if (entity != null && entity instanceof DocumentEntity) {
            final DocumentEntity documentEntity = (DocumentEntity) entity;

            // Find the folder path to this entity.
            final List<Folder> folders = getFolderPath(documentEntity);

            // Create directories for the path if not already created by another entity.
            final Path parentDir = createDirs(dir, folders, messageList);

            // Ensure the parent directory exists.
            if (!Files.isDirectory(parentDir)) {
                // Don't output the full path here are we don't want users to see the full file system path.
                messageList.add(new Message(Severity.FATAL_ERROR, "Unable to create directory for folder: " + parentDir.getFileName()));

            } else {
                final EntityService<?> entityService = genericEntityService.getEntityService(entity.getType());
                if (entityService != null && entityService instanceof DocumentEntityService) {
                    final DocumentEntityService documentEntityService = (DocumentEntityService) entityService;
                    final Map<String, String> dataMap = documentEntityService.exportDocument(docRef, omitAuditFields, messageList);
                    if (dataMap != null) {
                        for (final Entry<String, String> entry : dataMap.entrySet()) {
                            try {
                                final Path file = parentDir.resolve(entry.getKey());
                                try (final Writer writer = Files.newBufferedWriter(file, Charset.forName("UTF-8"))) {
                                    writer.write(entry.getValue());
                                }
                            } catch (final IOException e) {
                                messageList.add(new Message(Severity.ERROR, "Failed to write file '" + entry.getKey() + "'"));
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Folder> getFolderPath(final DocumentEntity documentEntity) {
        // Find the folder path to this entity.
        final List<Folder> path = new ArrayList<>();
        Folder folder = documentEntity.getFolder();
        while (folder != null) {
            if (folderService instanceof FolderServiceImpl) {
                folder = ((FolderServiceImpl) folderService).loadByIdInsecure(folder.getId(), Collections.emptySet());
            } else {
                folder = folderService.loadById(folder.getId(), Collections.emptySet());
            }

            if (folder != null) {
                path.add(0, folder);
                folder = folder.getFolder();
            }
        }
        return path;
    }

    private Path createDirs(final Path dir, final List<Folder> folders, final List<Message> messageList) throws IOException {
        Path parentDir = dir;
        for (final Folder folder : folders) {
            final Path child = parentDir.resolve(folder.getName());

            // If this folder hasn't been created yet then output data for the folder and create it.
            if (!Files.isDirectory(child)) {
                final Map<String, String> dataMap = folderService.exportDocument(DocRefUtil.create(folder), true, messageList);
                if (dataMap != null) {
                    for (final Entry<String, String> entry : dataMap.entrySet()) {
                        try {
                            final Path file = parentDir.resolve(entry.getKey());
                            try (final Writer writer = Files.newBufferedWriter(file, Charset.forName("UTF-8"))) {
                                writer.write(entry.getValue());
                            }
                        } catch (final IOException e) {
                            messageList.add(new Message(Severity.ERROR, "Failed to write file '" + entry.getKey() + "'"));
                        }
                    }
                }

                Files.createDirectories(child);
            }

            parentDir = child;
        }

        return parentDir;
    }

    private static class EntityClassComparator implements Comparator<Class<? extends DocumentEntity>> {
        @Override
        public int compare(final Class<? extends DocumentEntity> o1, final Class<? extends DocumentEntity> o2) {
            return o1.getSimpleName().compareTo(o2.getSimpleName());
        }
    }
}
