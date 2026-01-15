/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportDocumentEventLog;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.api.ImportExportVersion;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImportExportSerializerImpl implements ImportExportSerializer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ImportExportSerializerImpl.class);

    public static final String FOLDER = ExplorerConstants.FOLDER_TYPE;

    private final ExplorerService explorerService;
    private final ExplorerNodeService explorerNodeService;
    private final ImportExportActionHandlersImpl importExportActionHandlers;
    private final SecurityContext securityContext;
    private final ImportExportDocumentEventLog importExportDocumentEventLog;
    private static final byte[] LINE_END_CHAR_BYTES = "\n".getBytes(Charset.defaultCharset());

    @Inject
    ImportExportSerializerImpl(final ExplorerService explorerService,
                               final ExplorerNodeService explorerNodeService,
                               final ImportExportActionHandlersImpl importExportActionHandlers,
                               final SecurityContext securityContext,
                               final ImportExportDocumentEventLog importExportDocumentEventLog) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportActionHandlers = importExportActionHandlers;
        this.securityContext = securityContext;
        this.importExportDocumentEventLog = importExportDocumentEventLog;
    }

    /**
     * IMPORT
     */
    @Override
    public Set<DocRef> read(final Path dir,
                            List<ImportState> importStateList,
                            final ImportSettings importSettings) {
        if (importStateList == null || ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode())) {
            importStateList = new ArrayList<>();
        }

        // Attempt content migration if possible.
        new ContentMigration().migrate(dir);

        // Key the actionConfirmation's by their key
        final Map<DocRef, ImportState> confirmMap = importStateList.stream()
                .collect(Collectors.toMap(ImportState::getDocRef, Function.identity()));

        // Find all of the paths to import.
        final Set<DocRef> result = processDir(dir, confirmMap, importSettings);

        // Rebuild the list
        importStateList.clear();
        importStateList.addAll(confirmMap.values());

        // Rebuild the tree,
        if (!ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode())) {
            explorerService.rebuildTree();
        }

        //Add the root of the explorer tree
        result.add(ExplorerConstants.SYSTEM_DOC_REF);

        return result;
    }

    private Set<DocRef> processDir(final Path dir,
                                   final Map<DocRef, ImportState> confirmMap,
                                   final ImportSettings importSettings) {
        final Set<DocRef> result = new HashSet<>();

        try {
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        @NonNull
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                final String fileName = file.getFileName().toString();
                                if (fileName.endsWith(".node") && !fileName.startsWith(".")) {
                                    final DocRef imported = performImport(file, confirmMap, importSettings);
                                    if (imported != null) {
                                        result.add(imported);
                                    }
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return result;
    }

    private DocRef performImport(final Path nodeFile,
                                 final Map<DocRef, ImportState> confirmMap,
                                 final ImportSettings importSettings) {
        DocRef imported = null;
        try {
            // Read the node file.
            final InputStream inputStream = Files.newInputStream(nodeFile);
            final Properties properties = PropertiesSerialiser.read(inputStream);

            // Get the uuid.
            final String uuid = properties.getProperty("uuid");
            // Get the type.
            final String type = properties.getProperty("type");
            // Get the name.
            final String name = properties.getProperty("name");
            // Get the path.
            final String path = properties.getProperty("path");
            // Get the tags.
            final Set<String> tags = explorerService.parseNodeTags(properties.getProperty("tags"));

            // Create a doc ref.
            final DocRef docRef = new DocRef(type, uuid, name);
            // Create or get the import state.
            final ImportState importState = confirmMap.computeIfAbsent(
                    docRef,
                    k -> new ImportState(docRef, createPath(path, docRef.getName())));

            try {
                // Get other associated data.
                final Map<String, byte[]> dataMap = new HashMap<>();
                final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);
                final Path dir = nodeFile.getParent();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePrefix + "*")) {
                    stream.forEach(file -> {
                        try {
                            final String fileName = file.getFileName().toString();
                            if (!file.equals(nodeFile) && !fileName.startsWith(".")) {
                                final String key = fileName.substring(filePrefix.length() + 1);
                                final byte[] bytes = Files.readAllBytes(file);
                                dataMap.put(key, bytes);
                            }
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                }

                // Find the appropriate handler
                final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(type);
                if (importExportActionHandler instanceof NonExplorerDocRefProvider) {
                    imported = importNonExplorerDoc(
                            importExportActionHandler,
                            nodeFile,
                            docRef,
                            path,
                            dataMap,
                            importState,
                            confirmMap,
                            importSettings);

                } else {
                    imported = importExplorerDoc(
                            importExportActionHandler,
                            nodeFile,
                            docRef,
                            tags,
                            path,
                            dataMap,
                            importState,
                            confirmMap,
                            importSettings);
                }
            } catch (final IOException | PermissionException e) {
                LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
                importState.addMessage(Severity.ERROR, e.getMessage());
            }
        } catch (final IOException e) {
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
        }

        return imported;
    }

    private DocRef importNonExplorerDoc(final ImportExportActionHandler importExportActionHandler,
                                        final Path nodeFile,
                                        final DocRef docRef,
                                        final String path,
                                        final Map<String, byte[]> dataMap,
                                        final ImportState importState,
                                        final Map<DocRef, ImportState> confirmMap,
                                        final ImportSettings importSettings) {
        final NonExplorerDocRefProvider nonExplorerDocRefProvider =
                (NonExplorerDocRefProvider) importExportActionHandler;

        final DocRef importRootDocRef = importSettings.getRootDocRef();
        final String importPath = resolvePath(path, importRootDocRef);

        final DocRef ownerDocument = nonExplorerDocRefProvider.getOwnerDocument(docRef, dataMap);
        final Optional<ExplorerNode> existingExplorerNode = explorerNodeService.getNode(ownerDocument);
        String destPath = importPath;
        String destName = ownerDocument.getName();
        if (existingExplorerNode.isPresent()) {
            final List<ExplorerNode> parents = explorerNodeService.getPath(ownerDocument);
            if (!importSettings.isUseImportNames()) {
                destName = existingExplorerNode.get().getName();
            }
            if (!importSettings.isUseImportFolders()) {
                destPath = getParentPath(parents);
            }
        }
        final String docRefName = docRef.getType() + " " + docRef.getUuid();
        final String nameSuffix = " - (" + docRefName + ")";
        importState.setSourcePath(createPath(path, ownerDocument.getName()) + nameSuffix);
        importState.setDestPath(createPath(destPath, destName) + nameSuffix);
        importState.setOwnerDocRef(ownerDocument);


        try {
            // Import the item via the appropriate handler.
            if (canImport(importSettings, importState)) {
                LOGGER.debug(() -> LogUtil.message(
                        "importNonExplorerDoc() - Importing {}, mode: {}, isAction: {} importState: {}",
                        docRef, importSettings.getImportMode(), importState.isAction(), importState.getState()));

                final DocRef imported = importExportActionHandler.importDocument(
                        docRef,
                        dataMap,
                        importState,
                        importSettings);

                if (imported == null) {
                    throw new RuntimeException("Import failed - no DocRef returned");
                }

                // Add explorer node afterwards on successful import as they won't be controlled by
                // doc service.
                if (ImportSettings.ok(importSettings, importState)) {
                    importExportDocumentEventLog.importDocument(
                            docRef.getType(),
                            imported.getUuid(),
                            docRef.getName(),
                            null);
                }
            } else {
                LOGGER.debug(() -> LogUtil.message(
                        "importNonExplorerDoc() - Skipping {}, mode: {}, isAction: {} importState: {}",
                        docRef, importSettings.getImportMode(), importState.isAction(), importState.getState()));
                // We can't import this item so remove it from the map.
                confirmMap.remove(docRef);
            }
        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importExportDocumentEventLog.importDocument(
                    docRef.getType(),
                    docRef.getUuid(),
                    docRef.getName(),
                    e);
            throw e;
        }

        return docRef;
    }

    /**
     * Imports something that appears in the Explorer Tree.
     *
     * @param importExportActionHandler Handler for the type of DocRef
     * @param nodeFile                  Path to the import file on disk
     * @param docRef                    DocRef created from the .node data on disk
     * @param tags                      List of tags extracted from .node data on disk
     * @param path                      Path to the item in the Explorer Tree, from the .node data
     *                                  on disk
     * @param dataMap                   Map of disk file extension to disk file contents
     * @param importState               State of the import for docRef
     * @param confirmMap                Accessed to remove docRef from the map if the docRef
     *                                  cannot be imported.
     * @param importSettings            Key settings for the import; notably the RootDocRef.
     * @return The DocRef of the imported document.
     */
    private DocRef importExplorerDoc(final ImportExportActionHandler importExportActionHandler,
                                     final Path nodeFile,
                                     final DocRef docRef,
                                     final Set<String> tags,
                                     final String path,
                                     final Map<String, byte[]> dataMap,
                                     final ImportState importState,
                                     final Map<DocRef, ImportState> confirmMap,
                                     final ImportSettings importSettings) {

        // This shows where the thing is in the Explorer Tree
        // Uses the importSettings.getRootDocRef() to work out where to put things
        final DocRef importRootDocRef = importSettings.getRootDocRef();
        final String importPath = resolvePath(path, importRootDocRef);

        String destPath = importPath;
        String destName = docRef.getName();

        // See if we have an existing node for this item.
        final Optional<ExplorerNode> existingNode = explorerNodeService.getNode(docRef);
        final boolean docExists = existingNode.isPresent();
        boolean moving = false;

        if (docExists) {
            // This is a pre-existing item so make sure we are allowed to update it.
            if (!securityContext.hasDocumentPermission(docRef,
                    DocumentPermission.EDIT)) {
                throw new PermissionException(securityContext.getUserRef(),
                        "You do not have permission to update '" + docRef + "'");
            }

            importState.setState(State.UPDATE);
            final List<ExplorerNode> parents = explorerNodeService.getPath(docRef);
            final String currentPath = getParentPath(parents);
            if (!importSettings.isUseImportNames()) {
                destName = existingNode.get().getName();
            }
            if (!importSettings.isUseImportFolders()) {
                destPath = currentPath;
            }
            if (!destPath.equals(currentPath)) {
                moving = true;
            }
        } else {
            importState.setState(State.NEW);
        }
        importState.setDestPath(createPath(destPath, destName));

        // Only do this if we're not mocked up
        // If we are creating a new node or moving an existing one then create the destination folders and check
        // permissions.
        DocRef folderRef = null;
        ExplorerNode parentNode = null;
        if (!importSettings.isMockEnvironment()) {
            if (importState.getState() == State.NEW || moving) {
                // Create parent folders for the new node.
                final ExplorerNode parent = explorerNodeService.getRoot();
                parentNode = getOrCreateParentFolder(parent,
                        importPath,
                        ImportSettings.ok(importSettings, importState));
                // Check permissions on the parent folder.
                folderRef = new DocRef(parentNode.getType(), parentNode.getUuid(), parentNode.getName());
                if (!securityContext.hasDocumentCreatePermission(folderRef, docRef.getType())) {
                    throw new PermissionException(securityContext.getUserRef(),
                            "You do not have permission to create '" + docRef + "' in '" + folderRef);
                }
            }
        }

        try {
            // Import the item via the appropriate handler.
            if (importExportActionHandler != null && (
                    ImportMode.CREATE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                    ImportMode.IGNORE_CONFIRMATION.equals(importSettings.getImportMode()) ||
                    importState.isAction())) {

                final DocRef imported = importExportActionHandler.importDocument(
                        docRef,
                        dataMap,
                        importState,
                        importSettings);

                if (imported == null) {
                    throw new RuntimeException("Import failed - no DocRef returned");
                }

                if (!importSettings.isMockEnvironment()) {
                    // Add explorer node afterwards on successful import as they won't be controlled by
                    // doc service.
                    if (ImportSettings.ok(importSettings, importState)) {
                        final ExplorerNode explorerNode = ExplorerNode
                                .builder()
                                .docRef(docRef)
                                .build();

                        // Create, rename and/or move explorer node.
                        if (existingNode.isEmpty()) {
                            explorerNodeService.createNode(
                                    imported,
                                    folderRef,
                                    PermissionInheritance.DESTINATION);
                            explorerService.rebuildTree();
                        } else {
                            if (importSettings.isUseImportNames()) {
                                explorerService.rename(explorerNode, docRef.getName());
                            }
                            if (moving) {
                                explorerService.move(
                                        Collections.singletonList(explorerNode),
                                        parentNode,
                                        PermissionInheritance.DESTINATION);
                            }
                        }

                        importExportDocumentEventLog.importDocument(
                                docRef.getType(),
                                imported.getUuid(),
                                docRef.getName(),
                                null);
                    }
                }
            } else {
                LOGGER.debug(() -> LogUtil.message(
                        "importExplorerDoc() - Skipping {}, mode: {}, isAction: {} importState: {}",
                        docRef, importSettings.getImportMode(), importState.isAction(), importState.getState()));
                // We can't import this item so remove it from the map.
                confirmMap.remove(docRef);
            }
        } catch (final RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            LOGGER.error("Error importing file {}", nodeFile.toAbsolutePath(), e);
            importExportDocumentEventLog.importDocument(docRef.getType(),
                    docRef.getUuid(),
                    docRef.getName(),
                    e);
            throw e;
        }

        return docRef;
    }

    private boolean canImport(final ImportSettings importSettings, final ImportState importState) {
        final ImportMode mode = importSettings.getImportMode();
        final boolean canImport = ImportMode.CREATE_CONFIRMATION.equals(mode)
                                  || ImportMode.IGNORE_CONFIRMATION.equals(mode)
                                  || (importState.isAction() && importState.getState() != State.IGNORE);
        LOGGER.debug(() -> LogUtil.message(
                "canImport() - mode: {}, isAction: {} importState: {}, canImport: {}",
                mode, importState.isAction(), importState.getState(), canImport));
        return canImport;
    }

    /**
     * Returns the path in the Explorer Tree.
     * It will return either 'path' (the first parameter), or the path resolved
     * to the 'importSettings' Root DocRef if that exists.
     *
     * @param path             The path to the item, deduced from the import data.
     * @param importRootDocRef The setting from ImportSettings the holds the
     *                         optional RootDocRef - i.e. where the stuff on
     *                         disk should be imported to. Will be null if
     *                         not set.
     * @return Returns path if the RootDocRef hasn't been set or cannot be
     * found in the Explorer Tree. Otherwise, returns the path to the
     * RootDocRef/path.
     */
    private String resolvePath(final String path, final DocRef importRootDocRef) {
        String result = path;
        if (importRootDocRef != null) {
            final Optional<ExplorerNode> optImportRootNode =
                    explorerNodeService.getNode(importRootDocRef);
            if (optImportRootNode.isPresent()) {
                final ExplorerNode importRootNode = optImportRootNode.get();
                final List<ExplorerNode> nodePathToImportRootDocRef = explorerNodeService.getPath(importRootDocRef);
                nodePathToImportRootDocRef.add(importRootNode);
                // Remove root node.

                final ExplorerNode systemRootNode = explorerNodeService.getRoot();
                if (nodePathToImportRootDocRef.getFirst().equals(systemRootNode)) {
                    nodePathToImportRootDocRef.removeFirst();
                }
                if (!nodePathToImportRootDocRef.isEmpty()) {
                    final String rootPath = getParentPath(nodePathToImportRootDocRef);
                    result = createPath(rootPath, path);
                }
            }
        }
        return result;
    }

    /**
     * Method for exporting using the latest version of export format.
     * @param rootNodePath     Path to root node of the export. The method
     *                         removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param docTypesToIgnore Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null. Used to prevent
     *                         exports of GitRepo objects.
     * @param omitAuditFields  Do not export audit fields.
     * @return The summary of the export.
     */
    @Override
    public ExportSummary write(final List<ExplorerNode> rootNodePath,
                               final Path dir,
                               final Set<DocRef> docRefs,
                               final Set<String> docTypesToIgnore,
                               final boolean omitAuditFields) {
        return this.write(rootNodePath,
                dir,
                docRefs,
                docTypesToIgnore,
                omitAuditFields,
                ImportExportVersion.V1);
    }

    /**
     * Method for exporting.
     * @param rootNodePath     Path to root node of the export. The method
     *                         removes these path elements from the start of
     *                         the exported path. Normally this should be the path to the
     *                         GitRepo node, including that node.
     * @param dir              Where to serialize the DocRef items to on disk.
     * @param docRefs          Set of the DocRefs to serialize.
     * @param docTypesToIgnore Set of the Doc types that shouldn't be exported, nor
     *                         their children. Must not be null. Used to prevent
     *                         exports of GitRepo objects.
     * @param omitAuditFields  Do not export audit fields.
     * @return The summary of the export.
     */
    @Override
    public ExportSummary write(final List<ExplorerNode> rootNodePath,
                               final Path dir,
                               final Set<DocRef> docRefs,
                               final Set<String> docTypesToIgnore,
                               final boolean omitAuditFields,
                               final ImportExportVersion version) {

        if (version != ImportExportVersion.V1) {
            throw new RuntimeException("Invalid version for export: "
                                       + version);
        }

        Objects.requireNonNull(docTypesToIgnore);

        // Create a set of all entities that we are going to try and export.
        final Set<DocRef> expandedDocRefs = expandDocRefSet(docRefs, docTypesToIgnore);
        if (expandedDocRefs.isEmpty()) {
            throw new EntityServiceException("No documents were found that could be exported");
        }
        LOGGER.info("DocRefs to export: {}", expandedDocRefs);

        final ExportSummary exportSummary = new ExportSummary();
        final List<Message> messageList = new ArrayList<>();
        for (final DocRef docRef : expandedDocRefs) {
            try {
                LOGGER.debug("Exporting '{}' to '{}', omitAuditFields: '{}'", docRef, dir, omitAuditFields);
                performExport(rootNodePath, dir, docRef, omitAuditFields, messageList);
                exportSummary.addSuccess(docRef.getType());
            } catch (final IOException | RuntimeException e) {
                messageList.add(new Message(Severity.ERROR,
                        "Error created while exporting (" + docRef.toString() + ") : "
                        + LogUtil.exceptionMessage(e)));
                exportSummary.addFailure(docRef.getType());
            }
        }
        exportSummary.setMessages(messageList);
        return exportSummary;
    }

    private ExplorerNode getOrCreateParentFolder(ExplorerNode parent,
                                                 final String path,
                                                 final boolean create) {
        // Create a parent folder for the new node.
        final String[] elements = path.split("/");

        for (final String element : elements) {
            if (!element.isEmpty()) {
                List<ExplorerNode> nodes = explorerNodeService.getNodesByName(parent, element);
                nodes = nodes.stream().filter(ExplorerConstants::isFolder).toList();

                if (nodes.isEmpty()) {
                    // No parent node can be found for this element so create one if possible.
                    final DocRef folderRef = new DocRef(parent.getType(), parent.getUuid(), parent.getName());
                    if (!securityContext.hasDocumentCreatePermission(folderRef, FOLDER)) {
                        throw new PermissionException(securityContext.getUserRef(),
                                "You do not have permission to create a folder in '" + folderRef);
                    }

                    // Go and create the folder if we are actually importing now.
                    if (create) {
                        // Go and create the folder.
                        parent = explorerService.create(
                                FOLDER,
                                element,
                                parent,
                                PermissionInheritance.DESTINATION);
                    }

                } else {
                    parent = nodes.getFirst();
                }
            }
        }

        return parent;
    }

    private Set<DocRef> expandDocRefSet(final Set<DocRef> set,
                                        final Set<String> docTypesToIgnore) {
        final Set<DocRef> expandedDocRefs = new HashSet<>();

        if (set == null) {
            // If the set is null then add the whole tree of exportable items.
            addDescendants(null, expandedDocRefs, docTypesToIgnore);
        } else {
            for (final DocRef docRef : set) {
                if (!docTypesToIgnore.contains(docRef.getType())) {
                    addDocRef(docRef, expandedDocRefs);
                    addDescendants(docRef, expandedDocRefs, docTypesToIgnore);
                }
            }
        }

        return expandedDocRefs;
    }

    /**
     * Called recursively to add things to the set of Docs to export.
     *
     * @param docRef           The root docref
     * @param expandedDocRefs  The output of the function
     * @param docTypesToIgnore Any types of doc to ignore and not recurse below.
     */
    private void addDescendants(final DocRef docRef,
                                final Set<DocRef> expandedDocRefs,
                                final Set<String> docTypesToIgnore) {

        final List<ExplorerNode> children = explorerNodeService.getChildren(docRef);
        for (final ExplorerNode child : children) {
            if (ExplorerConstants.isFolder(child)) {
                if (!docTypesToIgnore.contains(child.getType())) {
                    this.addDocRef(child.getDocRef(), expandedDocRefs);
                    this.addDescendants(child.getDocRef(), expandedDocRefs, docTypesToIgnore);
                }
            } else {
                if (!docTypesToIgnore.contains(child.getType())) {
                    this.addDocRef(child.getDocRef(), expandedDocRefs);
                }
            }
        }
    }

    private void addDocRef(final DocRef docRef, final Set<DocRef> docRefs) {
        try {
            final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(
                    docRef.getType());
            if (importExportActionHandler != null) {
                if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                    docRefs.add(docRef);

                    final Set<DocRef> associatedNonExplorerDocRefs =
                            importExportActionHandler.findAssociatedNonExplorerDocRefs(docRef);
                    if (associatedNonExplorerDocRefs != null) {
                        docRefs.addAll(associatedNonExplorerDocRefs);
                    }
                }
            }
        } catch (final RuntimeException e) {
            // We might get a permission exception which is expected for some users.
            LOGGER.debug(e.getMessage(), e);
        }
    }

    /**
     * Prunes the node path for GitRepo exports. Removes everything in the rootPath
     * from the nodePath if the rootPath matches the start of the nodePath.
     *
     * @param rootPath Can be null, in which case this function just returns nodePath.
     *                 Otherwise, the list of items to prune from the start of nodePath.
     * @param nodePath The node path to prune.
     * @return The pruned nodePath.
     */
    private List<ExplorerNode> pruneNodePath(final List<ExplorerNode> rootPath,
                                             final List<ExplorerNode> nodePath) {
        List<ExplorerNode> resultPath = nodePath;

        if (rootPath != null) {
            final List<String> rootUuids = rootPath.stream()
                    .map(ExplorerNode::getUuid)
                    .toList();
            final List<String> nodeUuids = nodePath.stream()
                    .map(ExplorerNode::getUuid)
                    .limit(rootPath.size())
                    .toList();
            if (rootUuids.equals(nodeUuids)) {
                resultPath = nodePath.subList(rootPath.size(), nodePath.size());
            } else {
                LOGGER.warn("Node path '{}' does not start with the root path '{}'", nodePath, rootPath);
            }
        }

        return resultPath;
    }

    private void performExport(final List<ExplorerNode> rootNodePath,
                               final Path dir,
                               final DocRef initialDocRef,
                               final boolean omitAuditFields,
                               final List<Message> messageList) throws IOException {

        final ImportExportActionHandler importExportActionHandler = importExportActionHandlers.getHandler(
                initialDocRef.getType());
        final List<Message> localMessageList = new ArrayList<>();
        if (importExportActionHandler != null) {

            LOGGER.debug("Exporting: " + initialDocRef);
            final Map<String, byte[]> dataMap = importExportActionHandler.exportDocument(initialDocRef,
                    omitAuditFields,
                    localMessageList);
            final DocRef explorerDocRef;
            final ExplorerNode explorerNode;
            final DocRef docRef;
            if (importExportActionHandler instanceof final NonExplorerDocRefProvider docRefProvider) {
                // Find the closest DocRef to this one to give a location to export it.
                explorerDocRef = docRefProvider.findNearestExplorerDocRef(initialDocRef);

                if (explorerDocRef == null) {
                    throw new RuntimeException(
                            "Unable to locate suitable location for export, whilst exporting " +
                            initialDocRef);
                }

                final String docRefName = docRefProvider.findNameOfDocRef(initialDocRef);
                docRef = new DocRef(initialDocRef.getType(), initialDocRef.getUuid(), docRefName);

                explorerNode = ExplorerNode
                        .builder()
                        .type(docRef.getType())
                        .uuid(docRef.getUuid())
                        .name(docRefName)
                        .build();
            } else {
                docRef = initialDocRef;
                explorerDocRef = docRef;
                explorerNode = explorerNodeService.getNode(explorerDocRef).orElse(null);
            }

            try {
                // Get the explorer path to this doc ref.
                List<ExplorerNode> path = explorerNodeService.getPath(explorerDocRef);

                // Check if we've specified a non-system root for the export
                // If we have then remove everything down to that root
                if (rootNodePath != null) {
                    path = this.pruneNodePath(rootNodePath, path);
                }

                // Turn the path into a list of strings but ignore any nodes that aren't folders, e.g. the root.
                final List<String> pathElements = path.stream()
                        .filter(ExplorerConstants::isFolder)
                        .map(ExplorerNode::getName).toList();

                // Create directories for the path if not already created by another entity.
                final Path parentDir = createDirs(dir, pathElements);

                // Ensure the parent directory exists.
                if (!Files.isDirectory(parentDir)) {
                    // Don't output the full path here as we don't want users to see the full file system path.
                    localMessageList.add(new Message(Severity.FATAL_ERROR,
                            "Unable to create directory for folder: " + parentDir.getFileName()));

                } else {
                    // Write a file for this explorer entry.
                    final String filePrefix = ImportExportFileNameUtil.createFilePrefix(docRef);

                    writeNodeProperties(explorerNode, pathElements, parentDir, filePrefix, localMessageList);

                    // Write out all associated data.
                    dataMap.forEach((k, v) -> {
                        final String fileName = filePrefix + "." + k;
                        try {
                            final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
                            outputStream.write(v);
                            // POSIX standard is for all files to end with a line end (\n) so add one if not there
                            if (isMissingLineEndAsLastChar(v)) {
                                outputStream.write(LINE_END_CHAR_BYTES);
                            }
                            outputStream.close();

                        } catch (final IOException e) {
                            localMessageList.add(new Message(
                                    Severity.ERROR,
                                    "Failed to write file '" + fileName + "': "
                                    + LogUtil.exceptionMessage(e)));
                        }
                    });

                    final List<Message> errors = localMessageList.stream()
                            .filter(message -> Severity.FATAL_ERROR.equals(message.getSeverity())
                                               || Severity.ERROR.equals(message.getSeverity()))
                            .toList();

                    if (errors.isEmpty()) {
                        importExportDocumentEventLog.exportDocument(docRef, null);
                    } else {
                        final String errorText = errors.stream()
                                .map(error -> error.getSeverity() + ": " + error.getMessage())
                                .collect(Collectors.joining(", "));

                        importExportDocumentEventLog.exportDocument(docRef, new RuntimeException(errorText));
                    }
                }
            } catch (final Exception e) {
                importExportDocumentEventLog.exportDocument(docRef, e);
                localMessageList.add(new Message(
                        Severity.ERROR,
                        "Error exporting directory '"
                        + NullSafe.get(dir, Path::toAbsolutePath, Path::normalize) + "': "
                        + LogUtil.exceptionMessage(e)));
            } finally {
                messageList.addAll(localMessageList);
            }
        }
    }

    private boolean isMissingLineEndAsLastChar(final byte[] bytes) {
        if (bytes.length < LINE_END_CHAR_BYTES.length) {
            return false;
        } else {
            final byte[] lastChar = Arrays.copyOfRange(bytes, bytes.length - LINE_END_CHAR_BYTES.length, bytes.length);
            return !Arrays.equals(LINE_END_CHAR_BYTES, lastChar);
        }
    }

    private void writeNodeProperties(final ExplorerNode explorerNode,
                                     final List<String> pathElements,
                                     final Path parentDir,
                                     final String filePrefix,
                                     final List<Message> messageList) {
        final String fileName = filePrefix + ".node";

        try {
            final Properties properties = new Properties();
            properties.setProperty("uuid", explorerNode.getUuid());
            properties.setProperty("type", explorerNode.getType());
            properties.setProperty("name", explorerNode.getName());
            properties.setProperty("path", String.join("/", pathElements));
            final String tagsStr = NullSafe.get(explorerNode.getTags(), explorerService::nodeTagsToString);
            if (!NullSafe.isBlankString(tagsStr)) {
                properties.setProperty("tags", tagsStr);
            }

            final OutputStream outputStream = Files.newOutputStream(parentDir.resolve(fileName));
            PropertiesSerialiser.write(properties, outputStream);
        } catch (final Exception e) {
            messageList.add(new Message(
                    Severity.ERROR,
                    "Error writing properties to file '" + fileName + "': " + LogUtil.exceptionMessage(e)));
        }
    }

    private Path createDirs(final Path dir, final List<String> pathElements) throws IOException {
        Path parentDir = dir;
        for (final String pathElement : pathElements) {
            final String safeName = ImportExportFileNameUtil.toSafeFileName(pathElement, 100);
            final Path child = parentDir.resolve(safeName);

            // If this folder hasn't been created yet then output data for the folder and create it.
            if (!Files.isDirectory(child)) {
                Files.createDirectories(child);
            }

            parentDir = child;
        }

        return parentDir;
    }

    private String createPath(final String parent, final String child) {
        if (parent == null || parent.isEmpty()) {
            return child;
        }
        return parent + "/" + child;
    }

    private String getParentPath(final List<ExplorerNode> parents) {
        if (parents != null && !parents.isEmpty()) {
            String parentPath = parents.stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("/"));
            int index = parentPath.indexOf("System");
            if (index == 0) {
                parentPath = parentPath.substring(index + "System".length());
            }
            index = parentPath.indexOf("/");
            if (index == 0) {
                parentPath = parentPath.substring(1);
            }
            return parentPath;
        }
        return "";
    }

}
